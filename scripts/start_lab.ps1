$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
Write-Host 'Starting both local VulnBank backends...' -ForegroundColor Cyan
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed. Run docker compose logs --tail=100' }
docker compose ps
Write-Host 'Validating Vulnerable and Secure APIs...' -ForegroundColor Green
python .\scripts\verify_dual_mode.py
if ($LASTEXITCODE -ne 0) { throw 'Dual-mode API verification failed.' }
Write-Host 'Vulnerable: Burp 127.0.0.1:8081 -> backend 127.0.0.1:8080'
Write-Host 'Secure: emulator -> backend 10.0.2.2:8082 (no Burp)'
