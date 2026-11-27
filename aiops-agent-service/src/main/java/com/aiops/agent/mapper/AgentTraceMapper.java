package com.aiops.agent.mapper;

import com.aiops.agent.entity.AgentTrace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 过程留痕 Mapper。
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {
}
