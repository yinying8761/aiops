# AI 智能运维告警分析与处置平台（AIOps）

> 《软件工程综合实践R》SJ003303 —— 第 11 组「不想动项目组」
>
> 郑颖浩（项目经理 / AI 模块） · 常辰（数据库 / 数据层） · 赵佳伟（Java 后端）

基于 **Spring Boot 3 + Spring Cloud Alibaba + Spring AI + Vue 3** 的前后端分离微服务系统，
实现「告警接入 → 指纹聚合去重 → 多 Agent 分析 → 自动派单 → 处置 → 统计报表」的完整运维闭环。

---

## 一、系统架构

```
                          ┌──────────────────────────┐
                          │  Vue 3 + Element Plus    │
                          │  ECharts + Axios (5173)  │
                          └────────────┬─────────────┘
                                       │ /api 代理
                          ┌────────────▼─────────────┐
                          │  aiops-gateway  :8080    │
                          │  路由 / JWT 鉴权 / CORS   │
                          └────────────┬─────────────┘
        ┌──────────┬──────────┬────────┼────────┬──────────┬──────────┐
        ▼          ▼          ▼        ▼        ▼          ▼          ▼
   auth:8081  alert:8082  monitor:8083  agent:8084  ticket:8085  knowledge:8086  report:8087
   认证授权    告警聚合     指标采集      多Agent编排   工单流转      经验知识库      统计报表
        │          │          │        │        │          │          │
        └──────────┴──────────┴────────┼────────┴──────────┴──────────┘
                                       │
                          ┌────────────▼─────────────┐
                          │  MySQL 8  /  Redis 7     │
                          └──────────────────────────┘

   demo-app :8090  ← 被监控的模拟订单系统（制造故障，作为被运维对象）
```

### 模块职责

| 模块 | 端口 | 职责 |
|---|---|---|
| `aiops-gateway` | 8080 | Spring Cloud Gateway：路由、JWT 统一鉴权、CORS |
| `aiops-auth-service` | 8081 | 登录、JWT 签发、RBAC、用户/角色管理 |
| `aiops-alert-service` | 8082 | 告警接入、**指纹聚合去重**、分级、状态管理 |
| `aiops-monitor-service` | 8083 | 指标采集（30s）、时序查询、手动注入 |
| `aiops-agent-service` | 8084 | **多 Agent 编排、Tool Calling、SSE 流式输出** |
| `aiops-ticket-service` | 8085 | 工单自动创建、派单、**状态机**流转 |
| `aiops-knowledge-service` | 8086 | 处置经验模板（症状—根因—解法）检索复用 |
| `aiops-report-service` | 8087 | 统计报表、MTTR、降噪率、Markdown 导出 |
| `aiops-demo-app` | 8090 | 模拟订单系统，可注入 cpu/memory/连接池等故障 |
| `aiops-common` | — | 统一返回、全局异常、JWT、MyBatis/Redis 公共配置 |

---

## 二、核心技术实现

### 1. 告警聚合去重算法（对应文档 2.5 算法一）

```java
// 1. 归一化字段 + 计算指纹
String fingerprint = MD5(instanceId + "|" + alertType + "|" + 时间窗口(ts, 5min));

// 2. Redis 原子计数（首次计数时设置窗口过期）
Long count = redis.opsForValue().increment("alert:fp:" + fingerprint);

// 3. 计数 1→2 时建事件并触发 Agent；继续增加只累加 count
if (count == 2) {
    createEventAndTriggerAgent(...);
} else if (count > 2) {
    eventMapper.increaseCount(eventId, ts);   // 不再重复分析
}
```

**实测效果**：100 条同类告警 → 1 个聚合事件，**去重率 99%**，下游 Agent 调用从 100 次降为 1 次。

### 2. 多 Agent 编排（对应文档 2.5 算法二）

`agent-service` 按「分类 → 根因 → 处置 → 报告」链式编排，每阶段输出作为下一阶段输入：

| 阶段 | 输入 | 输出契约 |
|---|---|---|
| 分类 Agent | 聚合告警事件 | `{category, level, summary}` |
| 根因 Agent | 事件 + 分类结论 | `{root_cause, evidence, confidence, impact}` |
| 处置 Agent | 事件 + 分类 + 根因 | `{steps[], suggestion, risk}` |
| 报告 Agent | 前三阶段全部结论 | `{title, content}`（Markdown） |

### 3. Tool Calling / ReAct（对应文档 2.5 算法三）

根因分析**不依赖文档问答**，而是通过工具主动查数据。已注册 4 个工具：

| 工具 | 作用 | 数据来源 |
|---|---|---|
| `query_metrics` | 查监控指标时序，判断是否真异常、峰值多少 | monitor-service |
| `query_history_alerts` | 查同类历史告警，判断是否复发 | alert-service |
| `query_service_dependency` | 查依赖拓扑，区分自身问题还是上下游传导 | 静态拓扑表 |
| `query_knowledge` | 检索处置经验模板，复用既有方案 | knowledge-service |

ReAct 循环最大 6 轮，每轮的思考、工具调用、工具结果全部落库 `agent_trace` 并通过 SSE 实时推送到前端。

### 4. 大模型双实现（可插拔）

| 配置 | 实现 | 说明 |
|---|---|---|
| `aiops.llm.provider=mock`（默认） | `MockLlmProvider` | 规则+模板产出结构化结果，**无需 API Key 即可完整演示** |
| `aiops.llm.provider=deepseek` | `DeepSeekLlmProvider` | 基于 Spring AI 调用 DeepSeek，带指数退避重试 + 失败降级回 Mock |

两者接口契约完全一致。启用真实模型只需设置环境变量：

```powershell
$env:DEEPSEEK_API_KEY = "sk-xxxx"
$env:LLM_PROVIDER = "deepseek"
```

> Mock 模式下的**工具调用是真实执行的**：同样会实际调用 monitor/alert/knowledge 服务取数，
> 只是推理文本由模板生成。因此「智能体调用工具干活」的业务闭环与真实模型一致。

### 5. 工单状态机（对应 TC-07）

```
PENDING ──接单──▶ PROCESSING ──解决──▶ RESOLVED ──关闭──▶ CLOSED
   │                  │                   │
   └──取消──▶ CLOSED  └──挂起──▶ PENDING   └──重开──▶ PROCESSING
```

非法跃迁（如「已关闭 → 处理中」）由白名单校验直接拒绝，返回 `50002`。每次变更写 `ticket_log` 形成完整时间线。

---

## 三、快速开始

### 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 本项目在 Java 21 下构建（`--release 17` 兼容） |
| Maven | 3.9+ | 已配置阿里云镜像 |
| Node.js | 20+ | 前端构建 |
| MySQL | 8.0+ | `127.0.0.1:3306` |
| Redis | 6+ | `127.0.0.1:6379` |

### 0. 配置环境变量（必做）

仓库中不含任何真实凭据。首次运行前复制模板并填写：

```powershell
cd aiops
Copy-Item .env.example .env
notepad .env          # 填写 MYSQL_PASSWORD 等
```

`.env` 已被 `.gitignore` 忽略，不会进入版本库。可用项见 `.env.example` 注释。

### 1. 初始化数据库

```powershell
$my = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
Get-Content sql\01_schema.sql -Raw -Encoding UTF8 | & $my -u root -p"你的密码" --default-character-set=utf8mb4
Get-Content sql\02_seed.sql   -Raw -Encoding UTF8 | & $my -u root -p"你的密码" --default-character-set=utf8mb4
```

> `02_seed.sql` 可重复执行（会先清空主数据表）。
> 各服务配置中的数据库密码写作 `${MYSQL_PASSWORD:CHANGE_ME}`，
> 由 `scripts/manage.py` 从 `.env` 读取后注入，无需改 yml。

### 2. 构建后端

```powershell
$env:JAVA_HOME = "D:\Java\Java21"
mvn clean package -DskipTests
```

### 3. 启动全部服务

```powershell
python scripts\manage.py start     # 按依赖顺序启动 9 个服务
python scripts\manage.py status    # 查看端口状态
python scripts\manage.py stop      # 全部停止
```

### 4. 启动前端

```powershell
python scripts\web.py start        # 独立进程启动 Vite
# 或手动：cd aiops-web && pnpm install && pnpm run dev
```

访问 **http://127.0.0.1:5173**

### 默认账号

> 下为演示用示例账号，密码均为 `123456`（admin 为 `admin123`）。
> 生产环境请自行维护用户体系。

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 系统管理员 |
| `ops_duty` | `123456` | 管理员 / 运维 |
| `ops_db` | `123456` | 运维 / 值班负责人 |
| `ops_backend` | `123456` | 运维 |

---

## 四、功能页面

| 页面 | 路径 | 说明 |
|---|---|---|
| 告警大屏 | `/dashboard` | 24h 趋势、级别分布、降噪率仪表盘、实时事件流（10s 自动刷新） |
| 告警中心 | `/alerts` | 聚合事件列表、筛选、详情抽屉、补偿投递 |
| AI 根因分析 | `/alerts/:id/analyze` | SSE 实时渲染四阶段过程、根因/证据/工具轨迹、处置方案、报告 |
| 工单处置 | `/tickets` | 工单列表、详情、状态流转（含流转时间线） |
| 知识库 | `/knowledge` | 处置经验模板 CRUD、按关键词/分类检索 |
| 统计报表 | `/reports` | 趋势、分类/级别/实例 Top、工单状态、MTTR、报告导出 |
| 故障演练台 | `/demo` | 一键演示：注入故障 → 推告警 → 聚合 → Agent 分析 → 派单 |
| 用户管理 | `/users` | 用户 CRUD、角色分配、启禁用（仅管理员） |

---

## 五、测试

### 交付验收（推荐先跑这个）

```powershell
python scripts\acceptance.py
```

从零验证整套系统可复现：清库重建 → 清 Redis → 重启全部服务 → 全链路 → 前端可用性。
当前 **11/11 项通过**。

> 注意：脚本会 `DROP DATABASE aiops` 后重建，仅用于开发环境验证，不要在生产库上跑。

### 全链路联调

```powershell
$env:PATH = "C:\Program Files\Redis;$env:PATH"   # acceptance.py 需要 redis-cli
python scripts\e2e_test.py
```

覆盖 TC-01 ~ TC-10，当前 **18/18 全部通过**：

| 用例 | 验证点 | 实测结果 |
|---|---|---|
| TC-01 | 登录返回 JWT / 错误密码被拒 | PASS |
| TC-02 | 无 Token 返回 401 / 非法 Token 被拒 | PASS |
| TC-03 | 100 条同类告警聚合为 1 个事件 | PASS（去重率 99%） |
| TC-04 | 分级规则（P0/P1/P2） | PASS |
| TC-05 | Agent 调用工具完成根因分析 | PASS（4 次工具调用） |
| TC-06 | 处置建议生成 | PASS（5 步方案） |
| TC-07 | 工单状态机流转 + 非法流转拦截 | PASS |
| TC-08 | 报表统计（趋势/分类/MTTR/降噪率） | PASS |
| TC-09 | SSE 流式输出 | PASS（31 个步骤事件） |
| TC-10 | 知识库检索 | PASS |

### 视觉验收

```powershell
python scripts\screenshot_all.py      # 批量截图 7 个页面
python scripts\shot_analyze.py        # 分析页（含准备数据）
```

截图输出在 `out/` 目录。

---

## 六、关键技术难点与解决（对应文档 4.4）

| 问题 | 原因 | 解决方案 |
|---|---|---|
| 大模型返回格式不稳定 | LLM 输出 JSON 偶发缺字段/带代码围栏 | `JsonUtils.extractJsonObject` 做「去围栏 + 括号深度平衡截取」，配合重试与规则兜底 |
| 告警风暴打满下游 | 瞬时并发过高 | Redis 原子计数聚合去重 + 有界线程池异步 + 补偿扫描 |
| Agent 工具调用参数不准 | 大模型对参数理解偏差 | 工具 Schema 细化描述 + `AgentTool.argLong/argString` 类型容错 |
| **工单号重复导致建单失败** | ① 内存计数器重启归零；② 改为查库最大值后，并发仍算出同号；③ 唯一键冲突会把事务标记 rollback-only，重试必然继续失败 | 改用 **Redis 原子自增序列**（按天分桶，首用取库中最大号做基线），彻底消除并发冲突 |
| **清库后告警「假聚合」** | Redis 里指纹→事件映射指向已删除的事件，`increaseCount` 影响 0 行却未检查，误报「已聚合」 | 检查 UPDATE 影响行数；为 0 时重建事件。`createEventAndTrigger` 增加幂等复用，避免唯一键冲突 |
| **事务边界放错** | `createEventAndTrigger` 的 `@Transactional` 因内部调用不生效 | 事务上移到 `ingest`，保证「写告警 + 建事件 + 绑定」原子完成 |
| 指标查询 500 | JDBC 返回 `LocalDateTime` 却强转 `Timestamp` | `formatTs` 兼容 LocalDateTime/Timestamp/Date/OffsetDateTime |
| 分析页每次都要重跑 | 无历史结果回显 | 新增 `/agent/result/{eventId}` 从 report+trace+ticket 重建结果 |

---

## 七、目录结构

```
aiops/
├── pom.xml                     # 父工程（统一版本管理）
├── aiops-common/               # 公共模块
├── aiops-gateway/              # 网关
├── aiops-auth-service/         # 认证授权
├── aiops-alert-service/        # 告警聚合（核心算法）
├── aiops-monitor-service/      # 指标采集
├── aiops-agent-service/        # 多 Agent 编排（核心）
├── aiops-ticket-service/       # 工单
├── aiops-knowledge-service/    # 知识库
├── aiops-report-service/       # 报表
├── aiops-demo-app/             # 模拟业务系统
├── aiops-web/                  # Vue 3 前端
│   ├── src/api/                # 接口封装
│   ├── src/views/              # 8 个页面
│   └── src/layout/             # 主布局
├── sql/
│   ├── 01_schema.sql           # 14 张表建表脚本
│   ├── 02_seed.sql             # 种子数据（可重复执行）
│   └── 03_alter_fault_report_steps.sql  # 增量迁移
├── scripts/
│   ├── run_all.py              # 一键启动后端+前端
│   ├── manage.py               # 后端服务启动/停止/状态 + .env 注入
│   ├── middleware.py           # 中间件管理（Docker 启停 Nacos + RabbitMQ）
│   ├── web.py                  # 前端启动/停止
│   ├── acceptance.py           # 交付验收（清库重建→全链路→前端）
│   ├── e2e_test.py             # 全链路联调（TC-01~TC-10）
│   ├── gen_demo_sheet.py       # 生成验收演示单页
│   ├── gen_bats.py             # 生成启动脚本（GBK 编码）
│   ├── screenshot*.py          # 页面截图验收
│   └── smoke_test.py           # 告警接口冒烟测试
├── docs/
│   ├── 项目技术文档.docx        # 六大章节技术文档
│   ├── 综合项目实训任务书.docx   # 任务书
│   ├── AIOps平台-验收演示步骤单页.docx / .pdf
│   └── screenshots/            # 9 张实际运行截图
├── .env.example                # 环境变量模板（复制为 .env 使用）
├── .gitignore
├── 启动.bat / status.bat / stop.bat / 中间件.bat
└── logs/                       # 各服务运行日志（不入库）
```

---

## 八、文档索引

| 文档 | 说明 |
|---|---|
| [docs/项目技术文档.docx](docs/项目技术文档.docx) | 需求分析、项目设计、任务分工、项目实现、项目测试、项目总结 六大部分 |
| [docs/综合项目实训任务书.docx](docs/综合项目实训任务书.docx) | 课程任务书 |
| [docs/AIOps平台-验收演示步骤单页.docx](docs/AIOps平台-验收演示步骤单页.docx) | A4 单页，验收当天照着做 |
| [docs/AIOps平台-验收演示步骤单页.pdf](docs/AIOps平台-验收演示步骤单页.pdf) | 同上，PDF 版便于打印 |
| [docs/screenshots/](docs/screenshots/) | 9 张页面实际运行截图 |
| [README.md](README.md) | 本文件：架构、算法、启动、测试 |

---

## 九、仓库说明

**不含真实凭据。** 仓库中所有数据库密码写作 `${MYSQL_PASSWORD:CHANGE_ME}`，
真实值只存在于本地 `.env`（已 gitignore）。首次运行请先按「三、快速开始」第 0 步配置。

**不含他人隐私数据。** 用户表种子数据使用示例账号（`ops_duty` / `ops_db` / `ops_backend`）
与 `@example.com` 占位邮箱；截图中的账号信息已同步匿名化。

**署名说明。** 本项目为《软件工程综合实践R》SJ003303 第 11 组课程作业，
组员姓名仅作为作者署名出现在本 README 与演示单页中，不涉及账号、学号等敏感数据。

---

## 十、可选：启用 Nacos 与 RabbitMQ

两者均为**可插拔**设计：默认关闭时用线程池 + 静态 URI 也能完整运行；
开启后由注册中心寻址、由 MQ 异步削峰。切换只改 `.env`，无需改代码。

### 10.1 用 Docker 启动中间件

```powershell
docker run -d --name aiops-nacos --restart unless-stopped `
  -p 8848:8848 -p 9848:9848 `
  -e MODE=standalone `
  nacos/nacos-server:v2.4.3

docker run -d --name aiops-rabbitmq --restart unless-stopped `
  -p 5672:5672 -p 15672:15672 `
  -e RABBITMQ_DEFAULT_USER=aiops `
  -e RABBITMQ_DEFAULT_PASS=aiops123 `
  rabbitmq:3.13-management
```

- Nacos 控制台：<http://127.0.0.1:8848/nacos>（默认 nacos/nacos）
- RabbitMQ 控制台：<http://127.0.0.1:15672>（aiops / aiops123）

### 10.2 打开开关

在 `.env` 中配置：

```ini
NACOS_ENABLED=true
NACOS_ADDR=127.0.0.1:8848
NACOS_IP=127.0.0.1          # 本机装有 Radmin VPN 等虚拟网卡时必填，否则会注册到错误网卡
MQ_ENABLED=true
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USER=aiops
RABBITMQ_PASSWORD=aiops123

# 网关改为按服务名负载均衡（不配则仍走固定端口）
AUTH_URI=lb://aiops-auth-service
ALERT_URI=lb://aiops-alert-service
MONITOR_URI=lb://aiops-monitor-service
AGENT_URI=lb://aiops-agent-service
TICKET_URI=lb://aiops-ticket-service
KNOWLEDGE_URI=lb://aiops-knowledge-service
REPORT_URI=lb://aiops-report-service
DEMO_URI=lb://aiops-demo-app
```

改完重新执行 `启动.bat` 即可。

### 10.3 开启后的实际效果

**服务注册发现**：9 个服务全部注册到 Nacos，网关按服务名做负载均衡，
服务实例的端口变化不再需要改网关配置。

```
aiops-auth-service      127.0.0.1:8081  healthy=True
aiops-alert-service     127.0.0.1:8082  healthy=True
...（共 9 个）
```

**MQ 异步削峰**：告警接入不再直接调用 Agent，而是投递到
`aiops.agent.analyze.queue`，由 agent-service 的消费者拉取处理。

```
alert-service ──投递──▶ aiops.alert.exchange ──▶ aiops.agent.analyze.queue ──消费──▶ 四段式分析
                                                        └─ 失败重试 3 次 ─▶ .dlq 死信队列
```

设计要点：

| 项 | 说明 |
|---|---|
| 消息格式 | JSON（`Jackson2JsonMessageConverter`），生产/消费两端必须一致 |
| 投递可靠性 | `publisher-confirm-type: correlated`，到达交换机才确认 |
| 消费失败 | 重试 3 次后进死信队列，不会静默丢任务 |
| 降级 | MQ 不可用时自动退回直连 HTTP，告警接入主链路不受影响 |
| 消费并发 | 2~4，`prefetch=1`，避免打满 LLM 配额 |

实测一次完整链路（告警 → MQ → 分析）：

```
【投递前】入队=2 消费=2 堆积=0
【告警】count=2 eventId=9 已触发=True
【投递后】入队=3 消费=3 堆积=0     ← 消息确实经由 MQ 消费
【死信】堆积=0
```

### 10.4 关闭方式

把 `.env` 中 `NACOS_ENABLED`、`MQ_ENABLED` 改回 `false` 并重启即可，
不需要删除容器；容器可用 `docker stop aiops-nacos aiops-rabbitmq` 暂停。
