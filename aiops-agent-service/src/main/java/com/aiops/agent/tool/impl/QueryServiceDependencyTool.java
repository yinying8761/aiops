package com.aiops.agent.tool.impl;

import com.aiops.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具三：查询服务依赖拓扑。
 * <p>对应文档 2.5「query_service_dependency(instance_id)：查询服务依赖拓扑」。
 * 用于判断故障是「自身问题」还是「上游/下游传导」，这是根因分析的关键一步。
 *
 * <p>当前依赖关系来自静态拓扑表（课程环境无 APM 探针）；生产环境可替换为
 * SkyWalking / Pinpoint 的依赖数据源，接口契约不变。
 */
@Slf4j
@Component
public class QueryServiceDependencyTool implements AgentTool {

    /** 静态拓扑：instanceId -> 依赖描述 */
    private static final Map<Long, Map<String, Object>> TOPOLOGY = Map.of(
            1L, topology("demo-order-service", "订单核心服务",
                    List.of("demo-payment-service", "MySQL", "Redis", "RabbitMQ"),
                    List.of("aiops-gateway"),
                    "订单服务依赖支付服务与数据库；支付或数据库异常会直接传导为订单服务告警"),
            2L, topology("aiops-auth-service", "认证服务",
                    List.of("MySQL", "Redis"),
                    List.of("aiops-gateway"),
                    "认证服务是所有接口的前置依赖，异常会影响全站登录"),
            3L, topology("aiops-alert-service", "告警接入服务",
                    List.of("MySQL", "Redis", "RabbitMQ"),
                    List.of("aiops-gateway", "aiops-agent-service"),
                    "告警接入异常会导致告警丢失，进而使分析链路空转"),
            4L, topology("aiops-report-service", "报表服务",
                    List.of("MySQL", "aiops-alert-service"),
                    List.of("aiops-gateway"),
                    "报表服务依赖告警服务的数据，自身异常不影响主链路"),
            5L, topology("demo-payment-service", "支付服务",
                    List.of("MySQL", "第三方支付网关"),
                    List.of("demo-order-service"),
                    "支付服务异常会通过订单服务放大为订单失败率上升")
    );

    @Override
    public String name() {
        return "query_service_dependency";
    }

    @Override
    public String description() {
        return "查询指定服务实例的上下游依赖拓扑与依赖说明。"
                + "用于判断故障根因在被监控服务自身，还是由上游调用方或下游依赖（数据库/缓存/MQ/第三方）传导而来。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("instance_id", Map.of(
                "type", "integer",
                "description", "服务实例ID"));
        return Map.of("type", "object", "properties", props,
                "required", List.of("instance_id"));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Long instanceId = AgentTool.argLong(args, "instance_id", null);
        if (instanceId == null) {
            return Map.of("success", false, "error", "缺少 instance_id 参数");
        }
        Map<String, Object> topo = TOPOLOGY.get(instanceId);
        if (topo == null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("instance_id", instanceId);
            out.put("message", "该实例未登记依赖拓扑，按独立服务处理");
            out.put("downstream", List.of());
            out.put("upstream", List.of());
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>(topo);
        out.put("success", true);
        out.put("instance_id", instanceId);
        return out;
    }

    private static Map<String, Object> topology(String serviceName, String role,
                                                List<String> downstream, List<String> upstream,
                                                String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service_name", serviceName);
        m.put("role", role);
        m.put("downstream", downstream);
        m.put("upstream", upstream);
        m.put("note", note);
        return m;
    }
}
