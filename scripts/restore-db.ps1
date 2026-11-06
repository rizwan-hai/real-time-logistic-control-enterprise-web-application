param([Parameter(Mandatory=$true)][string]$InputFile)
$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $InputFile -PathType Leaf)) { throw "Backup file not found: $InputFile" }
Write-Warning "This replaces data in the configured database. Confirm you selected the intended local environment."
Get-Content -Raw -LiteralPath $InputFile | docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
if ($LASTEXITCODE -ne 0) { throw "Database restore failed." }
Write-Output "Restore completed from $InputFile"
