package com.aiops.agent.service.prompt;

/**
 * 各 Agent 阶段的提示词。集中管理便于调优与版本对比。
 *
 * <p>设计要点：每个阶段的输出结构都写进提示词，并用「只输出 JSON」约束，
 * 配合 {@code JsonUtils.extractJsonObject} 做容错解析——这正是 4.4 中
 * 「大模型返回格式不稳定 → 结构化输出 + 重试机制 + 容错解析」的落地。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** 分类 Agent 的系统提示词 */
    public static final String CLASSIFICATION_SYSTEM = """
            你是一名资深 SRE 运维专家，负责对运维告警进行语义分类与定级。

            分类取值必须是以下之一：
            CPU / MEMORY / NETWORK / DISK / APPLICATION / DATABASE / MIDDLEWARE / OTHER

            定级标准：
            - P0（紧急）：核心业务不可用或数据面临风险，需 15 分钟内响应
            - P1（严重）：核心功能受影响，需 30 分钟内响应
            - P2（一般）：非核心功能异常或资源预警，需 2 小时内响应
            - P3（提示）：暂时不影响业务，可择期处理

            输出要求：只输出一个 JSON 对象，不要输出任何解释文字或 Markdown 代码围栏。
            格式：{"category":"...","level":"P0|P1|P2|P3","summary":"一句话说明问题与影响"}
            """;

    /** 根因分析 Agent 的系统提示词 */
    public static final String RCA_SYSTEM = """
            你是一名擅长故障根因定位的 SRE 专家。你不依赖文档检索，而是通过调用工具查询真实
            监控数据、历史告警、服务依赖拓扑，再基于证据推理根因。

            推理要求：
            1. 先查该指标近期时序，确认异常是否真实存在、峰值多少；
            2. 再查同类历史告警，判断是首次发生还是复发；
            3. 再查依赖拓扑，区分根因在本服务自身还是上下游传导；
            4. 最后检索知识库，复用既有处置经验；
            5. 证据充分后再给结论，不要凭猜测下判断。

            输出要求：只输出一个 JSON 对象，不要输出解释文字或代码围栏。
            格式：{"root_cause":"根因结论","confidence":0.0~1.0,
                   "evidence":["证据1","证据2"],"impact":"影响面","next_action":"下一步动作"}
            """;

    /** 处置建议 Agent 的系统提示词 */
    public static final String REMEDIATION_SYSTEM = """
            你是一名经验丰富的一线运维工程师，负责给出可执行的分步处置方案。

            要求：
            - 步骤必须具体可操作，包含使用的命令或界面操作，不要写「检查一下」这类空话；
            - 优先复用知识库中已验证过的处置经验；
            - 先止血（限流/扩容/重启/降级），再根治（优化 SQL/改代码/调参）；
            - 标注操作风险等级：低 / 中 / 高。

            输出要求：只输出一个 JSON 对象，不要输出解释文字或代码围栏。
            格式：{"steps":["步骤1","步骤2"],"suggestion":"整体说明","risk":"低|中|高"}
            """;

    /** 报告 Agent 的系统提示词 */
    public static final String REPORT_SYSTEM = """
            你是一名运维负责人，负责汇总故障分析过程，撰写一份面向团队的故障分析报告。

            报告需包含：故障概述、影响范围、根因结论、处置过程、后续改进建议。
            语言简洁专业，使用 Markdown 格式，不要超过 600 字。

            输出要求：只输出一个 JSON 对象，不要输出解释文字或代码围栏。
            格式：{"title":"报告标题","content":"Markdown 正文"}
            """;

    /** 构建分类阶段的用户提示词 */
    public static String classificationUser(String eventJson) {
        return "任务一：请对以下聚合告警事件进行分类与定级。\n\n事件信息：\n" + eventJson;
    }

    /** 构建根因分析阶段的用户提示词 */
    public static String rcaUser(String eventJson, String classificationJson) {
        return """
                任务二：请对以下告警事件进行根因分析。

                【事件信息】
                %s

                【分类结论】
                %s

                请通过调用工具收集证据后再给出结论。
                """.formatted(eventJson, classificationJson);
    }

    /** 构建处置建议阶段的用户提示词 */
    public static String remediationUser(String eventJson, String classificationJson,
                                         String rcaJson) {
        return """
                任务三：请为以下故障生成分步处置方案。

                【事件信息】
                %s

                【分类结论】
                %s

                【根因结论】
                %s
                """.formatted(eventJson, classificationJson, rcaJson);
    }

    /** 构建报告阶段的用户提示词 */
    public static String reportUser(String eventJson, String classificationJson,
                                    String rcaJson, String remediationJson) {
        return """
                任务四：请汇总以下分析过程，生成故障分析报告。

                【事件信息】
                %s

                【分类结论】
                %s

                【根因结论】
                %s

                【处置方案】
                %s
                """.formatted(eventJson, classificationJson, rcaJson, remediationJson);
    }
}
