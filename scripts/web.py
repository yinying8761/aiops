# -*- coding: utf-8 -*-
"""启动 / 停止前端 Vite 开发服务器。

以独立分离进程启动（DETACHED），避免随 Agent 的工具调用结束而被回收。
用法：
    python scripts/web.py start
    python scripts/web.py stop
    python scripts/web.py status
"""
import locale
import os
import shutil
import socket
import subprocess
import sys
import time

# 控制台输出编码要与 cmd 窗口的代码页一致（中文 Windows 默认 GBK/936），
# 否则 bat 双击运行时中文会显示成乱码。
try:
    _enc = locale.getpreferredencoding(False)
    sys.stdout.reconfigure(encoding=_enc, errors="replace")
    sys.stderr.reconfigure(encoding=_enc, errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_DIR = os.path.join(ROOT, "aiops-web")
LOGS = os.path.join(ROOT, "logs")
PID_FILE = os.path.join(LOGS, "web.pid")

def _find_node():
    """定位 node 可执行文件。

    优先用 PATH 上的 node；找不到时再探测常见的 DSH 运行时目录。
    不写死绝对路径，避免把本机用户名带进仓库。
    """
    exe = shutil.which("node")
    if exe:
        return exe
    candidates = []
    home = os.path.expanduser("~")
    candidates.append(os.path.join(
        home, ".dsh", "dsh-runtimes", "dsh-primary-runtime",
        "dependencies", "node", "bin", "node.exe"))
    for c in candidates:
        if os.path.exists(c):
            return c
    return "node"


def _find_pnpm():
    """定位 pnpm 入口（.mjs）；找不到时回退到 PATH 上的 pnpm。"""
    home = os.path.expanduser("~")
    mjs = os.path.join(
        home, ".dsh", "dsh-runtimes", "dsh-primary-runtime",
        "dependencies", "pnpm", "bin", "pnpm.mjs")
    if os.path.exists(mjs):
        return mjs
    return shutil.which("pnpm") or "pnpm"


NODE = _find_node()
PNPM = _find_pnpm()
PORT = 5173


def port_open(port=PORT):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


def start():
    os.makedirs(LOGS, exist_ok=True)
    if port_open():
        print(f"[已运行] Vite 已在 :{PORT}")
        return 0

    log_path = os.path.join(LOGS, "web.log")
    out = open(log_path, "w", encoding="utf-8")
    # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP：脱离父进程
    flags = 0x00000008 | 0x00000200
    env = dict(os.environ)
    env["PATH"] = os.path.dirname(NODE) + os.pathsep + env.get("PATH", "")

    proc = subprocess.Popen(
        [NODE, PNPM, "run", "dev"],
        cwd=WEB_DIR,
        stdout=out,
        stderr=subprocess.STDOUT,
        env=env,
        creationflags=flags,
        close_fds=True,
    )
    with open(PID_FILE, "w", encoding="utf-8") as f:
        f.write(str(proc.pid))
    print(f"[启动] Vite PID={proc.pid}，等待就绪 ...")

    for _ in range(40):
        time.sleep(1)
        if port_open():
            print(f"[就绪] http://127.0.0.1:{PORT}")
            return 0
    print("[超时] Vite 未在 40 秒内就绪，请查看 logs/web.log")
    return 1


def stop():
    if os.path.exists(PID_FILE):
        with open(PID_FILE, encoding="utf-8") as f:
            pid = f.read().strip()
        if pid:
            subprocess.run(["taskkill", "/F", "/T", "/PID", pid],
                           capture_output=True, check=False)
            print(f"[停止] 已结束 Vite (PID {pid})")
        os.remove(PID_FILE)
    # 兜底：按端口占用者清理
    if port_open():
        out = subprocess.run(
            ["netstat", "-ano", "-p", "TCP"],
            capture_output=True, text=True).stdout
        for line in out.splitlines():
            if f":{PORT}" in line and "LISTENING" in line:
                pid = line.split()[-1]
                subprocess.run(["taskkill", "/F", "/PID", pid],
                               capture_output=True, check=False)
                print(f"[停止] 清理端口占用进程 {pid}")
    return 0


def status():
    print(f"Vite :{PORT} -> {'OK' if port_open() else 'DOWN'}")
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    sys.exit({"start": start, "stop": stop, "status": status}.get(cmd, status)())
