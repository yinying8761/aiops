@echo off
REM 强制控制台使用 936(GBK) 代码页，与本文件的 GBK 编码保持一致。
REM 若不设置，从某些环境（如已切到 UTF-8 的终端）启动时会显示中文乱码。
chcp 936 >nul 2>nul
cd /d "%~dp0"
title AIOps 中间件管理

REM ---- 定位 Python ----
set "PY="
where python >nul 2>nul
if not errorlevel 1 (
    set "PY=python"
) else (
    py -3 --version >nul 2>nul
    if not errorlevel 1 set "PY=py -3"
)

if "%PY%"=="" (
    echo.
    echo [错误] 未找到 Python，无法运行。
    echo        请安装 Python 3.8+，安装时勾选 "Add Python to PATH"。
    echo.
    pause
    exit /b 1
)

echo ======================================================
echo   中间件管理（Nacos + RabbitMQ，基于 Docker）
echo ======================================================
echo.
echo   1. 启动中间件      2. 停止中间件      3. 查看状态
echo.
set /p CHOICE=请输入序号（回车默认 3）: 
if "%CHOICE%"=="" set CHOICE=3
if "%CHOICE%"=="1" %PY% scripts\middleware.py start
if "%CHOICE%"=="2" %PY% scripts\middleware.py stop
if "%CHOICE%"=="3" %PY% scripts\middleware.py status
echo.
pause
