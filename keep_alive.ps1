# Keep-Alive script to ping server every 10 minutes
param (
    [string]$Url = "https://journify-backend-hiky.onrender.com/",
    [int]$IntervalMinutes = 10
)

$IntervalSeconds = $IntervalMinutes * 60

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "[*] Keep-Alive Service Started (PowerShell)" -ForegroundColor Green
Write-Host "[*] Target URL: $Url" -ForegroundColor Yellow
Write-Host "[*] Interval:   Every $IntervalMinutes minutes ($IntervalSeconds seconds)" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Cyan

while ($true) {
    $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 30 -UseBasicParsing -UserAgent "JournifyKeepAlive/1.0"
        $sw.Stop()
        Write-Host "[$timestamp] PING SUCCESS -> Status: $($response.StatusCode) ($($sw.ElapsedMilliseconds)ms)" -ForegroundColor Green
    }
    catch {
        $sw.Stop()
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            Write-Host "[$timestamp] PING RESPONSE -> HTTP $status ($($sw.ElapsedMilliseconds)ms)" -ForegroundColor Yellow
        } else {
            Write-Host "[$timestamp] PING FAILED -> $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    Write-Host "Sleeping for $IntervalMinutes minutes..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $IntervalSeconds
}
