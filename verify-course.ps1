param([string]$CheckEvidence = '', [switch]$KeepDatabase)
$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'scripts/verify_course.py'
$arguments = @($runner)
if ($CheckEvidence) { $arguments += @('--check-evidence', $CheckEvidence) }
if ($KeepDatabase) { $arguments += '--keep-database' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw "Course verification failed (exit $LASTEXITCODE). See the reported evidence directory." }
