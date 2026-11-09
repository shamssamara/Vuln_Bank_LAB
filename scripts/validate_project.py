#!/usr/bin/env python3
"""Offline structural validation of the entire dual-mode lab (not an Android build)."""
from pathlib import Path
import xml.etree.ElementTree as ET
import sys
ROOT=Path(__file__).resolve().parent.parent
needed=['app/build.gradle.kts','app/src/main/AndroidManifest.xml',
 'app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt',
 'app/src/main/java/com/vulnbank/lab/ui/ModeSelectionScreen.kt',
 'app/src/main/java/com/vulnbank/lab/network/BankApi.kt',
 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
 'server/src/main/kotlin/com/vulnbank/server/Main.kt',
 'server/src/main/kotlin/com/vulnbank/server/SecureRoutes.kt',
 'db/init.sql','db/init_secure.sql','compose.yaml','scripts/verify_dual_mode.py',
 'gradle/wrapper/gradle-wrapper.jar','README.md']
for f in needed:
    assert (ROOT/f).is_file(),f'Missing project file: {f}'
for f in ROOT.glob('app/src/main/**/*.xml'): ET.parse(f)
compose=(ROOT/'compose.yaml').read_text()
assert all(s in compose for s in ['db_secure:','server_secure:','dbdata_secure:','127.0.0.1:8080:8080','127.0.0.1:8082:8080'])
secure=(ROOT/'server/src/main/kotlin/com/vulnbank/server/SecureRoutes.kt').read_text()
assert all(s in secure for s in ['Idempotency-Key','secure_transfer_requests','ownedAccount','ON CONFLICT DO NOTHING'])
assert not (ROOT/'frida_scripts').exists(), 'Legacy Frida scripts remain'
print('[PASS] Full project structure, XML, modes, idempotency, no Frida scripts')
