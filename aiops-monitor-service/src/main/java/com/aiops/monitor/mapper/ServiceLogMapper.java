package com.aiops.monitor.mapper;

import com.aiops.monitor.entity.ServiceLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服务日志 Mapper。
 */
@Mapper
public interface ServiceLogMapper extends BaseMapper<ServiceLog> {

    @Select("""
            SELECT id, level, logger, message, ts
              FROM service_log
             WHERE instance_id = #{instanceId}
               AND ts >= #{from}
             ORDER BY ts DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectRecent(@Param("instanceId") Long instanceId,
                                           @Param("from") LocalDateTime from,
                                           @Param("limit") int limit);
}
