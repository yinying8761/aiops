@echo off
REM 强制控制台使用 936(GBK) 代码页，与本文件的 GBK 编码保持一致。
REM 若不设置，从某些环境（如已切到 UTF-8 的终端）启动时会显示中文乱码。
chcp 936 >nul 2>nul
cd /d "%~dp0"
title AIOps 平台一键启动

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
echo   AI 智能运维告警分析与处置平台（AIOps）
echo   《软件工程综合实践R》SJ003303 - 第 11 组
echo ======================================================
echo.
echo [信息] Python: %PY%
echo.

echo ------------------------------------------------------
echo  第 1 步：启动后端服务（约需 60 秒）
echo ------------------------------------------------------
%PY% scripts\manage.py start

echo.
echo ------------------------------------------------------
echo  第 2 步：启动前端
echo ------------------------------------------------------
%PY% scripts\web.py start

echo.
echo ------------------------------------------------------
echo  第 3 步：打开浏览器
echo ------------------------------------------------------
timeout /t 2 /nobreak >nul
start "" "http://127.0.0.1:5173"

echo.
echo ======================================================
echo   启动完成
echo ======================================================
echo.
echo   前端地址 : http://127.0.0.1:5173
echo   网关地址 : http://127.0.0.1:8080
echo   登录账号 : admin / admin123
echo.
echo   常用操作：
echo     - 查看状态 : 双击 status.bat
echo     - 停止服务 : 双击 stop.bat
echo     - 交付验收 : python scripts\acceptance.py
echo.
echo   提示：本窗口可以关闭，服务在后台继续运行。
echo ======================================================
echo.
pause
