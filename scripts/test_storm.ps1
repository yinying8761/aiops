$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8082"

# TC-03：同一服务连续上报 100 条同类告警（告警风暴）
$sb = New-Object System.Text.StringBuilder
[void]$sb.Append("[")
for ($i = 1; $i -le 100; $i++) {
    if ($i -gt 1) { [void]$sb.Append(",") }
    $content = "Database connection pool exhausted, acquire timeout #$i"
    [void]$sb.Append("{")
    [void]$sb.Append("""instanceId"":1,")
    [void]$sb.Append("""alertType"":""connection_pool"",")
    [void]$sb.Append("""content"":""$content"",")
    [void]$sb.Append("""metricValue"":98.0")
    [void]$sb.Append("}")
}
[void]$sb.Append("]")

$tmp = "$env:TEMP\storm.json"
[System.IO.File]::WriteAllText($tmp, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output "payload bytes: $((Get-Item $tmp).Length)"

Write-Output "=== TC-03: 100 条同类告警批量上报 ==="
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$resp = & "C:\Windows\System32\curl.exe" -s -X POST "$base/alerts/batch" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$tmp"
$sw.Stop()
Write-Output $resp
Write-Output "HTTP cost: $($sw.ElapsedMilliseconds) ms"
