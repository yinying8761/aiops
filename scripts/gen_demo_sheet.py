# -*- coding: utf-8 -*-
"""生成《验收当天演示步骤单页》—— A4 一页，可直接打印。

用法：python scripts/gen_demo_sheet.py
输出：docs/验收演示步骤单页.docx
"""
import os

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "docs")
OUT = os.path.join(OUT_DIR, "AIOps平台-验收演示步骤单页.docx")

ACCENT = RGBColor(0x2B, 0x5F, 0xDF)
GREY = RGBColor(0x66, 0x70, 0x88)


def set_font(run, cn="宋体", en="Times New Roman", size=10.5, bold=False):
    run.font.name = en
    run._element.rPr.rFonts.set(qn("w:eastAsia"), cn)
    run.font.size = Pt(size)
    run.font.bold = bold


def para(doc, text, size=10, bold=False, cn="宋体", space_after=2,
         indent=False, color=None, align=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_after = Pt(space_after)
    pf.space_before = Pt(0)
    pf.line_spacing = 1.12
    if indent:
        pf.left_indent = Cm(0.5)
    if align is not None:
        p.alignment = align
    r = p.add_run(text)
    set_font(r, cn=cn, size=size, bold=bold)
    if color is not None:
        r.font.color.rgb = color
    return p


def heading(doc, text, num=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(5)
    pf.space_after = Pt(2)
    pf.line_spacing = 1.0
    r = p.add_run((f"{num}  " if num else "") + text)
    set_font(r, cn="黑体", en="Arial", size=10, bold=True)
    r.font.color.rgb = ACCENT
    # 下边框
    pPr = p._p.get_or_add_pPr()
    from docx.oxml import OxmlElement
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "6")
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), "2B5FDF")
    pbdr.append(bottom)
    pPr.append(pbdr)
    return p


def kv_table(doc, rows, widths=(3.2, 13.3), size=8):
    t = doc.add_table(rows=0, cols=2)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for k, v in rows:
        cells = t.add_row().cells
        cells[0].width = Cm(widths[0])
        cells[1].width = Cm(widths[1])
        for idx, txt in enumerate((k, v)):
            cell = cells[idx]
            cell.text = ""
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.line_spacing = 1.0
            r = p.add_run(txt)
            set_font(r, size=size, bold=(idx == 0),
                     cn="黑体" if idx == 0 else "宋体")
    return t


def build():
    doc = Document()

    # A4 + 窄边距
    sec = doc.sections[0]
    sec.page_width = Cm(21.0)
    sec.page_height = Cm(29.7)
    sec.left_margin = Cm(1.8)
    sec.right_margin = Cm(1.8)
    sec.top_margin = Cm(1.2)
    sec.bottom_margin = Cm(1.0)

    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal.font.size = Pt(10)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")

    # ---------- 标题 ----------
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.0
    r = p.add_run("AI 智能运维告警分析与处置平台（AIOps）")
    set_font(r, cn="黑体", en="Arial", size=14, bold=True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.0
    r = p.add_run("验收当天演示步骤单页")
    set_font(r, cn="黑体", en="Arial", size=12, bold=True)
    r.font.color.rgb = ACCENT

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.0
    r = p.add_run("《软件工程综合实践R》SJ003303 · 第 11 组「不想动项目组」"
                  " · 郑颖浩 / 常辰 / 赵佳伟")
    set_font(r, size=8)
    r.font.color.rgb = GREY

    # ---------- 1 启动 ----------
    heading(doc, "启动系统（约 90 秒）", "①")
    kv_table(doc, [
        ("方式 A（推荐）", "双击项目根目录 aiops\\启动.bat —— 自动启动 9 个后端服务 + 前端，并打开浏览器"),
        ("方式 B（命令行）", "cd aiops  然后  python scripts\\run_all.py"),
        ("前置条件", "MySQL 8（3306）与 Redis（6379）须已启动；JDK 17+ 已安装"),
        ("访问地址", "http://127.0.0.1:5173      登录：admin / admin123"),
        ("停止服务", "双击 aiops\\stop.bat"),
    ])

    # ---------- 2 自检 ----------
    heading(doc, "验收前自检（约 3 分钟）", "②")
    para(doc, "在 aiops 目录执行：  python scripts\\acceptance.py",
         size=9.5, indent=True, space_after=2)
    kv_table(doc, [
        ("做什么", "自动清库重建 → 清 Redis → 重启全部服务 → 跑全链路 → 校验前端"),
        ("判断标准", "末行显示「11/11 项通过」即环境正常，可开始演示"),
        ("若未通过", "按提示排查：MySQL/Redis 未启动、端口被占用、jar 未构建（需 mvn clean package）"),
    ])

    # ---------- 3 演示路径 ----------
    heading(doc, "演示主线：故障演练台 → 一键演示", "③")
    para(doc, "打开左侧「故障演练台」，点击【开始一键演示】，系统自动完成 5 步，"
              "右侧日志区实时滚动输出：",
         size=8.5, indent=True, space_after=2)

    rows = [
        ("第 1 步 注入故障",
         "向被监控的模拟订单系统注入「数据库连接池耗尽」，连接池使用率升至 98%"),
        ("第 2 步 推送告警",
         "批量上报 20 条同类告警，模拟真实生产环境的「告警风暴」"),
        ("第 3 步 聚合去重",
         "基于 MD5 指纹与 5 分钟窗口聚合，20 条告警 → 1 个聚合事件"),
        ("第 4 步 Agent 分析",
         "四阶段流水线：分类分级 → 根因分析（调用 4 个工具查真实数据）→ 处置建议 → 报告生成"),
        ("第 5 步 自动派单",
         "依据分析结果自动创建工单并派发给负责人，日志显示工单号"),
    ]
    t = doc.add_table(rows=0, cols=2)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for k, v in rows:
        cells = t.add_row().cells
        cells[0].width = Cm(3.4)
        cells[1].width = Cm(13.5)
        for idx, txt in enumerate((k, v)):
            cells[idx].text = ""
            pp = cells[idx].paragraphs[0]
            pp.paragraph_format.space_after = Pt(0)
            pp.paragraph_format.space_before = Pt(0)
            pp.paragraph_format.line_spacing = 1.0
            rr = pp.add_run(txt)
            set_font(rr, size=8, bold=(idx == 0),
                     cn="黑体" if idx == 0 else "宋体")

    # ---------- 4 关键展示点 ----------
    heading(doc, "关键展示点（评委最关心这三件事）", "④")

    para(doc, "展示点 1 ｜ 智能体是真的在「调用工具干活」，不是知识库问答",
         size=8.5, bold=True, cn="黑体", space_after=1)
    para(doc, "演示后进「告警中心」→ 点任意事件【AI 分析】，右侧可见「工具调用（4 次）」："
              "query_metrics（指标时序）、query_history_alerts（历史告警）、"
              "query_service_dependency（依赖拓扑）、query_knowledge（处置经验）。"
              "左侧「执行过程」逐条显示参数与返回结果——均为真实 HTTP 调用。",
         size=8, indent=True, space_after=3)

    para(doc, "展示点 2 ｜ 告警聚合显著降噪，节省大模型成本",
         size=8.5, bold=True, cn="黑体", space_after=1)
    para(doc, "「告警大屏」右侧仪表盘显示实时降噪率（实测约 98%）："
              "100 条同类告警只触发 1 次 Agent 分析而非 100 次，"
              "直接决定大规模告警下系统是否可用、成本是否可控。",
         size=8, indent=True, space_after=3)

    para(doc, "展示点 3 ｜ 工单状态机与防误操作",
         size=8.5, bold=True, cn="黑体", space_after=1)
    para(doc, "「工单处置」→【详情】查看处置时间线；对「已关闭」工单再点【重开】会被拒绝，"
              "提示「不允许从『已关闭』流转到『处理中』」——体现状态机约束。",
         size=8, indent=True, space_after=3)

    # ---------- 5 可选切换 ----------
    heading(doc, "可选演示：中间件与真实大模型切换", "⑤")
    kv_table(doc, [
        ("切换真实 DeepSeek",
         "设环境变量 DEEPSEEK_API_KEY 与 LLM_PROVIDER=deepseek 后重启 agent 服务；"
         "页面右上角标签会由「模拟模型」变为「DeepSeek」"),
        ("启用 Nacos 注册",
         "启动 Nacos（默认 127.0.0.1:8848）后，设 NACOS_ENABLED=true 重启各服务"),
        ("启用 RabbitMQ",
         "启动 RabbitMQ 后，将 alert-service 的 aiops.mq.enabled 置为 true，"
         "告警分析投递由本地线程池切换为 MQ 队列消费"),
        ("说明",
         "以上均为「可插拔」设计：默认不装中间件也能完整运行，"
         "装上后仅需改配置即可切换，不影响已有功能"),
    ])

    # ---------- 页脚提示 ----------
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6)
    r = p.add_run("技术文档见《SJ004106+软件工程综合实践R-项目技术文档.docx》"
                  "  ·  代码说明见 aiops/README.md")
    set_font(r, size=8)
    r.font.color.rgb = GREY

    os.makedirs(OUT_DIR, exist_ok=True)
    doc.save(OUT)
    return OUT


if __name__ == "__main__":
    path = build()
    print(f"已生成: {path}")
    print(f"大小: {os.path.getsize(path)} 字节")
