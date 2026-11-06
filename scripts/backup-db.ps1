param(
    [string]$OutputDirectory = "backups"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path ".env")) { throw "Copy .env.example to .env and configure it first." }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$file = Join-Path $OutputDirectory "logistics-$stamp.sql"
docker compose exec -T mysql sh -c 'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' | Set-Content -Encoding utf8 $file
if ($LASTEXITCODE -ne 0) { Remove-Item -LiteralPath $file -ErrorAction SilentlyContinue; throw "Database backup failed." }
Write-Output "Backup written to $file"
