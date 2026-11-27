package com.aiops.ticket.mapper;

import com.aiops.ticket.entity.Ticket;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工单 Mapper。
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    /**
     * 查询指定前缀（如 TK20261125）下已存在的最大工单号，用于生成当日序号。
     * <p>不用内存计数器：服务重启后计数器归零会与库中已有单号冲突。
     */
    @Select("""
            SELECT MAX(ticket_no)
              FROM ticket
             WHERE ticket_no LIKE CONCAT(#{prefix}, '%')
            """)
    String maxTicketNo(@Param("prefix") String prefix);

    /**
     * 计算平均处理时长（分钟）：从创建到关闭。
     * <p>MTTR 的核心口径，对应文档 4.2「report-service：MTTR 计算」。
     */
    @Select("""
            SELECT AVG(TIMESTAMPDIFF(SECOND, create_time, close_time)) AS avgSeconds
              FROM ticket
             WHERE close_time IS NOT NULL
               AND create_time >= #{from}
            """)
    Double avgHandleSeconds(@Param("from") LocalDateTime from);

    /**
     * 按状态统计工单数。
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt
              FROM ticket
             WHERE create_time >= #{from}
             GROUP BY status
            """)
    List<Map<String, Object>> countByStatus(@Param("from") LocalDateTime from);

    /**
     * 按优先级统计。
     */
    @Select("""
            SELECT priority, COUNT(*) AS cnt
              FROM ticket
             WHERE create_time >= #{from}
             GROUP BY priority
            """)
    List<Map<String, Object>> countByPriority(@Param("from") LocalDateTime from);

    /**
     * 计算按时关闭率：在 SLA 时限内关闭的工单占比。
     */
    @Select("""
            SELECT
              SUM(CASE WHEN TIMESTAMPDIFF(MINUTE, create_time, close_time) <= #{slaMinutes}
                       THEN 1 ELSE 0 END) AS onTime,
              COUNT(*) AS total
              FROM ticket
             WHERE close_time IS NOT NULL
               AND create_time >= #{from}
            """)
    Map<String, Object> slaStats(@Param("from") LocalDateTime from,
                                 @Param("slaMinutes") int slaMinutes);
}
