# -*- coding: utf-8 -*-
"""用 headless Chrome 截图验证前端页面真实渲染效果。

登录态注入方式：在 vite 的 public 目录写一个同源的 _autologin.html，
它会先把 token 写入 localStorage 再跳转目标页。
必须同源，否则 localStorage 与 http://127.0.0.1:5173 不共享。

用法：python scripts/screenshot.py <页面路径> <输出文件> [login]
例如：python scripts/screenshot.py /dashboard dashboard.png login
"""
import json
import os
import subprocess
import sys
import urllib.request

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "out")
PUBLIC_DIR = os.path.join(ROOT, "aiops-web", "public")
PROFILE_DIR = os.path.join(OUT_DIR, "chrome-profile")
WEB_BASE = "http://127.0.0.1:5173"


def login_and_get_token():
    req = urllib.request.Request(
        "http://127.0.0.1:8080/auth/login",
        data=json.dumps({"username": "admin", "password": "admin123"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())["data"]


def write_autologin(target_path):
    """在同源下生成注入脚本页面。"""
    info = login_and_get_token()
    user = {
        "userId": info["userId"],
        "username": info["username"],
        "nickname": info["nickname"],
        "roles": info["roles"],
    }
    html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>autologin</title></head><body>
<script>
localStorage.setItem('aiops_token', {json.dumps(info['token'])});
localStorage.setItem('aiops_user', {json.dumps(json.dumps(user, ensure_ascii=False))});
location.replace({json.dumps(target_path)});
</script>
</body></html>"""
    os.makedirs(PUBLIC_DIR, exist_ok=True)
    path = os.path.join(PUBLIC_DIR, "_autologin.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)
    return "/_autologin.html"


def screenshot(url, out_file, width=1600, height=1000, budget_ms=8000):
    os.makedirs(os.path.dirname(out_file), exist_ok=True)
    if os.path.exists(out_file):
        os.remove(out_file)
    cmd = [
        CHROME,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--hide-scrollbars",
        "--disable-dev-shm-usage",
        f"--user-data-dir={PROFILE_DIR}",
        f"--window-size={width},{height}",
        f"--screenshot={out_file}",
        f"--virtual-time-budget={budget_ms}",
        url,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    ok = os.path.exists(out_file) and os.path.getsize(out_file) > 3000
    if not ok:
        print("STDERR:", result.stderr[-1200:])
    return ok


def main():
    if len(sys.argv) < 3:
        print("用法: python scripts/screenshot.py <path> <outfile> [login]")
        return 1

    path = sys.argv[1]
    out_file = os.path.join(OUT_DIR, sys.argv[2])
    need_login = len(sys.argv) > 3 and sys.argv[3] == "login"

    if need_login:
        # 先写注入页，再先访问一次以完成 localStorage 写入（同一 profile），
        # 然后用同一 profile 直接访问目标页
        entry = write_autologin(path)
        screenshot(WEB_BASE + entry, os.path.join(OUT_DIR, "_warmup.png"), budget_ms=4000)
        url = WEB_BASE + path
    else:
        url = WEB_BASE + path

    ok = screenshot(url, out_file)
    print(f"{'OK  ' if ok else 'FAIL'} {path} -> {out_file}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
