@echo off
REM 强制控制台使用 936(GBK) 代码页，与本文件的 GBK 编码保持一致。
REM 若不设置，从某些环境（如已切到 UTF-8 的终端）启动时会显示中文乱码。
chcp 936 >nul 2>nul
cd /d "%~dp0"
title AIOps 停止服务

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

echo 正在停止全部服务 ...
echo.
%PY% scripts\web.py stop
%PY% scripts\manage.py stop
echo.
echo 已停止。
pause
