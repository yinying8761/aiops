# -*- coding: utf-8 -*-
"""生成三个 .bat 脚本并转成 GBK 编码（cmd 默认代码页 936）。

为什么不用 chcp 65001：批处理文件本身以 GBK 保存，
若再把控制台切到 UTF-8(65001)，cmd 解析/回显中文就会乱码。
保持控制台默认 936 代码页、文件也用 GBK，中文即可正常显示。
Python 脚本内部再自行 reconfigure 为 UTF-8 输出，两边各自统一。
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

HEADER = """@echo off
REM 强制控制台使用 936(GBK) 代码页，与本文件的 GBK 编码保持一致。
REM 若不设置，从某些环境（如已切到 UTF-8 的终端）启动时会显示中文乱码。
chcp 936 >nul 2>nul
cd /d "%~dp0"
title {title}

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
"""

START_BAT = HEADER.format(title="AIOps 平台一键启动") + """
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
%PY% scripts\\manage.py start

echo.
echo ------------------------------------------------------
echo  第 2 步：启动前端
echo ------------------------------------------------------
%PY% scripts\\web.py start

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
echo     - 交付验收 : python scripts\\acceptance.py
echo.
echo   提示：本窗口可以关闭，服务在后台继续运行。
echo ======================================================
echo.
pause
"""

STATUS_BAT = HEADER.format(title="AIOps 服务状态") + """
%PY% scripts\\manage.py status
echo.
%PY% scripts\\web.py status
echo.
pause
"""

STOP_BAT = HEADER.format(title="AIOps 停止服务") + """
echo 正在停止全部服务 ...
echo.
%PY% scripts\\web.py stop
%PY% scripts\\manage.py stop
echo.
echo 已停止。
pause
"""

MW_BAT = HEADER.format(title="AIOps 中间件管理") + """
echo ======================================================
echo   中间件管理（Nacos + RabbitMQ，基于 Docker）
echo ======================================================
echo.
echo   1. 启动中间件      2. 停止中间件      3. 查看状态
echo.
set /p CHOICE=请输入序号（回车默认 3）: 
if "%CHOICE%"=="" set CHOICE=3
if "%CHOICE%"=="1" %PY% scripts\\middleware.py start
if "%CHOICE%"=="2" %PY% scripts\\middleware.py stop
if "%CHOICE%"=="3" %PY% scripts\\middleware.py status
echo.
pause
"""

FILES = {
    "启动.bat": START_BAT,
    "status.bat": STATUS_BAT,
    "stop.bat": STOP_BAT,
    "中间件.bat": MW_BAT,
}

if __name__ == "__main__":
    for name, text in FILES.items():
        path = os.path.join(ROOT, name)
        text = text.lstrip("\ufeff").replace("\r\n", "\n").replace("\n", "\r\n")
        with open(path, "wb") as f:
            f.write(text.encode("gbk"))
        print(f"[生成] {name}: {os.path.getsize(path)} 字节 (GBK)")
