# AIOps 平台一键启动脚本
# 用法：powershell -File scripts/start-all.ps1
$ErrorActionPreference = "Continue"

$ROOT = Split-Path -Parent $PSScriptRoot
$JAVA = "D:\Java\Java21\bin\java.exe"
$LOGS = Join-Path $ROOT "logs"

New-Item -ItemType Directory -Force -Path $LOGS | Out-Null

# 服务启动顺序：先被依赖的，后依赖方
$SERVICES = @(
    @{ Name = "auth";      Port = 8081; Jar = "aiops-auth-service\target\aiops-auth-service.jar";      Wait = 15 },
    @{ Name = "monitor";   Port = 8083; Jar = "aiops-monitor-service\target\aiops-monitor-service.jar";   Wait = 12 },
    @{ Name = "alert";     Port = 8082; Jar = "aiops-alert-service\target\aiops-alert-service.jar";       Wait = 14 },
    @{ Name = "knowledge"; Port = 8086; Jar = "aiops-knowledge-service\target\aiops-knowledge-service.jar"; Wait = 12 },
    @{ Name = "ticket";    Port = 8085; Jar = "aiops-ticket-service\target\aiops-ticket-service.jar";     Wait = 12 },
    @{ Name = "agent";     Port = 8084; Jar = "aiops-agent-service\target\aiops-agent-service.jar";       Wait = 14 },
    @{ Name = "report";    Port = 8087; Jar = "aiops-report-service\target\aiops-report-service.jar";      Wait = 12 },
    @{ Name = "demo";      Port = 8090; Jar = "aiops-demo-app\target\aiops-demo-app.jar";                  Wait = 10 },
    @{ Name = "gateway";   Port = 8080; Jar = "aiops-gateway\target\aiops-gateway.jar";                    Wait = 12 }
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " AIOps 平台启动中 ..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

foreach ($svc in $SERVICES) {
    $jarPath = Join-Path $ROOT $svc.Jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "[跳过] $($svc.Name): 未找到 $($svc.Jar)，请先执行 mvn package" -ForegroundColor Yellow
        continue
    }

    # 若端口已被占用，说明服务已在运行
    $inUse = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
    if ($inUse) {
        Write-Host "[已运行] $($svc.Name) 端口 $($svc.Port)" -ForegroundColor DarkGray
        continue
    }

    $logFile = Join-Path $LOGS "$($svc.Name).log"
    $proc = Start-Process -FilePath $JAVA `
        -ArgumentList "-Xms256m", "-Xmx512m", "-Dfile.encoding=UTF-8", "-jar", "`"$jarPath`"" `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$logFile.err" `
        -PassThru -WindowStyle Hidden

    Write-Host "[启动] $($svc.Name) (PID $($proc.Id), 端口 $($svc.Port))" -ForegroundColor Green
    Start-Sleep -Seconds $svc.Wait
}

Write-Host ""
Write-Host "============ 健康检查 ============" -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline) {
    $allUp = $true
    foreach ($svc in $SERVICES) {
        $up = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
        if (-not $up) { $allUp = $false }
    }
    if ($allUp) { break }
    Start-Sleep -Seconds 5
}

foreach ($svc in $SERVICES) {
    $up = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
    $status = if ($up) { "OK  " } else { "DOWN" }
    $color = if ($up) { "Green" } else { "Red" }
    Write-Host "  $status  $($svc.Name.PadRight(10)) :$($svc.Port)" -ForegroundColor $color
}

Write-Host ""
Write-Host "网关地址: http://127.0.0.1:8080" -ForegroundColor Cyan
Write-Host "前端地址: http://127.0.0.1:5173  (需另行启动 pnpm dev)" -ForegroundColor Cyan
Write-Host "日志目录: $LOGS" -ForegroundColor Cyan
