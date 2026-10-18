$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '.lifetrack-run\processes.json'

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host 'No local process record was found. LifeTrack may not be running.'
    exit 0
}

$processes = Get-Content -LiteralPath $pidFile -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $processes) {
    if (Get-Process -Id $item.pid -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $item.pid /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $item.pid -ErrorAction SilentlyContinue)) {
            throw "Failed to stop $($item.name) (PID $($item.pid))."
        }
        Write-Host "Stopped $($item.name)."
    }
}
Remove-Item -LiteralPath $pidFile -Force
Write-Host 'LifeTrack stopped.' -ForegroundColor Green
