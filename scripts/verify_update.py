#!/usr/bin/env python3
"""Read-only contract verification for the current VulnBank Kotlin backend.

Calls local HTTP only (127.0.0.1:8080), bypassing operating-system proxy
settings. The invalid transfer is deliberately rejected before balance changes.
"""
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

BASE = 'http://127.0.0.1:8080'
KEY = 'VULNBANK_LAB_KEY_123'
opener = build_opener(ProxyHandler({}))

def call(path, *, body=None, with_key=False):
    headers={'Accept':'application/json', 'Content-Type':'application/json'}
    if with_key:
        headers['X-API-Key']=KEY
    req=Request(BASE+path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    try:
        with opener.open(req, timeout=12) as r:
            raw=r.read().decode()
            if not raw.lstrip().startswith(('{', '[')):
                raise AssertionError(f'{path}: expected JSON, got {raw[:90]!r} (check Burp listening ports)')
            return r.status,json.loads(raw)
    except HTTPError as e:
        raw=e.read().decode(errors='replace')
        try: data=json.loads(raw)
        except ValueError: data={'raw':raw[:90]}
        return e.code,data

try:
    status,health=call('/health')
    assert status == 200 and health.get('status')=='ok', f'Health failed: {status} {health}'
    assert health.get('backendVersion')=='2026.11-recipient-transfer-fix', (
        'Old server image is running! Use: docker compose up -d --build --force-recreate server'
    )
    print('[PASS] Fresh Kotlin backend is running (HTTP :8080).')
    status,people=call('/api/v1/recipients',with_key=True)
    assert status == 200, f'Recipient endpoint returned HTTP {status}: {people}. Rebuild the server image.'
    assert isinstance(people,list) and len(people)>=2, f'Directory empty or invalid: {people}'
    required={'accountId','userId','username','fullName','accountType'}
    assert all(required <= set(p) for p in people), 'Recipient fields missing.'
    bob=next((p for p in people if p['username']=='bob'),None)
    assert bob and bob['fullName']=='Bob Smith', 'Synthetic Bob entry missing.'
    print(f"[PASS] Recipient directory: {len(people)} people; Bob Smith · @bob · Account #{bob['accountId']}")
    status,rejected=call('/api/v1/transfers',body={
        'fromAccountId':1,'toAccountId':2,'amount':0.0,'note':'read-only API route test'
    },with_key=True)
    assert status==400, f'Transfer route missing or misconfigured, got HTTP {status}: {rejected}'
    print('[PASS] Transfers endpoint exists; invalid zero amount rejected (no balance change).')
    status,account=call('/api/v1/accounts/1',with_key=True)
    assert status==200 and account.get('userId')==1, 'Account API unavailable.'
    print('[PASS] Existing account API responds.')
    print('\nALL LOCAL API CONTRACT CHECKS PASSED.\nNext: open Android Studio and Run app, then inspect Burp HTTP history on :8081.')
except (AssertionError, URLError, TimeoutError, ValueError) as exc:
    print('\n[FAIL]',exc,file=sys.stderr)
    print('Check: docker compose ps; docker compose logs --tail=80 server',file=sys.stderr)
    sys.exit(1)
