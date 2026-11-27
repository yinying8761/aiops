package com.aiops.alert.mapper;

import com.aiops.alert.entity.Alert;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 告警 Mapper。
 */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    /**
     * 把某指纹下尚未归属事件的告警，统一挂到新建的聚合事件上。
     */
    @Update("""
            UPDATE alert
               SET event_id = #{eventId}
             WHERE fingerprint = #{fingerprint}
               AND event_id IS NULL
            """)
    int bindEvent(@Param("fingerprint") String fingerprint, @Param("eventId") Long eventId);
}
