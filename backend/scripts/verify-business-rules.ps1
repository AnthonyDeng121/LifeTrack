param([string]$BaseUrl = "http://localhost:8081/api/v1")
$ErrorActionPreference = "Stop"
$createdTaskIds = [System.Collections.Generic.List[long]]::new()
$assertionCount = 0

function Assert-Equal($Expected, $Actual, [string]$Label) {
    if ($Expected -ne $Actual) { throw "$Label failed: expected [$Expected], actual [$Actual]" }
    $script:assertionCount++
}
function Invoke-Api([string]$Method, [string]$Path, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = "application/json; charset=utf-8"; TimeoutSec = 60 }
    if ($script:token) { $parameters.Headers = @{ Authorization = "Bearer $script:token" } }
    if ($null -ne $Body) { $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 8 -Compress }
    Invoke-RestMethod @parameters
}
function New-Plan($Start, $End, $Steps = @(@{ content = "Business rules acceptance"; weight = 1 })) {
    $response = Invoke-Api "POST" "/tasks/manual" @{
        title = "Business-rules-$([guid]::NewGuid().ToString('N'))"; category = ([string][char]0x5B66 + [char]0x4E60)
        startDate = $Start; endDate = $End; targetDays = 5; subTasks = $Steps
    }
    if ($response.data.taskId) { $createdTaskIds.Add([long]$response.data.taskId) }
    return $response
}

$login = Invoke-Api "POST" "/auth/login/dev"
Assert-Equal 200 $login.code "login"
$script:token = $login.data.token
$today = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "China Standard Time").Date
$todayText = $today.ToString("yyyy-MM-dd")
$yesterday = $today.AddDays(-1).ToString("yyyy-MM-dd")
$end = $today.AddDays(7).ToString("yyyy-MM-dd")
try {
    $before = Invoke-Api "GET" "/dashboard/stats"
    Assert-Equal 200 $before.code "dashboard baseline"
    $created = New-Plan $today.AddDays(-2).ToString("yyyy-MM-dd") $end
    Assert-Equal 200 $created.code "create active plan"
    $taskId = $created.data.taskId
    foreach ($date in @($yesterday, $todayText)) {
        $record = Invoke-Api "POST" "/check-ins" @{ taskId = $taskId; checkInDate = $date; content = "Actual attendance"; durationMinutes = 30 }
        Assert-Equal 200 $record.code "persist attendance"
    }
    $history = Invoke-Api "GET" "/check-ins?taskId=$taskId"
    $outsideIds = @($history.data | Where-Object { $_.checkInDate -lt $todayText } | ForEach-Object { [long]$_.id })
    $unconfirmed = Invoke-Api "PUT" "/tasks/$taskId" @{ startDate = $todayText }
    Assert-Equal 409 $unconfirmed.code "no implicit deletion"
    $unchanged = Invoke-Api "GET" "/check-ins?taskId=$taskId"
    Assert-Equal 2 @($unchanged.data).Count "unconfirmed history unchanged"
    $wrongConfirmation = Invoke-Api "PUT" "/tasks/$taskId" @{
        startDate = $todayText; discardOutsideCheckIns = $true; confirmedDiscardCheckInIds = @(-1)
    }
    Assert-Equal 409 $wrongConfirmation.code "stale or fabricated confirmation rejected"
    $inconsistent = Invoke-Api "PUT" "/tasks/$taskId" @{ startDate = $todayText; status = "NOT_STARTED" }
    Assert-Equal 400 $inconsistent.code "inconsistent status rejected before pruning"
    $updated = Invoke-Api "PUT" "/tasks/$taskId" @{
        startDate = $todayText; discardOutsideCheckIns = $true; confirmedDiscardCheckInIds = $outsideIds
    }
    Assert-Equal 200 $updated.code "confirmed pruning"
    $history = Invoke-Api "GET" "/check-ins?taskId=$taskId"
    Assert-Equal 1 @($history.data).Count "only out-of-period attendance deleted"
    Assert-Equal $todayText $history.data[0].checkInDate "inside attendance retained"
    $stats = Invoke-Api "GET" "/check-ins/tasks/$taskId/stats"
    Assert-Equal 1 $stats.data.checkedInDays "actual remaining days"
    Assert-Equal 1 $stats.data.currentStreak "streak recalculated"
    Assert-Equal 20 $stats.data.completionRate "rate recalculated"
    $after = Invoke-Api "GET" "/dashboard/stats"
    Assert-Equal ([int]$before.data.checkedInDays + 1) ([int]$after.data.checkedInDays) "dashboard reflects deleted attendance"

    $cancelled = Invoke-Api "PUT" "/tasks/$taskId" @{ status = "CANCELLED" }
    Assert-Equal 200 $cancelled.code "cancel plan"
    $steps = Invoke-Api "GET" "/tasks/$taskId/subtasks"
    $stepId = $steps.data[0].id
    $completedStep = Invoke-Api "PUT" "/tasks/subtasks/$stepId/complete"
    Assert-Equal 400 $completedStep.code "cancelled plan cannot complete a step"
    $detail = Invoke-Api "GET" "/tasks/$taskId"
    Assert-Equal "CANCELLED" $detail.data.status "cancelled remains terminal"

    foreach ($badWeights in @(@(2, 0), @(-1, 2), @(0.2, 0.2))) {
        $invalid = New-Plan $todayText $end @(@{ content = "A"; weight = $badWeights[0] }, @{ content = "B"; weight = $badWeights[1] })
        Assert-Equal 400 $invalid.code "invalid creation weights rejected"
    }
    $valid = New-Plan $todayText $end @(@{ content = "A"; weight = 0.6 }, @{ content = "B" }, @{ content = "C" })
    Assert-Equal 200 $valid.code "remaining weights distributed"
    Assert-Equal 0.2 $valid.data.subTasks[1].weight "missing weight share"
    $taskId = $valid.data.taskId
    $steps = Invoke-Api "GET" "/tasks/$taskId/subtasks"
    $stepId = $steps.data[0].id
    $pending = Invoke-Api "POST" "/actions/deferred" @{ taskId = $taskId; content = "Persisted before completion"; durationMinutes = 20 }
    Assert-Equal 200 $pending.code "pending learning before completion"
    $completed = Invoke-Api "PUT" "/tasks/$taskId" @{ status = "COMPLETED" }
    Assert-Equal 200 $completed.code "complete active plan"
    $requests = @(
        @{ method = "POST"; path = "/tasks/$taskId/subtasks"; body = @{ content = "New step"; weight = 0.2 } },
        @{ method = "PUT"; path = "/tasks/subtasks/$stepId"; body = @{ content = "Changed step" } },
        @{ method = "DELETE"; path = "/tasks/subtasks/$stepId"; body = $null },
        @{ method = "PUT"; path = "/tasks/subtasks/$stepId/complete"; body = $null },
        @{ method = "PUT"; path = "/tasks/$taskId"; body = @{ status = "IN_PROGRESS" } },
        @{ method = "PUT"; path = "/tasks/$taskId"; body = @{ endDate = $today.AddDays(8).ToString("yyyy-MM-dd") } },
        @{ method = "PUT"; path = "/tasks/$taskId"; body = @{ targetDays = 4 } },
        @{ method = "POST"; path = "/actions/deferred/$($pending.data.id)/retry"; body = $null }
    )
    foreach ($request in $requests) {
        $result = Invoke-Api $request.method $request.path $request.body
        Assert-Equal 400 $result.code "completed plan mutation blocked: $($request.path)"
    }
    $renamed = Invoke-Api "PUT" "/tasks/$taskId" @{ title = "Completed snapshot title corrected" }
    Assert-Equal 200 $renamed.code "completed metadata remains editable"
    Assert-Equal "COMPLETED" $renamed.data.status "rename cannot reopen plan"

    $created = New-Plan $todayText $end
    $taskId = $created.data.taskId
    $tomorrow = $today.AddDays(1).ToString("yyyy-MM-dd")
    $contradiction = Invoke-Api "PUT" "/tasks/$taskId" @{ startDate = $tomorrow; status = "IN_PROGRESS" }
    Assert-Equal 400 $contradiction.code "future period cannot be in progress"
    $future = Invoke-Api "PUT" "/tasks/$taskId" @{ startDate = $tomorrow }
    Assert-Equal "NOT_STARTED" $future.data.status "future period derives not started"
    $readAgain = Invoke-Api "GET" "/tasks/$taskId"
    Assert-Equal $future.data.status $readAgain.data.status "save and read status stable"

    [pscustomobject]@{ assertionsPassed = $assertionCount; periodPruning = "confirmed and persistent"; terminalStates = "protected"; weights = "validated"; dateStatus = "consistent" } | ConvertTo-Json
}
finally {
    foreach ($id in $createdTaskIds) {
        $cleanup = Invoke-Api "DELETE" "/tasks/$id"
        if ($cleanup.code -ne 200) { throw "Failed to clean temporary plan $id" }
    }
}
