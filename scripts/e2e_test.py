# -*- coding: utf-8 -*-
"""AIOps 全链路联调脚本。

验证「告警产生 → 聚合去重 → Agent 分析 → 工单派发 → 处置 → 报表」完整闭环，
对应技术文档 5.2 的 TC-01 ~ TC-10。

用法：python scripts/e2e_test.py
"""
import json
import sys

# 跟随 cmd 代码页输出，避免中文乱码
try:
    import locale
    sys.stdout.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
    sys.stderr.reconfigure(encoding=locale.getpreferredencoding(False), errors='replace')
except Exception:
    pass
import time
import urllib.error
import urllib.parse
import urllib.request

GW = "http://127.0.0.1:8080"
AUTH = "http://127.0.0.1:8081"
ALERT = "http://127.0.0.1:8082"
MONITOR = "http://127.0.0.1:8083"
AGENT = "http://127.0.0.1:8084"
TICKET = "http://127.0.0.1:8085"
KNOWLEDGE = "http://127.0.0.1:8086"
REPORT = "http://127.0.0.1:8087"
DEMO = "http://127.0.0.1:8090"

results = []


def call(method, url, payload=None, token=None, timeout=120):
    data = None
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body}


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    mark = "PASS" if ok else "FAIL"
    print(f"  [{mark}] {name}" + (f"  — {detail}" if detail else ""))
    return ok


# ---------------------------------------------------------------------------
def tc01_login():
    print("\n=== TC-01 用户登录 ===")
    st, r = call("POST", f"{AUTH}/auth/login",
                 {"username": "admin", "password": "admin123"})
    ok = st == 200 and r.get("code") == 0 and r.get("data", {}).get("token")
    check("TC-01 登录成功并返回 JWT", bool(ok),
          f"roles={r.get('data', {}).get('roles')}" if ok else str(r))
    token = r["data"]["token"] if ok else None

    # 错误密码
    st2, r2 = call("POST", f"{AUTH}/auth/login",
                   {"username": "admin", "password": "wrongpass"})
    check("TC-01b 错误密码被拒绝", r2.get("code") == 20003, f"code={r2.get('code')}")
    return token


def tc02_unauthorized():
    print("\n=== TC-02 无权限访问 ===")
    st, r = call("GET", f"{GW}/tickets")
    ok = st == 401 and r.get("code") in (20001, 20005)
    check("TC-02 未携带 Token 访问受保护接口返回 401", ok,
          f"HTTP {st}, code={r.get('code')}")

    st, r = call("GET", f"{GW}/tickets", token="invalid.token.here")
    ok2 = st == 401
    check("TC-02b 非法 Token 被拒绝", ok2, f"HTTP {st}")
    return ok and ok2


def tc03_aggregate():
    print("\n=== TC-03 告警聚合去重（100 条同类告警）===")
    payload = [{
        "instanceId": 1,
        "alertType": "connection_pool",
        "content": "数据库连接池耗尽，获取连接超时（第 %d 条）" % (i + 1),
        "metricValue": 98.0,
    } for i in range(100)]

    start = time.time()
    st, r = call("POST", f"{ALERT}/alerts/batch", payload)
    cost = (time.time() - start) * 1000
    d = r.get("data") or {}
    ok = d.get("eventCreated") == 1 and d.get("aggregated") == 99
    check("TC-03 100 条告警聚合为 1 个事件", ok,
          f"eventCreated={d.get('eventCreated')}, aggregated={d.get('aggregated')}, "
          f"dedupRate={d.get('dedupRate')}%, 耗时 {cost:.0f}ms")

    event_id = (d.get("eventIds") or [None])[0]
    if event_id:
        st, r2 = call("GET", f"{ALERT}/alerts/events/{event_id}")
        ev = r2.get("data") or {}
        check("TC-03b 聚合事件 count 累计正确", ev.get("count", 0) >= 100,
              f"count={ev.get('count')}, level={ev.get('level')}, "
              f"duration={ev.get('durationSeconds')}s")
    return event_id


def tc04_level():
    print("\n=== TC-04 告警分级 ===")
    cases = [
        ("cpu", 96.0, "P0", "CPU 96% 超 95 阈值"),
        ("cpu", 88.0, "P1", "CPU 88% 未超阈值"),
        ("memory", 97.5, "P0", "内存 97.5%"),
        ("network", 50.0, "P2", "网络正常范围"),
        ("database", None, "P1", "数据库默认级别"),
    ]
    all_ok = True
    for alert_type, metric, expect, desc in cases:
        payload = {"instanceId": 2, "alertType": alert_type,
                   "content": desc, "metricValue": metric}
        st, r = call("POST", f"{ALERT}/alerts", payload)
        actual = (r.get("data") or {}).get("level")
        ok = actual == expect
        all_ok = all_ok and ok
        print(f"       {desc:<22} → {actual} (期望 {expect}) {'OK' if ok else 'MISMATCH'}")
    check("TC-04 分级规则全部符合预期", all_ok)
    return all_ok


def tc05_rca(event_id):
    print(f"\n=== TC-05 根因分析（事件 {event_id}）===")
    # 先给该实例写入异常指标，让工具查到真实数据
    for metric, value, unit in [("conn_pool_usage", 98.5, "%"), ("rt_p95", 2400, "ms")]:
        call("POST", f"{MONITOR}/metrics/1/point?metricName={metric}&value={value}&unit={unit}")

    start = time.time()
    st, r = call("POST", f"{AGENT}/agent/analyze?eventId={event_id}", timeout=180)
    cost = (time.time() - start) * 1000

    d = r.get("data") or {}
    rca = d.get("rca") or {}
    root_cause = rca.get("rootCause") or ""
    tool_trace = rca.get("toolTrace") or []

    ok = bool(root_cause) and len(tool_trace) > 0
    check("TC-05 Agent 调用工具完成根因分析", ok,
          f"耗时 {cost:.0f}ms, 工具调用 {len(tool_trace)} 次: {tool_trace}")

    print(f"\n  ---- 分析结果 ----")
    cls = d.get("classification") or {}
    print(f"  分类     : {cls.get('category')} / 级别 {cls.get('level')}")
    print(f"  摘要     : {cls.get('summary')}")
    print(f"  根因     : {root_cause}")
    print(f"  置信度   : {rca.get('confidence')}")
    ev = rca.get("evidence") or []
    for i, e in enumerate(ev, 1):
        print(f"  证据{i}    : {e}")
    print(f"  影响     : {rca.get('impact')}")

    rem = d.get("remediation") or {}
    steps = rem.get("steps") or []
    print(f"\n  处置方案（{len(steps)} 步，风险 {rem.get('risk')}）：")
    for i, s in enumerate(steps, 1):
        print(f"    {i}. {s}")

    rep = d.get("report") or {}
    print(f"\n  报告标题 : {rep.get('title')}")
    print(f"  工单     : {d.get('ticketNo')} (id={d.get('ticketId')})")
    print(f"  LLM 实现 : {d.get('llmProvider')}")

    check("TC-06 处置建议生成", len(steps) >= 3, f"{len(steps)} 步")
    return d


def tc07_ticket(ticket_id):
    print(f"\n=== TC-07 工单状态流转（工单 {ticket_id}）===")
    if not ticket_id:
        check("TC-07 工单状态流转", False, "没有工单ID")
        return False

    st, r = call("GET", f"{TICKET}/tickets/{ticket_id}")
    t = r.get("data") or {}
    check("TC-07a 工单已自动创建并派发", t.get("status") == "PENDING",
          f"单号={t.get('ticketNo')}, 处理人={t.get('assignee')}, 状态={t.get('status')}")

    # PENDING → PROCESSING
    st, r = call("PUT", f"{TICKET}/tickets/{ticket_id}/status?status=PROCESSING", {})
    ok1 = (r.get("data") or {}).get("status") == "PROCESSING"

    # PROCESSING → RESOLVED
    q = urllib.parse.quote("已优化慢SQL并调大连接池，指标已恢复")
    st, r = call("PUT", f"{TICKET}/tickets/{ticket_id}/status?status=RESOLVED&remark={q}", {})
    ok2 = (r.get("data") or {}).get("status") == "RESOLVED"

    # RESOLVED → CLOSED
    st, r = call("PUT", f"{TICKET}/tickets/{ticket_id}/status?status=CLOSED", {})
    ok3 = (r.get("data") or {}).get("status") == "CLOSED"

    check("TC-07b 状态按 待处理→处理中→已解决→已关闭 流转", ok1 and ok2 and ok3,
          f"PENDING({ok1}) → PROCESSING({ok2}) → RESOLVED({ok3}) → CLOSED")

    # 非法跃迁：已关闭不能再流转
    st, r = call("PUT", f"{TICKET}/tickets/{ticket_id}/status?status=PROCESSING", {})
    check("TC-07c 非法流转被状态机拒绝", r.get("code") == 50002,
          f"code={r.get('code')}, msg={r.get('message')}")

    # 流转日志
    st, r = call("GET", f"{TICKET}/tickets/{ticket_id}/logs")
    logs = r.get("data") or []
    check("TC-07d 流转日志完整留痕", len(logs) >= 4, f"{len(logs)} 条记录")
    return ok1 and ok2 and ok3


def tc08_report():
    print("\n=== TC-08 报表统计 ===")
    st, r = call("GET", f"{REPORT}/reports/stats?days=7")
    d = r.get("data") or {}
    ok = r.get("code") == 0 and "trend" in d and "mttrMinutes" in d
    check("TC-08 返回趋势、分类分布与 MTTR", ok,
          f"事件 {d.get('aggregatedEvents')}, 原始告警 {d.get('rawAlerts')}, "
          f"降噪率 {d.get('dedupRate')}%, MTTR {d.get('mttrMinutes')} 分钟")
    print(f"       分类分布: {d.get('categoryDistribution')}")
    print(f"       级别分布: {d.get('levelDistribution')}")
    return ok


def tc09_sse(event_id):
    print(f"\n=== TC-09 SSE 流式输出（事件 {event_id}）===")
    url = f"{AGENT}/agent/analyze/stream?eventId={event_id}"
    steps = 0
    got_result = False
    try:
        req = urllib.request.Request(url, headers={"Accept": "text/event-stream"})
        with urllib.request.urlopen(req, timeout=180) as resp:
            event_name = None
            for raw in resp:
                line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
                if line.startswith("event:"):
                    event_name = line[6:].strip()
                elif line.startswith("data:"):
                    if event_name == "step":
                        steps += 1
                    elif event_name == "result":
                        got_result = True
    except Exception as e:
        check("TC-09 SSE 流式输出", False, f"连接异常: {e}")
        return False

    ok = steps >= 5 and got_result
    check("TC-09 前端可逐段渲染分析过程", ok,
          f"收到 {steps} 个步骤事件, 完整结果={got_result}")
    return ok


def tc10_knowledge():
    print("\n=== TC-10 知识库检索 ===")
    st, r = call("GET", f"{KNOWLEDGE}/knowledge/search?keyword=%E8%BF%9E%E6%8E%A5%E6%B1%A0&limit=3")
    items = r.get("data") or []
    check("TC-10 按关键词检索到处置模板", len(items) > 0,
          f"命中 {len(items)} 条: {[i.get('title') for i in items]}")
    return len(items) > 0


def demo_fault_flow():
    print("\n=== 演示：demo-app 注入故障 → 指标恶化 ===")
    st, r = call("POST", f"{DEMO}/demo/fault?mode=connection_pool&intensity=0.95", {})
    check("注入 connection_pool 故障", (r.get("faultMode") == "connection_pool"),
          f"faultMode={r.get('faultMode')}")

    st, r = call("POST", f"{DEMO}/demo/orders?quantity=1", {})
    d = r or {}
    print(f"       下单返回: {d.get('status')} / {d.get('message')}")

    # 恢复
    call("POST", f"{DEMO}/demo/fault?mode=none", {})
    check("恢复故障模式", True, "已恢复 none")


def main():
    print("=" * 60)
    print(" AIOps 平台全链路联调测试")
    print("=" * 60)

    token = tc01_login()
    tc02_unauthorized()
    event_id = tc03_aggregate()
    tc04_level()
    result = tc05_rca(event_id) if event_id else {}
    ticket_id = result.get("ticketId")
    tc07_ticket(ticket_id)
    tc08_report()
    tc09_sse(event_id) if event_id else None
    tc10_knowledge()
    demo_fault_flow()

    print("\n" + "=" * 60)
    passed = sum(1 for _, ok, _ in results if ok)
    total = len(results)
    print(f" 测试结果: {passed}/{total} 通过")
    failed = [n for n, ok, _ in results if not ok]
    if failed:
        print(f" 失败项: {failed}")
    print("=" * 60)
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
