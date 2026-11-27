package com.aiops.alert.mapper;

import com.aiops.alert.entity.AlertEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聚合告警事件 Mapper。
 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    /**
     * 原子地累加计数并刷新末次时间。
     * 并发场景下由数据库保证 count 不丢更新，替代「读-改-写」。
     */
    @Update("""
            UPDATE alert_event
               SET count = count + 1,
                   last_time = #{lastTime},
                   update_time = NOW()
             WHERE id = #{eventId}
            """)
    int increaseCount(@Param("eventId") Long eventId, @Param("lastTime") LocalDateTime lastTime);

    /**
     * 按级别统计各状态的告警事件数量（报表用）。
     */
    @Select("""
            SELECT level, COUNT(*) AS cnt
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY level
            """)
    List<Map<String, Object>> countByLevel(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 按分类统计（报表用）。
     */
    @Select("""
            SELECT IFNULL(category, 'OTHER') AS category, COUNT(*) AS cnt
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY IFNULL(category, 'OTHER')
            """)
    List<Map<String, Object>> countByCategory(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * 按天统计告警事件趋势（报表用）。
     */
    @Select("""
            SELECT DATE_FORMAT(first_time, '%Y-%m-%d') AS day, COUNT(*) AS cnt
              FROM alert_event
             WHERE first_time >= #{from} AND first_time < #{to}
             GROUP BY DATE_FORMAT(first_time, '%Y-%m-%d')
             ORDER BY day
            """)
    List<Map<String, Object>> countByDay(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /**
     * 待分析事件（补偿扫描用）。
     */
    @Select("""
            SELECT * FROM alert_event
             WHERE analyze_status = 'PENDING'
             ORDER BY id
             LIMIT #{limit}
            """)
    List<AlertEvent> selectPending(@Param("limit") int limit);

    /**
     * 更新分析状态。
     */
    @Update("""
            UPDATE alert_event
               SET analyze_status = #{status}, update_time = NOW()
             WHERE id = #{eventId}
            """)
    int updateAnalyzeStatus(@Param("eventId") Long eventId, @Param("status") String status);
}
