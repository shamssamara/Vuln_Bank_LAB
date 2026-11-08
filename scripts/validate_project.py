#!/usr/bin/env python3
"""Static quality checks. Does NOT replace a Gradle/Docker/Android build."""
from pathlib import Path
import json
import ssl
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
required=[
 'settings.gradle.kts','build.gradle.kts','gradlew','gradlew.bat','compose.yaml',
 'gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties',
 'app/build.gradle.kts','app/src/main/AndroidManifest.xml',
 'app/src/main/java/com/vulnbank/lab/MainActivity.kt',
 'app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt',
 'app/src/main/java/com/vulnbank/lab/network/BankApi.kt',
 'app/src/main/java/com/vulnbank/lab/security/LabSecrets.kt',
 'app/src/main/java/com/vulnbank/lab/security/RootGate.kt',
 'app/src/main/java/com/vulnbank/lab/security/AuthGate.kt',
 'app/src/main/res/raw/vulnbank_lab_ca.crt',
 'app/src/main/res/xml/network_security_config.xml',
 'server/Dockerfile','server/build.gradle.kts',
 'server/src/main/kotlin/com/vulnbank/server/Main.kt',
 'server/certs/server.p12','server/certs/lab-cert.pem',
 'db/init.sql','scripts/smoke_test.py','README.md','docs/START_HERE_AR.md',
 *['frida_scripts/'+name+'.js' for name in ('ssl_pinning_bypass','root_detection_bypass','auth_bypass','runtime_key_logger','all_lab_hooks')]
]
for f in required:
    assert (ROOT/f).is_file(),f'Missing: {f}'
print('[PASS] All required source/config/cert files exist:',len(required))
for xml in [ROOT/'app/src/main/AndroidManifest.xml',ROOT/'app/src/main/res/xml/network_security_config.xml',ROOT/'app/src/main/res/values/strings.xml']:
    ET.parse(xml)
print('[PASS] Android resource XML parse')
import yaml
compose=yaml.safe_load((ROOT/'compose.yaml').read_text())
assert compose['services']['server']['ports']==['127.0.0.1:8080:8080', '127.0.0.1:8443:8443']
assert compose['services']['db']['ports']==['127.0.0.1:5433:5432']
print('[PASS] Docker Compose YAML parses; HTTP and HTTPS ports bind localhost only')
application=(ROOT/'app/build.gradle.kts').read_text()
cert=ROOT/'server/certs/lab-cert.pem'
pin=subprocess.check_output('openssl x509 -pubkey -noout -in "'+str(cert)+'" | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | openssl base64 -A',shell=True,text=True).strip()
assert 'http://127.0.0.1:8080' in application, 'Expected local HTTP backend URL'
assert 'TLS_PIN' not in application, 'TLS pinning must be disabled in HTTP interception lab'
assert ssl.PEM_cert_to_DER_cert(cert.read_text())==(ROOT/'app/src/main/res/raw/vulnbank_lab_ca.crt').read_bytes()
print('[PASS] Legacy HTTPS certificate still matches embedded trust anchor (not used by Android HTTP lab)')
certinfo=subprocess.check_output(['openssl','x509','-in',str(cert),'-noout','-ext','subjectAltName'],text=True)
assert all(v in certinfo for v in ['localhost','127.0.0.1','10.0.2.2'])
print('[PASS] Legacy HTTPS certificate SANs cover host + emulator URLs')
key_android=(ROOT/'app/src/main/java/com/vulnbank/lab/security/LabSecrets.kt').read_text()
key_sql=(ROOT/'db/init.sql').read_text()
key_server=(ROOT/'server/src/main/kotlin/com/vulnbank/server/Main.kt').read_text()
assert 'VULNBANK_LAB_KEY_123' in key_android and 'VULNBANK_LAB_KEY_123' in key_sql and 'VULNBANK_LAB_KEY_123' in key_server
print('[PASS] Synthetic API key is consistent across app, DB, server')
source=(ROOT/'app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt').read_text()
assert 'settings.javaScriptEnabled=true' in source and '${ticket.message}' in source
assert "PremiumLoginScreen" in source and "' OR 1=1 --" in (ROOT/'docs/LAB_EXERCISES.md').read_text()
assert (ROOT/'app/src/main/java/com/vulnbank/lab/ui/PremiumScreens.kt').is_file()
assert 'post("/transfers")' in key_server and 'fromAccountId' in key_server
assert 'suspend fun transfer(' in (ROOT/'app/src/main/java/com/vulnbank/lab/network/BankApi.kt').read_text()
assert 'TicketCreated(true, id)' in key_server
print('[PASS] Required vulnerable lab paths are included and documented')
with zipfile.ZipFile(ROOT/'gradle/wrapper/gradle-wrapper.jar') as jar:
    assert 'org/gradle/wrapper/GradleWrapperMain.class' in jar.namelist()
print('[PASS] Bootstrap launcher contains documented Gradle entry point')
for js in (ROOT/'frida_scripts').glob('*.js'):
    subprocess.run(['node','--check',str(js)],check=True,stdout=subprocess.DEVNULL)
print('[PASS] Optional legacy Frida JavaScript syntax validated')
print('\nSTATIC VALIDATION COMPLETE — build/deploy still require Android SDK, Gradle downloads and Docker.')
