# -*- coding: utf-8 -*-
"""为 AI 分析页准备一个已分析完成的事件，然后截图。

分析页需要先有结果才能看到完整渲染效果，因此这里：
1. 推送告警创建事件；
2. 调用同步分析接口跑完四阶段；
3. 再截图该事件的 /alerts/{id}/analyze 页面。
"""
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from screenshot import OUT_DIR, screenshot, write_autologin, WEB_BASE  # noqa: E402

ALERT = "http://127.0.0.1:8082"
AGENT = "http://127.0.0.1:8084"


def post(url, payload=None, timeout=180):
    data = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    req = urllib.request.Request(
        url, data=data, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def main():
    # 复用最近一个已分析完成的事件；没有就新建
    with urllib.request.urlopen(f"{ALERT}/alerts/events?pageNum=1&pageSize=20", timeout=20) as r:
        events = json.loads(r.read().decode())["data"]["records"]

    target = next((e for e in events if e.get("analyzeStatus") == "DONE"), None)

    if target is None:
        print("没有已分析事件，创建新的 ...")
        payload = [{
            "instanceId": 1,
            "alertType": "connection_pool",
            "content": f"数据库连接池耗尽，获取连接超时（第 {i + 1} 条）",
            "metricValue": 98.0,
        } for i in range(10)]
        res = post(f"{ALERT}/alerts/batch", payload)
        event_id = res["data"]["eventIds"][0]
        print(f"事件 {event_id} 已创建，开始分析 ...")
        result = post(f"{AGENT}/agent/analyze?eventId={event_id}")
        print(f"分析完成，根因：{result['data']['rca']['rootCause']}")
    else:
        event_id = target["id"]
        print(f"复用已分析事件 {event_id}")
        # 重新分析，确保 agent_trace 等过程数据存在
        result = post(f"{AGENT}/agent/analyze?eventId={event_id}")
        print(f"分析完成，工具调用 {len(result['data']['rca']['toolTrace'])} 次")

    # 截图分析页
    path = f"/alerts/{event_id}/analyze"
    write_autologin(path)
    screenshot(WEB_BASE + "/_autologin.html", os.path.join(OUT_DIR, "_warmup.png"),
               budget_ms=5000)
    out_file = os.path.join(OUT_DIR, "analyze.png")
    ok = screenshot(WEB_BASE + path, out_file, budget_ms=12000)
    print(f"{'OK  ' if ok else 'FAIL'} {path} -> out/analyze.png")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
