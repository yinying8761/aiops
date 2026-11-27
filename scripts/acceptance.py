# -*- coding: utf-8 -*-
"""从零开始的完整验收：清库 → 重建 → 全链路 → 前端页面。

这是交付前的最终验收脚本，模拟「拿到代码的人第一次跑起来」的完整路径，
确保系统可重复复现，而不是依赖上一次残留的数据。

用法：python scripts/acceptance.py
"""
import json
import os
import subprocess
import sys

# 跟随 cmd 代码页输出，避免中文乱码
try:
    import locale
    sys.stdout.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
    sys.stderr.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
except Exception:
    pass
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
MYSQL_PWD = os.environ.get("MYSQL_PASSWORD", "")


def _load_env_if_needed():
    """若未设置 MYSQL_PASSWORD，则尝试从项目根目录的 .env 读取。"""
    global MYSQL_PWD
    if MYSQL_PWD:
        return
    env_path = os.path.join(ROOT, ".env")
    if os.path.exists(env_path):
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("MYSQL_PASSWORD="):
                    MYSQL_PWD = line.split("=", 1)[1].strip().strip('"').strip("'")
                    os.environ["MYSQL_PASSWORD"] = MYSQL_PWD
                    return
REDIS_CLI = os.environ.get("REDIS_CLI", "redis-cli")
PY = sys.executable

results = []


def step(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))
    return ok


def mysql(sql, database=None):
    args = [MYSQL, "-u", "root", f"-p{MYSQL_PWD}", "--default-character-set=utf8mb4", "-e", sql]
    if database:
        args.insert(4, database)
    return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")


def mysql_file(path):
    with open(path, encoding="utf-8") as f:
        sql = f.read()
    return mysql(sql)


def flush_redis():
    """清空 Redis 中本项目的 key（不整体 FLUSHDB，避免影响同实例其它数据）。"""
    keys = [
        "alert:fp:*",
        "alert:fp:event:*",
        "aiops:ticket:seq:*",
        "metric:latest:*",
        "auth:blacklist:*",
    ]
    try:
        removed = 0
        for pattern in keys:
            r = subprocess.run(
                [REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "--scan", "--pattern", pattern],
                capture_output=True, text=True, timeout=15)
            for key in r.stdout.splitlines():
                key = key.strip()
                if not key:
                    continue
                d = subprocess.run(
                    [REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "DEL", key],
                    capture_output=True, text=True, timeout=10)
                removed += int(d.stdout.strip() or 0)
        return True, f"删除 {removed} 个 key"
    except Exception as e:
        return False, str(e)


def http_json(url, payload=None, timeout=180):
    data = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    req = urllib.request.Request(
        url, data=data, method="POST" if payload is not None else "GET",
        headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def section(title):
    print()
    print("=" * 62)
    print(f" {title}")
    print("=" * 62)


def main():
    _load_env_if_needed()
    if not MYSQL_PWD:
        print("ERROR: 未配置数据库密码。请复制 .env.example 为 .env 并填写 MYSQL_PASSWORD。")
        return 1

    section("第 1 步：重建数据库（验证建表脚本可重复执行）")

    r = mysql_file(os.path.join(ROOT, "sql", "01_schema.sql"))
    step("01_schema.sql 执行成功", r.returncode == 0, r.stderr.strip()[:200] if r.returncode else "")

    r = mysql_file(os.path.join(ROOT, "sql", "02_seed.sql"))
    step("02_seed.sql 执行成功", r.returncode == 0, r.stderr.strip()[:200] if r.returncode else "")

    # 关键：清空 Redis 里的指纹缓存。
    # 否则上一步删除的告警事件仍留在 Redis 映射中，新告警会去更新一个已不存在的事件，
    # 造成「看板显示已聚合、实际没有事件」的假象。真实部署里这对应「缓存与库不一致」。
    redis_ok, redis_detail = flush_redis()
    step("Redis 告警缓存已清空", redis_ok, redis_detail)

    # 校验表与种子数据
    r = mysql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='aiops';")
    table_count = 0
    for line in r.stdout.splitlines():
        if line.strip().isdigit():
            table_count = int(line.strip())
    step("14 张表全部创建", table_count == 14, f"实际 {table_count} 张")

    r = mysql("SELECT COUNT(*) FROM sys_user;", "aiops")
    user_count = 0
    for line in r.stdout.splitlines():
        if line.strip().isdigit():
            user_count = int(line.strip())
    step("种子用户已导入", user_count == 4, f"实际 {user_count} 个")

    r = mysql("SELECT COUNT(*) FROM knowledge_template;", "aiops")
    tpl_count = 0
    for line in r.stdout.splitlines():
        if line.strip().isdigit():
            tpl_count = int(line.strip())
    step("知识模板已导入", tpl_count == 8, f"实际 {tpl_count} 条")

    section("第 2 步：重启服务（验证无残留状态依赖）")
    subprocess.run([PY, "-X", "utf8", os.path.join(ROOT, "scripts", "manage.py"), "stop"],
                   capture_output=True, text=True)
    time.sleep(4)
    r = subprocess.run([PY, "-X", "utf8", os.path.join(ROOT, "scripts", "manage.py"), "start"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    ok = "全部服务已就绪" in r.stdout
    step("9 个服务全部就绪", ok, "" if ok else r.stdout[-300:])

    section("第 3 步：全链路业务验证")
    r = subprocess.run([PY, "-X", "utf8", os.path.join(ROOT, "scripts", "e2e_test.py")],
                       capture_output=True, text=True, encoding="utf-8", errors="replace",
                       timeout=600)
    passed = failed = 0
    for line in r.stdout.splitlines():
        if "[PASS]" in line:
            passed += 1
        elif "[FAIL]" in line:
            failed += 1
            print(f"     {line.strip()}")
    step(f"全链路测试 {passed} 项通过", failed == 0 and passed >= 18,
         f"通过 {passed}，失败 {failed}")

    section("第 4 步：前端可用性")
    # 重启后前端不会自动拉起，这里显式启动（若已运行则跳过）
    web_status = subprocess.run(
        [PY, "-X", "utf8", os.path.join(ROOT, "scripts", "web.py"), "start"],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    step("前端服务已启动", "就绪" in web_status.stdout or "已运行" in web_status.stdout,
         web_status.stdout.strip()[-200:])

    try:
        with urllib.request.urlopen("http://127.0.0.1:5173/", timeout=10) as resp:
            html = resp.read().decode("utf-8", errors="replace")
        step("前端首页可访问", resp.status == 200 and 'id="app"' in html)
    except Exception as e:
        step("前端首页可访问", False, str(e))

    try:
        body = http_json("http://127.0.0.1:8080/auth/login",
                         {"username": "admin", "password": "admin123"})
        step("网关转发登录成功", body.get("code") == 0 and body.get("data", {}).get("token"))
    except Exception as e:
        step("网关转发登录成功", False, str(e))

    section("验收结果汇总")
    total = len(results)
    ok_count = sum(1 for _, ok, _ in results if ok)
    print(f"  {ok_count}/{total} 项通过")
    if ok_count != total:
        print("  失败项：")
        for name, ok, detail in results:
            if not ok:
                print(f"    - {name}: {detail}")
    print()
    return 0 if ok_count == total else 1


if __name__ == "__main__":
    sys.exit(main())
