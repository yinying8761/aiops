# -*- coding: utf-8 -*-
"""AIOps 接口冒烟测试：TC-03 告警风暴聚合、TC-04 分级。

用法：python scripts/smoke_test.py [alert|storm|all]
"""
import json
import sys
import time
import urllib.request

ALERT_BASE = "http://127.0.0.1:8082"


def post(url, payload):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get(url):
    with urllib.request.urlopen(url, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def test_storm(n=100):
    """TC-03：同一服务连续上报 n 条同类告警，应聚合为 1 个事件。"""
    print(f"=== TC-03 告警风暴：连续上报 {n} 条 connection_pool 告警 ===")
    payload = [{
        "instanceId": 1,
        "alertType": "connection_pool",
        "content": f"数据库连接池耗尽，获取连接超时（第 {i + 1} 条）",
        "metricValue": 98.0,
    } for i in range(n)]

    start = time.time()
    result = post(f"{ALERT_BASE}/alerts/batch", payload)
    cost = (time.time() - start) * 1000
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"HTTP 往返耗时: {cost:.0f} ms")

    data = result.get("data") or {}
    print()
    print("--- 断言 ---")
    ok1 = data.get("eventCreated") == 1
    print(f"[{'PASS' if ok1 else 'FAIL'}] 100 条告警仅创建 1 个聚合事件（实际 {data.get('eventCreated')}）")
    ok2 = data.get("aggregated") == n - 1
    print(f"[{'PASS' if ok2 else 'FAIL'}] {n - 1} 条被聚合去重（实际 {data.get('aggregated')}）")
    print(f"[INFO] 去重率 {data.get('dedupRate')}%")

    # 校验事件的 count
    events = get(f"{ALERT_BASE}/alerts/events?pageNum=1&pageSize=5&status=")
    evs = (events.get("data") or {}).get("records") or []
    if evs:
        newest = evs[0]
        print(f"[INFO] 最新事件 id={newest['id']} count={newest['count']} "
              f"level={newest['level']} status={newest['status']}")
        ok3 = newest["count"] >= n
        print(f"[{'PASS' if ok3 else 'FAIL'}] 聚合事件 count={newest['count']}（应 >= {n}）")
    return ok1 and ok2


def test_level():
    """TC-04：上报 CPU 告警应分级为 P0/P1。"""
    print()
    print("=== TC-04 告警分级 ===")
    cases = [
        ("cpu", 96.0, None, "CPU 使用率 96%，超过 95 阈值 → P0"),
        ("cpu", 88.0, None, "CPU 使用率 88%，未超阈值 → P1"),
        ("memory", 97.5, None, "内存 97.5% → P0"),
        ("network", 50.0, None, "网络延迟正常 → P2"),
        ("database", None, None, "数据库默认 → P1"),
        ("unknown_type", None, None, "未知类型 → P2 兜底"),
    ]
    all_ok = True
    for alert_type, metric, declared, desc in cases:
        payload = {"instanceId": 2, "alertType": alert_type,
                   "content": desc, "metricValue": metric}
        if declared:
            payload["level"] = declared
        r = post(f"{ALERT_BASE}/alerts", payload)
        level = (r.get("data") or {}).get("level")
        print(f"  {desc:<45} => {level}")
    print("[INFO] 分级规则由 LevelResolver 依据 aiops.alert.rules 推导")
    return all_ok


def test_dashboard():
    print()
    print("=== 大屏概览 ===")
    d = get(f"{ALERT_BASE}/alerts/dashboard").get("data") or {}
    print(json.dumps(d, ensure_ascii=False, indent=2)[:1200])


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode in ("storm", "all"):
        test_storm()
    if mode in ("alert", "all"):
        test_level()
        test_dashboard()
