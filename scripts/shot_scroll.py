# -*- coding: utf-8 -*-
"""对长页面做「滚动后截图」，用于检查首屏之外的内容。

用法：python scripts/shot_scroll.py <path> <outfile> <scrollY> [login]
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from screenshot import (  # noqa: E402
    CHROME, OUT_DIR, PROFILE_DIR, WEB_BASE, write_autologin
)


def shot_scrolled(path, out_file, scroll_y, width=1600, height=1000, budget_ms=10000):
    """Chrome 的 --screenshot 无法直接滚动，用临时注入脚本实现。"""
    os.makedirs(os.path.dirname(out_file), exist_ok=True)
    if os.path.exists(out_file):
        os.remove(out_file)

    # 在同源下生成一个自动滚动的包装页
    wrapper = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;overflow:hidden}}
iframe{{border:0;width:{width}px;height:{height}px;display:block}}
</style></head><body>
<iframe id="f" src="{path}"></iframe>
<script>
const f = document.getElementById('f');
f.onload = () => {{
  setTimeout(() => {{
    try {{ f.contentWindow.scrollTo(0, {scroll_y}); }} catch (e) {{}}
  }}, 3500);
}};
</script>
</body></html>"""
    public_dir = os.path.join(os.path.dirname(OUT_DIR), "aiops-web", "public")
    os.makedirs(public_dir, exist_ok=True)
    wrapper_path = os.path.join(public_dir, "_scroll.html")
    with open(wrapper_path, "w", encoding="utf-8") as fp:
        fp.write(wrapper)

    cmd = [
        CHROME,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--hide-scrollbars",
        f"--user-data-dir={PROFILE_DIR}",
        f"--window-size={width},{height}",
        f"--screenshot={out_file}",
        f"--virtual-time-budget={budget_ms}",
        f"{WEB_BASE}/_scroll.html",
    ]
    subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return os.path.exists(out_file) and os.path.getsize(out_file) > 3000


if __name__ == "__main__":
    path = sys.argv[1]
    out_file = os.path.join(OUT_DIR, sys.argv[2])
    scroll_y = int(sys.argv[3])
    if len(sys.argv) > 4 and sys.argv[4] == "login":
        write_autologin(path)
        # 先完成登录态注入
        from screenshot import screenshot
        screenshot(WEB_BASE + "/_autologin.html",
                   os.path.join(OUT_DIR, "_warmup.png"), budget_ms=5000)
    ok = shot_scrolled(path, out_file, scroll_y)
    print(f"{'OK  ' if ok else 'FAIL'} scroll={scroll_y} -> {out_file}")
    sys.exit(0 if ok else 1)
