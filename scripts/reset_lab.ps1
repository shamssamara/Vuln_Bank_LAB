# WARNING: deletes only VulnBank's Docker Compose database volume and resets all demo data.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
docker compose down -v
docker compose up -d --build
Write-Host 'VulnBank local lab reset. Health URL: https://localhost:8443/health'
