package com.aiops.report.mapper;

import com.aiops.report.entity.FaultReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 故障报告 Mapper。跨库查询告警与工单表，用于统计报表聚合。
 */
@Mapper
public interface FaultReportMapper extends BaseMapper<FaultReport> {

    /**
     * 告警事件按天趋势。
     */
    @Select("""
            SELECT DATE_FORMAT(first_time, '%Y-%m-%d') AS day,
                   COUNT(*)                              AS eventCount,
                   SUM(count)                            AS alertCount
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY DATE_FORMAT(first_time, '%Y-%m-%d')
             ORDER BY day
            """)
    List<Map<String, Object>> trendByDay(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /**
     * 按分类统计事件数。
     */
    @Select("""
            SELECT IFNULL(category, 'OTHER') AS category, COUNT(*) AS cnt
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY IFNULL(category, 'OTHER')
             ORDER BY cnt DESC
            """)
    List<Map<String, Object>> countByCategory(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * 按级别统计。
     */
    @Select("""
            SELECT level, COUNT(*) AS cnt
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY level
             ORDER BY level
            """)
    List<Map<String, Object>> countByLevel(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 按服务实例统计 Top N。
     */
    @Select("""
            SELECT e.instance_id AS instanceId,
                   IFNULL(s.service_name, CONCAT('实例-', e.instance_id)) AS serviceName,
                   COUNT(*) AS eventCount
              FROM alert_event e
              LEFT JOIN service_instance s ON s.id = e.instance_id
             WHERE e.first_time >= #{from} AND e.first_time < #{to}
             GROUP BY e.instance_id, s.service_name
             ORDER BY eventCount DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> topInstances(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("limit") int limit);

    /**
     * MTTR：工单平均处理时长（秒）。
     */
    @Select("""
            SELECT AVG(TIMESTAMPDIFF(SECOND, create_time, close_time))
              FROM ticket
             WHERE close_time IS NOT NULL
               AND create_time >= #{from} AND create_time < #{to}
            """)
    Double avgMttrSeconds(@Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /**
     * 告警聚合效果：原始告警总数 vs 聚合事件总数，用于量化「降噪率」。
     */
    @Select("""
            SELECT
              (SELECT IFNULL(SUM(count), 0) FROM alert_event
                WHERE first_time >= #{from} AND first_time < #{to})   AS rawAlerts,
              (SELECT COUNT(*) FROM alert_event
                WHERE first_time >= #{from} AND first_time < #{to})   AS events
            """)
    Map<String, Object> aggregationEffect(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * 工单按状态分布。
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt
              FROM ticket
             WHERE create_time >= #{from} AND create_time < #{to}
             GROUP BY status
            """)
    List<Map<String, Object>> ticketByStatus(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);
}
