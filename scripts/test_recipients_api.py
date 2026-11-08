#!/usr/bin/env python3
"""Read-only contract check for user handles and account IDs in VulnBank lab."""
import json
from urllib.request import Request, urlopen

URL = 'http://127.0.0.1:8080/api/v1/recipients'
request = Request(URL, headers={'X-API-Key':'VULNBANK_LAB_KEY_123'})
with urlopen(request, timeout=10) as response:
    assert response.status == 200, f'Unexpected HTTP {response.status}'
    recipients = json.load(response)
assert isinstance(recipients,list) and len(recipients) >= 2, 'No demo recipients returned'
required={'accountId','userId','username','fullName','accountType'}
assert all(required.issubset(r) for r in recipients), 'Missing identity fields in response'
bob=next((r for r in recipients if r['username']=='bob'),None)
assert bob is not None and bob['fullName']=='Bob Smith', 'Demo username mapping invalid'
print(f'[PASS] {len(recipients)} demo recipients include name + @username + account ID')
print(f"[PASS] Example: {bob['fullName']} · @{bob['username']} · Account #{bob['accountId']}")
print('Read-only API check complete; no balances changed.')
