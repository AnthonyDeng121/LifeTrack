param([string]$BaseUrl = "http://localhost:8081/api/v1", [string]$Token = "")
$ErrorActionPreference = "Stop"

function Assert-Equal($Expected, $Actual, [string]$Label) {
    if ($Expected -ne $Actual) { throw "$Label failed: expected [$Expected], actual [$Actual]" }
}
Import-Module (Join-Path $PSScriptRoot "AcceptanceHttp.psm1") -Force
function Invoke-LifeTrack([string]$Method, [string]$Path, $Body = $null, [string]$Token = "") {
    if ($Method -eq "PUT" -and $Path -match '^/tasks/\d+$' -and $null -ne $Body -and -not $Body.ContainsKey('version')) {
        $current = Invoke-LifeTrackApi -BaseUrl $BaseUrl -Method "GET" -Path $Path -Token $Token
        $Body.version = $current.data.version
    }
    Invoke-LifeTrackApi -BaseUrl $BaseUrl -Method $Method -Path $Path -Body $Body -Token $Token
}

if (-not $Token) {
    $login = Invoke-LifeTrack "POST" "/auth/login/dev"
    Assert-Equal 200 $login.code "dev login"
    $token = $login.data.token
}
$today = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "China Standard Time").Date
$startDate = $today.AddDays(-2).ToString("yyyy-MM-dd")
$endDate = $today.AddDays(7).ToString("yyyy-MM-dd")
$todayText = $today.ToString("yyyy-MM-dd")
$taskId = $null
try {
    $dashboardBefore = Invoke-LifeTrack "GET" "/dashboard/stats" $null $token
    $beforeCheckedInDays = [int]$dashboardBefore.data.checkedInDays
    $title = "P1-acceptance-$($today.ToString('yyyyMMdd'))-$([DateTimeOffset]::Now.ToUnixTimeSeconds())"
    $created = Invoke-LifeTrack "POST" "/tasks/manual" @{
        title = $title; category = [char]0x5B66 + [char]0x4E60; startDate = $startDate; endDate = $endDate; targetDays = 5
        subTasks = @(@{ content = "P1 acceptance"; weight = 1 })
    } $token
    Assert-Equal 200 $created.code "create plan"
    $taskId = [long]$created.data.taskId
    $detail = Invoke-LifeTrack "GET" "/tasks/$taskId" $null $token
    Assert-Equal $title $detail.data.title "plan detail"
    $editedTitle = "$title-edited"
    $edited = Invoke-LifeTrack "PUT" "/tasks/$taskId" @{ title = $editedTitle; targetDays = 5 } $token
    Assert-Equal $editedTitle $edited.data.title "edit plan"

    foreach ($offset in -2..0) {
        $date = $today.AddDays($offset).ToString("yyyy-MM-dd")
        $checkIn = Invoke-LifeTrack "POST" "/check-ins" @{
            taskId = $taskId; checkInDate = $date; content = "acceptance $date"; durationMinutes = 30
        } $token
        Assert-Equal 200 $checkIn.code "create check-in $date"
    }
    $duplicate = Invoke-LifeTrack "POST" "/check-ins" @{ taskId = $taskId; checkInDate = $todayText; content = "duplicate"; durationMinutes = 10 } $token
    Assert-Equal 409 $duplicate.code "duplicate rejection"
    $outside = Invoke-LifeTrack "POST" "/check-ins" @{ taskId = $taskId; checkInDate = $today.AddDays(-3).ToString("yyyy-MM-dd"); content = "outside"; durationMinutes = 10 } $token
    Assert-Equal 400 $outside.code "outside-date rejection"
    $future = Invoke-LifeTrack "POST" "/check-ins" @{ taskId = $taskId; checkInDate = $today.AddDays(1).ToString("yyyy-MM-dd"); content = "future"; durationMinutes = 10 } $token
    Assert-Equal 400 $future.code "future-date rejection"

    $historyPath = "/check-ins?taskId=$taskId`&startDate=$startDate`&endDate=$todayText"
    $history = Invoke-LifeTrack "GET" $historyPath $null $token
    Assert-Equal 3 @($history.data).Count "persisted history count"
    $stats = Invoke-LifeTrack "GET" "/check-ins/tasks/$taskId/stats" $null $token
    Assert-Equal 3 $stats.data.checkedInDays "checked-in days"
    Assert-Equal 3 $stats.data.currentStreak "current streak"
    Assert-Equal 3 $stats.data.longestStreak "longest streak"
    Assert-Equal 60 $stats.data.completionRate "completion rate"
    $dashboardAfter = Invoke-LifeTrack "GET" "/dashboard/stats" $null $token
    $eligibleIds = @((Invoke-LifeTrack "GET" "/tasks" $null $token).data | Where-Object { $_.status -ne "CANCELLED" } | ForEach-Object { $_.id })
    $expectedDays = @((Invoke-LifeTrack "GET" "/check-ins" $null $token).data | Where-Object { $_.taskId -in $eligibleIds } | Select-Object -ExpandProperty checkInDate -Unique).Count
    Assert-Equal $expectedDays ([int]$dashboardAfter.data.checkedInDays) "dashboard natural days"
    $planSteps = Invoke-LifeTrack "GET" "/tasks/$taskId/subtasks" $null $token
    foreach ($step in $planSteps.data) { $null = Invoke-LifeTrack "PUT" "/tasks/subtasks/$($step.id)/complete" $null $token }
    $completed = Invoke-LifeTrack "GET" "/tasks/$taskId" $null $token
    Assert-Equal "COMPLETED" $completed.data.status "status transition after all steps"
    $blockedByStatus = Invoke-LifeTrack "POST" "/check-ins" @{ taskId = $taskId; checkInDate = $todayText; content = "completed"; durationMinutes = 10 } $token
    Assert-Equal 400 $blockedByStatus.code "status rejection"

    [pscustomobject]@{
        taskId = $taskId; historyApiCheckIns = @($history.data).Count; checkedInDays = $stats.data.checkedInDays
        currentStreak = $stats.data.currentStreak; longestStreak = $stats.data.longestStreak; completionRate = $stats.data.completionRate
        dashboardDelta = ([int]$dashboardAfter.data.checkedInDays - $beforeCheckedInDays); duplicateCode = $duplicate.code
        outsideDateCode = $outside.code; futureDateCode = $future.code; statusBlockedCode = $blockedByStatus.code
    } | ConvertTo-Json
}
finally {
    if ($null -ne $taskId) {
        $deleted = Invoke-LifeTrack "DELETE" "/tasks/$taskId" $null $token
        Assert-Equal 200 $deleted.code "cleanup acceptance plan"
    }
}
