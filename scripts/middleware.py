# -*- coding: utf-8 -*-
"""中间件管理：用 Docker 启停 Nacos 与 RabbitMQ。

与 manage.py 分开的原因：中间件只在需要演示「服务注册发现 / MQ 削峰」
时才用到，普通启动不依赖 Docker，避免没装 Docker 的同学被卡住。
"""
import subprocess
import sys
import time

NACOS = "aiops-nacos"
RABBIT = "aiops-rabbitmq"

CREATE = {
    NACOS: [
        "docker", "run", "-d", "--name", NACOS, "--restart", "unless-stopped",
        "-p", "8848:8848", "-p", "9848:9848",
        "-e", "MODE=standalone",
        "nacos/nacos-server:v2.4.3",
    ],
    RABBIT: [
        "docker", "run", "-d", "--name", RABBIT, "--restart", "unless-stopped",
        "-p", "5672:5672", "-p", "15672:15672",
        "-e", "RABBITMQ_DEFAULT_USER=aiops",
        "-e", "RABBITMQ_DEFAULT_PASS=aiops123",
        "rabbitmq:3.13-management",
    ],
}

PORTS = {NACOS: [("8848", "Nacos 控制台")], RABBIT: [("5672", "AMQP"), ("15672", "管理台")]}


def run(args, check=False):
    return subprocess.run(args, capture_output=True, text=True,
                          encoding="utf-8", errors="ignore").stdout.strip()


def docker_ready():
    out = run(["docker", "version", "--format", "{{.Server.Version}}"])
    if not out or "error" in out.lower():
        print("[错误] Docker 未运行。请先启动 Docker Desktop。")
        return False
    print(f"[自检] Docker Engine {out}")
    return True


def container_state(name):
    out = run(["docker", "inspect", "-f", "{{.State.Running}}", name])
    if out == "true":
        return "running"
    if out == "false":
        return "stopped"
    return "missing"


def start():
    if not docker_ready():
        return 1
    for name in (NACOS, RABBIT):
        state = container_state(name)
        if state == "running":
            print(f"[已运行] {name}")
            continue
        if state == "stopped":
            run(["docker", "start", name])
            print(f"[启动] {name}")
            continue
        print(f"[创建] {name} （首次需拉取镜像，可能较慢）")
        result = subprocess.run(CREATE[name], capture_output=True, text=True,
                                encoding="utf-8", errors="ignore")
        if result.returncode != 0:
            print(f"[失败] {name}: {result.stderr.strip()[:200]}")
            return 1

    print()
    print("等待中间件就绪 ...")
    time.sleep(12)
    check()

    print()
    print("=" * 52)
    print(" 中间件已启动，请确认 .env 中：")
    print("   NACOS_ENABLED=true")
    print("   MQ_ENABLED=true")
    print(" 然后重新执行 启动.bat")
    print("=" * 52)
    return 0


def stop():
    for name in (NACOS, RABBIT):
        if container_state(name) == "running":
            run(["docker", "stop", name])
            print(f"[停止] {name}")
    print("中间件已停止（容器保留，可随时 start 恢复）")
    return 0


def check():
    """检查端口与控制台可用性。"""
    import socket

    print()
    print("=" * 52)
    print(" 中间件状态")
    print("=" * 52)
    for name, ports in PORTS.items():
        state = container_state(name)
        mark = "OK  " if state == "running" else "DOWN"
        print(f"  {mark} {name:<16} {state}")
        for port, label in ports:
            with socket.socket() as s:
                s.settimeout(1.0)
                ok = s.connect_ex(("127.0.0.1", int(port))) == 0
            print(f"       :{port:<6} {label:<12} {'可访问' if ok else '不可访问'}")
    print()
    print("  Nacos      : http://127.0.0.1:8848/nacos")
    print("  RabbitMQ   : http://127.0.0.1:15672  (aiops / aiops123)")
    return 0


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    if action == "start":
        return start()
    if action == "stop":
        return stop()
    if action == "status":
        if not docker_ready():
            return 1
        return check()
    print("用法: python scripts/middleware.py [start|stop|status]")
    return 1


if __name__ == "__main__":
    sys.exit(main())
