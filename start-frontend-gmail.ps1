$ErrorActionPreference='Stop'

# Gmail API backend uses port 8081 by default, avoiding local services that use 8080.
$env:VITE_API_TARGET='http://127.0.0.1:8081'
Push-Location (Join-Path $PSScriptRoot 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules\\.bin\\vite.cmd')) {
        Write-Output 'Installing frontend dependencies...'
        & npm install
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    & npm run dev
    if ($LASTEXITCODE -ne 0) { throw 'Frontend startup failed.' }
}
finally {
    Pop-Location
}
