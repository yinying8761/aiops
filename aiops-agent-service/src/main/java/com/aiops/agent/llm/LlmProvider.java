package com.aiops.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 大模型调用抽象。
 *
 * <p>设计意图（对应文档 2.2「大模型：DeepSeek API」与 4.4「DeepSeek API 调用失败」的容错）：
 * 把「模型能力」与「Agent 编排」解耦，使编排逻辑不依赖具体供应商。
 * 当前有两个实现：
 * <ul>
 *   <li>{@code MockLlmProvider}：无 Key 时的规则+模板实现，接口契约与真实调用一致；</li>
 *   <li>{@code DeepSeekLlmProvider}：基于 Spring AI 的真实调用，配置 Key 后一行切换。</li>
 * </ul>
 */
public interface LlmProvider {

    /** 实现标识，用于日志与前端展示当前是 Mock 还是真实模型 */
    String name();

    /**
     * 单轮补全。
     *
     * @param systemPrompt 系统提示词（角色设定与输出格式约束）
     * @param userPrompt   用户提示词（本次任务的具体输入）
     * @param jsonMode     是否要求输出 JSON 对象
     */
    String complete(String systemPrompt, String userPrompt, boolean jsonMode);

    /**
     * 带工具调用的补全。
     *
     * <p>对应文档 2.5 算法（3）的 ReAct 循环：模型决定调用哪些工具、传什么参数，
     * 由编排层执行工具后把结果回灌，模型继续推理直至输出最终结论。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param tools        可调用工具描述
     * @return 模型本轮的决定（可能是调用工具，也可能是最终答案）
     */
    LlmToolDecision decide(String systemPrompt, String userPrompt, List<Map<String, Object>> tools);
}
