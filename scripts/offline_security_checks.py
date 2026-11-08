#!/usr/bin/env python3
"""Independent miniature proof that the lab's unsafe SQL pattern is injectable.
This uses an in-memory SQLite fixture ONLY; it is not a live Ktor/PostgreSQL test.
"""
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
conn=sqlite3.connect(':memory:')
conn.execute('CREATE TABLE users (id INTEGER, username TEXT, password TEXT)')
conn.executemany('INSERT INTO users VALUES (?,?,?)',[(1,'alice','password123'),(2,'bob','demo'),(3,'charlie','demo')])

def insecure_login(username,password):
    # Mirrors the vulnerable single-line SQL interpolation in server/Main.kt.
    sql=f"SELECT id,username FROM users WHERE username = '{username}' AND password = '{password}'"
    return conn.execute(sql).fetchall()

assert len(insecure_login('alice','password123'))==1
assert len(insecure_login('alice','incorrect'))==0
matches=insecure_login("' OR 1=1 --",'anything')
assert len(matches)==3
assert 'createStatement()' in (ROOT/'server/src/main/kotlin/com/vulnbank/server/Main.kt').read_text()
assert '${request.username}' in (ROOT/'server/src/main/kotlin/com/vulnbank/server/Main.kt').read_text()
print('[PASS] SQLi demonstration using separate throwaway SQLite fixture: 3 dummy records bypassed')
html=(ROOT/'app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt').read_text()
assert 'settings.javaScriptEnabled=true' in html and '${ticket.message}' in html
print('[PASS] Android UI contains deliberately unsafe HTML-in-WebView lab sink')
print('Offline checks passed. These do not substitute for app/backend runtime testing.')
