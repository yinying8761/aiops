# -*- coding: utf-8 -*-
"""AIOps 平台一键启动 / 停止 / 状态检查。

用法：
    python scripts/manage.py start     # 启动全部服务
    python scripts/manage.py stop      # 停止全部服务
    python scripts/manage.py status    # 查看端口状态
    python scripts/manage.py restart
"""
import locale
import os
import socket
import subprocess
import sys
import time

# 控制台输出编码要与 cmd 窗口的代码页一致。
# 中文 Windows 的 cmd 默认代码页是 936(GBK)，若 Python 强制输出 UTF-8，
# 在 cmd 里就会显示成乱码。这里跟随系统首选编码，跨语言环境也安全。
try:
    _enc = locale.getpreferredencoding(False)
    sys.stdout.reconfigure(encoding=_enc, errors="replace")
    sys.stderr.reconfigure(encoding=_enc, errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGS = os.path.join(ROOT, "logs")


def find_java():
    """按优先级探测 java 可执行文件，避免硬编码路径换机后失效。"""
    # 1) 环境变量 JAVA_HOME
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", "java.exe")
        if os.path.exists(candidate):
            return candidate
    # 2) 常见 JDK 安装位置
    for path in [
        r"D:\Java\Java21\bin\java.exe",
        r"D:\Java\Java17\bin\java.exe",
        r"C:\Program Files\Java\jdk-21\bin\java.exe",
        r"C:\Program Files\Java\jdk-17\bin\java.exe",
        r"C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe",
    ]:
        if os.path.exists(path):
            return path
    # 3) PATH 中的 java
    return "java"


JAVA = find_java()

# 启动顺序：被依赖的先起
SERVICES = [
    ("auth",      8081, r"aiops-auth-service\target\aiops-auth-service.jar",       16),
    ("monitor",   8083, r"aiops-monitor-service\target\aiops-monitor-service.jar", 14),
    ("alert",     8082, r"aiops-alert-service\target\aiops-alert-service.jar",     16),
    ("knowledge", 8086, r"aiops-knowledge-service\target\aiops-knowledge-service.jar", 14),
    ("ticket",    8085, r"aiops-ticket-service\target\aiops-ticket-service.jar",   14),
    ("agent",     8084, r"aiops-agent-service\target\aiops-agent-service.jar",     16),
    ("report",    8087, r"aiops-report-service\target\aiops-report-service.jar",   14),
    ("demo",      8090, r"aiops-demo-app\target\aiops-demo-app.jar",               12),
    ("gateway",   8080, r"aiops-gateway\target\aiops-gateway.jar",                 14),
]

PID_FILE = os.path.join(LOGS, "pids.txt")


def load_dotenv():
    """读取项目根目录的 .env（若存在），返回 dict。

    真实凭据只放在本地 .env 中，不随代码入库。
    格式：KEY=VALUE，支持 # 注释与空行。
    """
    env = {}
    path = os.path.join(ROOT, ".env")
    if not os.path.exists(path):
        return env
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            env[key.strip()] = value.strip().strip('"').strip("'")
    return env


def build_env():
    """构造子进程环境变量：系统环境优先，其次 .env。"""
    env = dict(os.environ)
    for k, v in load_dotenv().items():
        env.setdefault(k, v)
    return env


def port_open(port, host="127.0.0.1", timeout=0.4):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(timeout)
        return s.connect_ex((host, port)) == 0


def load_pids():
    pids = {}
    if os.path.exists(PID_FILE):
        with open(PID_FILE, encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split(",")
                if len(parts) == 2:
                    pids[parts[0]] = int(parts[1])
    return pids


def save_pids(pids):
    os.makedirs(LOGS, exist_ok=True)
    with open(PID_FILE, "w", encoding="utf-8") as f:
        for name, pid in pids.items():
            f.write(f"{name},{pid}\n")


def preflight():
    """启动前自检：jar 与 MySQL/Redis 是否就绪，避免双击后静默失败。"""
    problems = []

    missing = [name for name, _, jar_rel, _ in SERVICES
               if not os.path.exists(os.path.join(ROOT, jar_rel))]
    if missing:
        problems.append(f"缺少 {len(missing)} 个 jar（{'、'.join(missing)}），请先执行：mvn clean package -DskipTests")

    if not os.path.exists(JAVA) and JAVA == "java":
        problems.append("未找到 java，请安装 JDK 17+ 或设置 JAVA_HOME")

    for label, host, port in [("MySQL", "127.0.0.1", 3306), ("Redis", "127.0.0.1", 6379)]:
        if not port_open(port, host):
            problems.append(f"{label} 未启动（{host}:{port} 连接失败）")

    # 数据库密码必须来自环境变量或 .env
    pwd = build_env().get("MYSQL_PASSWORD", "")
    if not pwd or pwd == "CHANGE_ME":
        problems.append(
            "未配置 MYSQL_PASSWORD：请复制 .env.example 为 .env 并填写数据库密码")

    if problems:
        print("=" * 52)
        print(" 启动前自检未通过")
        print("=" * 52)
        for p in problems:
            print(f"  ✗ {p}")
        print()
        return False

    print(f"[自检] Java   : {JAVA}")
    print(f"[自检] MySQL  : 127.0.0.1:3306 OK")
    print(f"[自检] Redis  : 127.0.0.1:6379 OK")
    print(f"[自检] jar    : {len(SERVICES)} 个齐全")
    print()
    return True


def _netstat_lines(port):
    """返回 netstat 中涉及该端口的行（本地端口或对端端口）。"""
    try:
        out = subprocess.run(
            ["netstat", "-ano"], capture_output=True, text=True,
            encoding="utf-8", errors="ignore", timeout=10).stdout
    except Exception:
        return []
    return [ln for ln in out.splitlines() if f":{port} " in ln]


def _port_taken(port):
    """端口是否被真正占用。

    只看 LISTENING 与 ESTABLISHED：TIME_WAIT/CLOSE_WAIT 是正常关闭后的残留，
    操作系统允许重新绑定（SO_REUSEADDR），若把它们当成占用会导致服务启动被误跳过。
    """
    for line in _netstat_lines(port):
        parts = line.split()
        if len(parts) >= 4 and parts[-2] in ("LISTENING", "ESTABLISHED", "BOUND"):
            return True
    return False


def port_owner(port):
    """返回占用该端口的进程描述；无法确定时返回空串。

    Windows 上浏览器可能把 8080-8090 当作客户端临时端口占用，
    这类占用表现为 ESTABLISHED 而非 LISTENING，仅靠 port_open 探测不到，
    因此用 netstat 兜底，避免只报「启动失败」却无法定位元凶。
    """
    for line in _netstat_lines(port):
        parts = line.split()
        if len(parts) < 4 or parts[-2] not in ("LISTENING", "ESTABLISHED", "BOUND"):
            continue
        pid = parts[-1]
        if not pid.isdigit():
            continue
        try:
            name = subprocess.run(
                ["tasklist", "/FI", f"PID eq {pid}", "/NH"],
                capture_output=True, text=True,
                encoding="utf-8", errors="ignore", timeout=10).stdout
            first = name.strip().splitlines()[0] if name.strip() else ""
            exe = first.split()[0] if first.split() else "unknown"
        except Exception:
            exe = "unknown"
        return f"PID {pid} ({exe})"
    return ""


def start():
    os.makedirs(LOGS, exist_ok=True)
    print("=" * 52)
    print(" AIOps 平台启动中 ...")
    print("=" * 52)

    preflight()
    pids = load_pids()

    for name, port, jar_rel, wait in SERVICES:
        jar = os.path.join(ROOT, jar_rel)
        if not os.path.exists(jar):
            print(f"[跳过] {name}: 缺少 {jar_rel}，请先 mvn package")
            continue
        if port_open(port):
            print(f"[已运行] {name:<10} :{port}")
            continue

        # 端口空闲但 netstat 仍能看到残留连接，说明被别处临时占用，提前给出版权提示
        if _port_taken(port):
            owner = port_owner(port) or "未知进程"
            print(f"[警告] {name:<10} :{port} 被 {owner} 占用，跳过该服务")
            continue

        log_file = os.path.join(LOGS, f"{name}.log")
        with open(log_file, "w", encoding="utf-8") as out:
            proc = subprocess.Popen(
                [JAVA, "-Xms128m", "-Xmx384m", "-XX:MaxMetaspaceSize=192m",
                 "-Dfile.encoding=UTF-8", "-jar", jar],
                stdout=out, stderr=subprocess.STDOUT,
                cwd=ROOT,
                env=build_env(),
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
        pids[name] = proc.pid
        print(f"[启动] {name:<10} :{port}  PID={proc.pid}")
        time.sleep(wait)

    save_pids(pids)
    print()
    status()


def status():
    print("=" * 52)
    print(" 服务状态")
    print("=" * 52)
    all_up = True
    for name, port, _, _ in SERVICES:
        ok = port_open(port)
        all_up = all_up and ok
        print(f"  {'OK  ' if ok else 'DOWN'}  {name:<10} :{port}")
    print()
    if all_up:
        print("全部服务已就绪")
    else:
        print("存在未就绪服务，请查看 logs/ 下对应日志")
    print()
    print("网关  : http://127.0.0.1:8080")
    print("前端  : http://127.0.0.1:5173")
    return all_up


def stop():
    pids = load_pids()
    print("停止服务 ...")
    for name, pid in pids.items():
        try:
            subprocess.run(["taskkill", "/F", "/PID", str(pid)],
                           capture_output=True, check=False)
            print(f"[停止] {name} (PID {pid})")
        except Exception as e:
            print(f"[失败] {name}: {e}")
    if os.path.exists(PID_FILE):
        os.remove(PID_FILE)
    print("已停止")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    if cmd == "start":
        start()
    elif cmd == "stop":
        stop()
    elif cmd == "restart":
        stop()
        time.sleep(3)
        start()
    else:
        status()
