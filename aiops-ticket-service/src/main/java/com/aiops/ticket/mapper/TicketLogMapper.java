package com.aiops.ticket.mapper;

import com.aiops.ticket.entity.TicketLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单流转日志 Mapper。
 */
@Mapper
public interface TicketLogMapper extends BaseMapper<TicketLog> {
}
