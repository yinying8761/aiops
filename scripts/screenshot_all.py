# -*- coding: utf-8 -*-
"""批量截图所有前端页面，用于视觉验收。

用法：python scripts/screenshot_all.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from screenshot import OUT_DIR, screenshot, write_autologin, WEB_BASE  # noqa: E402

PAGES = [
    ("/dashboard", "dashboard.png"),
    ("/alerts", "alerts.png"),
    ("/tickets", "tickets.png"),
    ("/knowledge", "knowledge.png"),
    ("/reports", "reports.png"),
    ("/demo", "demo.png"),
    ("/users", "users.png"),
]


def main():
    print("=" * 56)
    print(" 前端页面批量截图")
    print("=" * 56)

    # 先通过同源注入页完成登录态写入（同一 Chrome profile 内持久）
    write_autologin("/dashboard")
    screenshot(WEB_BASE + "/_autologin.html", os.path.join(OUT_DIR, "_warmup.png"),
               budget_ms=6000)

    ok_count = 0
    for path, out_name in PAGES:
        out_file = os.path.join(OUT_DIR, out_name)
        ok = screenshot(WEB_BASE + path, out_file, budget_ms=9000)
        ok_count += 1 if ok else 0
        print(f"  {'OK  ' if ok else 'FAIL'}  {path:<14} -> out/{out_name}")

    print()
    print(f"完成 {ok_count}/{len(PAGES)}")
    return 0 if ok_count == len(PAGES) else 1


if __name__ == "__main__":
    sys.exit(main())
