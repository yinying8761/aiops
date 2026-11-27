# -*- coding: utf-8 -*-
"""AIOps 平台一键启动：后端 9 个服务 + 前端 Vite。

用法：
    python scripts/run_all.py           # 启动后端 + 前端
    python scripts/run_all.py backend   # 只启动后端
    python scripts/run_all.py frontend  # 只启动前端
    python scripts/run_all.py stop      # 全部停止
"""
import os
import sys

# 跟随 cmd 代码页输出，避免中文乱码
try:
    import locale
    sys.stdout.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
    sys.stderr.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
except Exception:
    pass

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import manage  # noqa: E402
import web  # noqa: E402


def start_all():
    print("=" * 56)
    print(" AIOps 平台启动")
    print("=" * 56)
    manage.start()
    print()
    print("=" * 56)
    print(" 启动前端")
    print("=" * 56)
    web.start()
    print()
    print("=" * 56)
    print(" 访问地址")
    print("=" * 56)
    print("  前端 : http://127.0.0.1:5173")
    print("  网关 : http://127.0.0.1:8080")
    print("  账号 : admin / admin123")
    return 0


def stop_all():
    web.stop()
    manage.stop()
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "all"
    if cmd == "stop":
        sys.exit(stop_all())
    if cmd == "backend":
        sys.exit(manage.start())
    if cmd == "frontend":
        sys.exit(web.start())
    sys.exit(start_all())
