# DESTRUCTIVE: clears both VulnBank lab demo volumes. Do not run during normal use.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
Write-Warning 'This action deletes BOTH local synthetic databases and all test transfers.'
$reply = Read-Host 'Type RESET to confirm'
if ($reply -cne 'RESET') { Write-Host 'Cancelled'; exit 0 }
docker compose down -v
if ($LASTEXITCODE -ne 0) { throw 'Docker reset failed' }
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Docker restart failed' }
Write-Host 'Lab demo data reset. Vulnerable: 8080; Secure: 8082.'
