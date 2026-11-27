$body = '{"instanceId":1,"alertType":"cpu","content":"CPU 使用率 96.5%，已持续 5 分钟","metricValue":96.5}'
$tmp = "$env:TEMP\alert_body.json"
[System.IO.File]::WriteAllText($tmp, $body, (New-Object System.Text.UTF8Encoding($false)))

Write-Output "=== 第 1 条 ==="
& "C:\Windows\System32\curl.exe" -s -X POST "http://127.0.0.1:8082/alerts" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$tmp"
Write-Output "`n=== 第 2 条（同指纹，应建事件并触发分析）==="
& "C:\Windows\System32\curl.exe" -s -X POST "http://127.0.0.1:8082/alerts" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$tmp"
Write-Output "`n=== 第 3 条（应只累加计数）==="
& "C:\Windows\System32\curl.exe" -s -X POST "http://127.0.0.1:8082/alerts" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$tmp"
Write-Output ""