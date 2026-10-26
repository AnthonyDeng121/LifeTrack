Set-StrictMode -Version Latest

function Invoke-LifeTrackApi {
    param([string]$BaseUrl, [string]$Method, [string]$Path, $Body = $null, [string]$Token = '', [int]$TimeoutSec = 60)
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = 'application/json; charset=utf-8'; TimeoutSec = $TimeoutSec; UseBasicParsing = $true }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress }
    try {
        $response = Invoke-WebRequest @parameters
        $status = [int]$response.StatusCode
        $response.RawContentStream.Position = 0
        $reader = [IO.StreamReader]::new($response.RawContentStream, [Text.Encoding]::UTF8)
        try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $failure = $_
        $content = ''
        try {
            $reader = [IO.StreamReader]::new($failure.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        } catch { $content = '' }
        if ([string]::IsNullOrWhiteSpace($content)) { $content = $failure.ErrorDetails.Message }
    }
    $result = $content | ConvertFrom-Json
    if ($null -eq $result.code -or [int]$result.code -ne $status) {
        throw "HTTP/body status mismatch: $Method $Path (HTTP $status)."
    }
    return $result
}

Export-ModuleMember -Function Invoke-LifeTrackApi
