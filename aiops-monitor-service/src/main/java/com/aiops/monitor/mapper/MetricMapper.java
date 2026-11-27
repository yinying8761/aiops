package com.aiops.monitor.mapper;

import com.aiops.monitor.entity.Metric;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标 Mapper。
 */
@Mapper
public interface MetricMapper extends BaseMapper<Metric> {

    /**
     * 查询指标时序。
     */
    @Select("""
            SELECT ts, value
              FROM metric
             WHERE instance_id = #{instanceId}
               AND metric_name = #{metricName}
               AND ts >= #{from}
             ORDER BY ts
            """)
    List<Map<String, Object>> selectSeries(@Param("instanceId") Long instanceId,
                                           @Param("metricName") String metricName,
                                           @Param("from") LocalDateTime from);

    /**
     * 查询实例最新一批指标快照：每个 metric_name 取最后一条。
     */
    @Select("""
            SELECT m.metric_name, m.value, m.unit, m.ts
              FROM metric m
              JOIN (
                    SELECT metric_name, MAX(ts) AS max_ts
                      FROM metric
                     WHERE instance_id = #{instanceId}
                     GROUP BY metric_name
                   ) t
                ON t.metric_name = m.metric_name AND t.max_ts = m.ts
             WHERE m.instance_id = #{instanceId}
            """)
    List<Map<String, Object>> selectLatest(@Param("instanceId") Long instanceId);

    /**
     * 清理过期指标（定时任务用），保留最近 N 天，防止表无限膨胀。
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM metric WHERE ts < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
