$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
Write-Host 'Starting isolated local VulnBank containers...' -ForegroundColor Cyan
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed; check logs.' }
docker compose ps
Write-Host ''
Write-Host 'Verify updated recipient and transfer API without changing balances:' -ForegroundColor Green
python .\scripts\verify_update.py
Write-Host ''
Write-Host 'Android Emulator -> Burp: 10.0.2.2:8081; Burp -> backend: 127.0.0.1:8080'
