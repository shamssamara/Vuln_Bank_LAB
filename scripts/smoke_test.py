#!/usr/bin/env python3
"""End-to-end API checks using Python stdlib (requires running Docker Compose lab)."""
import json
from urllib.request import ProxyHandler, build_opener
import ssl
import sys
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

ROOT=Path(__file__).resolve().parents[1]
BASE='https://localhost:8443'
CTX=ssl.create_default_context(cafile=str(ROOT/'server/certs/lab-cert.pem'))
API_KEY='VULNBANK_LAB_KEY_123' # public LAB-only key

def call(method,path,payload=None,key=False):
    body=None if payload is None else json.dumps(payload).encode()
    headers={'Content-Type':'application/json'}
    if key:headers['X-API-Key']=API_KEY
    req=Request(BASE+path,data=body,headers=headers,method=method)
    with urlopen(req,context=CTX,timeout=8) as r:
        return r.status,json.load(r)

def main():
    code,health=call('GET','/health')
    assert code==200 and health['labMode']
    print('[PASS] HTTPS health (trusted demo certificate)')
    opener = build_opener(ProxyHandler({}))
    with opener.open('http://127.0.0.1:8080/health', timeout=8) as r:
        assert r.status == 200 and json.load(r)['status'] == 'ok'
    print('[PASS] HTTP health (Burp-no-certificate listener)')
    code,good=call('POST','/api/v1/auth/login',{'username':'alice','password':'password123'})
    assert good['authenticated'] and len(good['matchedUsers'])==1
    print('[PASS] normal demo authentication')
    code,sqli=call('POST','/api/v1/auth/login',{'username':"' OR 1=1 --",'password':'anything'})
    assert sqli['authenticated'] and sqli['bypassDetected'] and len(sqli['matchedUsers'])>=10
    print(f"[PASS] intentionally vulnerable SQLi: {len(sqli['matchedUsers'])} demo users")
    code,customers=call('GET','/api/v1/customers',key=True)
    assert len(customers)>=10
    print('[PASS] hardcoded fake API key accesses customers')
    try:
        call('GET','/api/v1/customers')
        raise AssertionError('Expected 401 without API key')
    except HTTPError as e:
        assert e.code==401
    print('[PASS] API key enforcement present')
    code,account=call('GET','/api/v1/accounts/2',key=True)
    assert account['userId']==2
    print('[PASS] intentionally vulnerable BOLA: account #2 readable with shared key')
    payload='<script>alert("XSS");</script>'
    code,submitted=call('POST','/api/v1/support',{'userId':1,'subject':'XSS training','message':payload})
    assert code==201
    code,tickets=call('GET','/api/v1/support')
    assert any(ticket['message']==payload for ticket in tickets)
    print('[PASS] stored XSS payload persisted; JavaScript execution requires Android WebView')
    print('\nAll API smoke tests passed.')

if __name__=='__main__':
    try:main()
    except Exception as e:
        print('[FAIL]',e,file=sys.stderr)
        sys.exit(1)
