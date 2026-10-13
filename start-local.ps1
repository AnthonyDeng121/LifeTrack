param([switch]$WithAI)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
$runtimeDir = Join-Path $projectRoot '.lifetrack-run'
$pidFile = Join-Path $runtimeDir 'processes.json'

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Path. Copy .env.example to .env and fill in local settings."
    }
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        $pair = $line.Split('=', 2)
        if ($pair.Count -ne 2 -or [string]::IsNullOrWhiteSpace($pair[0])) {
            throw "Invalid line in .env: $rawLine"
        }
        $name = $pair[0].Trim()
        $value = $pair[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "Env:$name" -Value $value
    }
}

function Assert-CommandExists {
    param([string]$Name, [string]$InstallHint)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command not found: $Name. $InstallHint"
    }
}

function Assert-PortAvailable {
    param([int]$Port, [string]$ServiceName)
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        throw "$ServiceName port $Port is already in use. If LifeTrack is running, execute .\stop-local.ps1 first."
    }
}

function Wait-LocalPort {
    param([int]$Port, [int]$TimeoutSeconds, [string]$ServiceName, [string]$ErrorLog)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $result = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
            if ($result.AsyncWaitHandle.WaitOne(300) -and $client.Connected) { return }
        } finally {
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 700
    }
    $tail = if (Test-Path -LiteralPath $ErrorLog) {
        (Get-Content -LiteralPath $ErrorLog -Tail 20 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
    } else { 'No error log was generated.' }
    throw "$ServiceName did not start in time. Error log tail:`n$tail"
}

function Start-LocalProcess {
    param([string]$Name, [string]$WorkingDirectory, [string]$Command)
    $stdout = Join-Path $runtimeDir "$Name.out.log"
    $stderr = Join-Path $runtimeDir "$Name.err.log"
    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $Command) `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
    return [pscustomobject]@{ name = $Name; pid = $process.Id; stdout = $stdout; stderr = $stderr }
}

Import-DotEnv -Path $envFile
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { throw 'Set DB_USERNAME in .env.' }
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD) -or $env:DB_PASSWORD -eq 'CHANGE_ME') {
    throw 'Open .env and replace DB_PASSWORD=CHANGE_ME with your MySQL password.'
}
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET) -or $env:JWT_SECRET.Length -lt 32) {
    throw 'JWT_SECRET in .env must contain at least 32 characters.'
}
if ($WithAI -and [string]::IsNullOrWhiteSpace($env:ZHIPU_API_KEY)) {
    throw 'Set ZHIPU_API_KEY in .env when using -WithAI. Run .\start-local.ps1 without AI otherwise.'
}

Assert-CommandExists 'java' 'Install JDK 17 and add it to PATH.'
Assert-CommandExists 'node' 'Install Node.js 20 or later.'
Assert-CommandExists 'npm.cmd' 'Install npm.'
if ($WithAI) { Assert-CommandExists 'python' 'Install Python 3.8 or later.' }
Assert-PortAvailable 8081 'Backend'
Assert-PortAvailable 5173 'Frontend'
if ($WithAI) { Assert-PortAvailable 5000 'AI service' }

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$started = @()
try {
    Write-Host 'Starting backend...'
    $backend = Start-LocalProcess 'backend' (Join-Path $projectRoot 'backend') "& '.\mvnw.cmd' spring-boot:run"
    $started += $backend
    Wait-LocalPort 8081 120 'Backend' $backend.stderr

    Write-Host 'Starting frontend...'
    $frontendCommand = "if (-not (Test-Path -LiteralPath '.\node_modules')) { & npm.cmd install }; & npm.cmd run dev:h5"
    $frontend = Start-LocalProcess 'frontend' (Join-Path $projectRoot 'frontend') $frontendCommand
    $started += $frontend
    Wait-LocalPort 5173 120 'Frontend' $frontend.stderr

    if ($WithAI) {
        Write-Host 'Starting optional AI service...'
        $aiCommand = "if (-not (Test-Path -LiteralPath '.\venv\Scripts\python.exe')) { & python -m venv venv }; & '.\venv\Scripts\python.exe' -m pip install -q flask requests; & '.\venv\Scripts\python.exe' ai_server.py"
        $ai = Start-LocalProcess 'ai' (Join-Path $projectRoot 'AI_Server') $aiCommand
        $started += $ai
        Wait-LocalPort 5000 120 'AI service' $ai.stderr
    }

    $started | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
    Write-Host ''
    Write-Host 'LifeTrack started successfully.' -ForegroundColor Green
    Write-Host '  Frontend: http://localhost:5173/#/'
    Write-Host '  Backend:  http://localhost:8081'
    Write-Host '  Swagger:  http://localhost:8081/swagger-ui.html'
    if ($WithAI) { Write-Host '  AI:       http://127.0.0.1:5000' }
    Write-Host 'Stop all services with: .\stop-local.ps1'
    Write-Host "Logs: $runtimeDir"
} catch {
    foreach ($item in $started) { Stop-Process -Id $item.pid -Force -ErrorAction SilentlyContinue }
    throw
}
