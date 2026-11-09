#!/usr/bin/env python3
"""VulnBank dual-mode read-only checks, and opt-in synthetic replay test.

Uses Windows host loopback; never sends real financial data.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal

HOSTS = {'vulnerable': 'http://127.0.0.1:8080', 'secure': 'http://127.0.0.1:8082'}
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def api(mode, route, *, body=None, token=None, idem=None, key=False):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = f'Bearer {token}'
    if idem:
        headers['Idempotency-Key'] = idem
    if key:
        headers['X-API-Key'] = 'VULNBANK_LAB_KEY_123'
    payload = json.dumps(body).encode('utf-8') if body is not None else None
    req = urllib.request.Request(HOSTS[mode]+route, data=payload, headers=headers)
    try:
        with HTTP.open(req,timeout=12) as response:
            raw=response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw=error.read()
        try: result=json.loads(raw)
        except ValueError: result={'raw':raw.decode('utf-8','replace')[:120]}
        return error.code,result

def check(ok, msg):
    print(('[PASS]' if ok else '[FAIL]'),msg)
    if not ok: raise AssertionError(msg)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--run-replay',action='store_true',help='Execute small synthetic transfers, changing both demo databases')
    parser.add_argument('--run-concurrent',action='store_true',help='Run two concurrent secure retries; changes secure fictional balances')
    args=parser.parse_args()
    tokens={}
    for mode in HOSTS:
        code,info=api(mode,'/health')
        check(code==200 and info.get('securityMode')==mode, f'{mode} backend identity and port')
        code,data=api(mode,'/api/v1/auth/login',body={'username':'alice','password':'password123'})
        check(code==200 and data.get('authenticated'), f'{mode} demo login')
        tokens[mode]=data['sessionToken']
    code,_=api('secure','/api/v1/auth/login',body={'username':"' OR 1=1 --",'password':'whatever'})
    check(code==401,'secure login rejects classic SQL injection bypass')
    code,_=api('secure','/api/v1/accounts/1')
    check(code==401,'secure account lookup requires authentication')
    code,_=api('secure','/api/v1/accounts/2',token=tokens['secure'])
    check(code==403,'secure mode denies Alice access to Bob account')
    code,_=api('secure','/api/v1/customers',token=tokens['secure'])
    check(code==200,'secure mode authenticates customer API without demo key')
    code,_=api('secure','/api/v1/support',token=tokens['secure'])
    check(code==200,'secure support access is authenticated')
    if not (args.run_replay or args.run_concurrent):
        print('Replay NOT executed; run with --run-replay or --run-concurrent to spend fictional test balances.')
        return
    for mode in (HOSTS if args.run_replay else []):
        token=tokens[mode]
        kwargs={'token':token} if mode=='secure' else {'key':True}
        def balance():
            st,obj=api(mode,'/api/v1/accounts/1',**kwargs)
            check(st==200,f'{mode} account read')
            return Decimal(str(obj['balance']))
        before=balance()
        payload={'fromAccountId':1,'toAccountId':2,'amount':5.0,'note':f'replay-check-{uuid.uuid4()}'}
        idem=str(uuid.uuid4())
        first_code,first=api(mode,'/api/v1/transfers',body=payload,idem=idem,**kwargs)
        second_code,second=api(mode,'/api/v1/transfers',body=payload,idem=idem,**kwargs)
        after=balance()
        expected=Decimal('5.00') if mode=='secure' else Decimal('10.00')
        check(first_code==201 and second_code in (200,201),f'{mode} replay responses')
        check(before-after==expected,f'{mode} debit exactly {expected} fictional JOD')
        check((first['transferId']==second['transferId']) == (mode=='secure'),f'{mode} idempotency result')
        print(f'{mode}: transfer IDs {first["transferId"]}, {second["transferId"]}; debit {before-after}')
        if mode=='secure':
            code,conflict=api(mode,'/api/v1/transfers',body={**payload,'amount':6.0},idem=idem,**kwargs)
            check(code==409,'secure mode rejects a changed payload with the same idempotency key')
            check(balance()==after,'secure conflict did not change balance')
    if args.run_concurrent:
        mode='secure'
        kwargs={'token':tokens[mode]}
        def balance_secure():
            status,value=api(mode,'/api/v1/accounts/1',**kwargs)
            check(status==200,'concurrency account query')
            return Decimal(str(value['balance']))
        initial=balance_secure()
        idem=str(uuid.uuid4())
        payload={'fromAccountId':1,'toAccountId':2,'amount':5.0,'note':f'concurrent-replay-{uuid.uuid4()}'}
        def attempt(_): return api(mode,'/api/v1/transfers',body=payload,idem=idem,**kwargs)
        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes=list(pool.map(attempt,range(2)))
        ids=[value['transferId'] for status,value in outcomes if status in (200,201)]
        check(len(ids)==2 and ids[0]==ids[1],'secure concurrent retry returns the same transfer ID')
        check(initial-balance_secure()==Decimal('5.00'),'secure concurrent replay debits only once')
    print('All dual-mode checks passed.')

if __name__=='__main__':
    try: main()
    except (AssertionError,Exception) as exc:
        print('[ERROR]',type(exc).__name__,str(exc))
        sys.exit(1)
