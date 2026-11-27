package com.aiops.alert.service.impl;

import com.aiops.alert.dto.AlertEventView;
import com.aiops.alert.dto.AlertIngestRequest;
import com.aiops.alert.dto.AlertIngestResult;
import com.aiops.alert.entity.Alert;
import com.aiops.alert.entity.AlertEvent;
import com.aiops.alert.mapper.AlertEventMapper;
import com.aiops.alert.mapper.AlertMapper;
import com.aiops.alert.service.AlertService;
import com.aiops.alert.service.LevelResolver;
import com.aiops.common.api.PageResult;
import com.aiops.common.api.ResultCode;
import com.aiops.common.constant.CommonConstants;
import com.aiops.common.exception.BizException;
import com.aiops.common.util.FingerprintUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 告警接入与聚合服务实现。
 *
 * <h3>核心算法（对应文档 2.5 算法（1））</h3>
 * <pre>
 * 输入：原始告警 a（instance_id, alert_type, content, ts）
 * 1. 归一化字段：service = instance_id, type = alert_type
 * 2. 计算指纹：fingerprint = MD5(service + "|" + type + "|" + 时间窗口(ts, 5min))
 * 3. Redis 原子计数：INCR(alert:fp:{fingerprint})，并记录首末时间
 * 4. 若计数由 1 → 2，则新建聚合事件 alert_event 并触发 Agent 分析
 * 5. 若计数继续增加，则仅更新事件的 count 与 last_time，避免重复分析
 * </pre>
 * 通过该算法，一条持续刷新的同类告警只会触发一次分析流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final DateTimeFormatter TITLE_TIME_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AlertMapper alertMapper;
    private final AlertEventMapper eventMapper;
    private final StringRedisTemplate redis;
    private final LevelResolver levelResolver;
    private final AlertEventPublisher eventPublisher;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public AlertIngestResult ingest(AlertIngestRequest request) {
        LocalDateTime ts = request.getTs() == null ? LocalDateTime.now() : request.getTs();
        long tsMillis = ts.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        // ---- 步骤 1&2：归一化 + 指纹 ----
        String alertType = FingerprintUtils.normalize(request.getAlertType());
        String fingerprint = FingerprintUtils.fingerprint(request.getInstanceId(), alertType, tsMillis);

        // ---- 步骤 3：Redis 原子计数 ----
        String countKey = CommonConstants.REDIS_ALERT_FP + fingerprint;
        Long count = redis.opsForValue().increment(countKey);
        // 首次计数时设置窗口过期时间，窗口结束自动清理，避免 Key 无限增长
        if (count != null && count == 1L) {
            redis.expire(countKey,
                    CommonConstants.ALERT_AGG_WINDOW_MINUTES + 1, TimeUnit.MINUTES);
        }

        // 分级：优先用上报的级别，否则按规则推导
        String level = levelResolver.resolve(request.getAlertType(), request.getLevel(),
                request.getMetricValue());

        // 落库原始告警
        Alert alert = new Alert();
        alert.setInstanceId(request.getInstanceId());
        alert.setAlertType(alertType);
        alert.setLevel(level);
        alert.setTitle(request.getTitle() == null || request.getTitle().isBlank()
                ? buildTitle(request.getInstanceId(), alertType, ts)
                : request.getTitle());
        alert.setContent(request.getContent());
        alert.setFingerprint(fingerprint);
        alert.setStatus("NEW");
        alert.setCreateTime(LocalDateTime.now());
        alertMapper.insert(alert);

        AlertIngestResult result = new AlertIngestResult();
        result.setAlertId(alert.getId());
        result.setFingerprint(fingerprint);
        result.setCount(count == null ? 1L : count);
        result.setLevel(level);
        result.setEventCreated(false);
        result.setAnalysisTriggered(false);

        // ---- 步骤 4：计数 1 → 2，新建聚合事件并触发分析 ----
        if (result.getCount() == CommonConstants.ALERT_TRIGGER_COUNT) {
            EventCreation creation = createEventAndTrigger(request, alertType, fingerprint, level, ts);
            result.setEventId(creation.event().getId());
            result.setEventCreated(creation.created());
            result.setAnalysisTriggered(creation.created());
            result.setMessage(creation.created()
                    ? "已达触发阈值，已创建聚合事件并投递 Agent 分析"
                    : "已达触发阈值，关联到既有聚合事件并累加计数");
        } else if (result.getCount() > CommonConstants.ALERT_TRIGGER_COUNT) {
            // ---- 步骤 5：继续增加，仅累加 count 与 last_time ----
            Long eventId = resolveEventId(fingerprint);
            // 关键：必须检查 UPDATE 的影响行数。
            // Redis 里的指纹→事件映射可能指向一条已被删除的事件（清库后 Redis 未清），
            // 此时 increaseCount 影响 0 行，若不检查就会误报「已聚合」却什么都没做，
            // 导致该窗内的告警永久丢失分析机会。
            boolean updated = eventId != null && eventMapper.increaseCount(eventId, ts) > 0;

            if (updated) {
                alertMapper.bindEvent(fingerprint, eventId);
                result.setEventId(eventId);
                result.setMessage("已聚合并累加计数，不再重复触发分析");
            } else {
                // 事件不存在（或已被清理）：重建聚合事件并重新触发分析
                if (eventId != null) {
                    log.warn("指纹 {} 映射到的事件 {} 已不存在，尝试重建聚合事件", fingerprint, eventId);
                }
                EventCreation creation = createEventAndTrigger(request, alertType, fingerprint, level, ts);
                result.setEventId(creation.event().getId());
                result.setEventCreated(creation.created());
                result.setAnalysisTriggered(creation.created());
                result.setMessage(creation.created()
                        ? "事件缺失，已重建聚合事件并投递分析"
                        : "已关联到既有聚合事件，继续累加计数");
            }
        } else {
            result.setMessage("首次告警，等待同指纹告警累积以达到分析阈值");
        }

        log.info("告警接入 instanceId={} type={} fp={} count={} level={} eventCreated={}",
                request.getInstanceId(), alertType, fingerprint, result.getCount(),
                level, Boolean.TRUE.equals(result.getEventCreated()));

        return result;
    }

    /**
     * 事件创建结果：区分「真的新建了」与「复用了既有事件」。
     * 只有新建时才应触发一次 Agent 分析。
     */
    private record EventCreation(AlertEvent event, boolean created) {
    }

    /**
     * 创建聚合事件 + 触发分析。
     *
     * <p>注意：本方法由 {@link #ingest} 内部调用，方法上的 {@code @Transactional}
     * 不会生效（Spring AOP 只拦截外部调用）。事务边界统一放在 {@code ingest} 上，
     * 保证「写原始告警 + 建事件 + 绑定告警」原子完成。
     */
    protected EventCreation createEventAndTrigger(AlertIngestRequest request, String alertType,
                                                  String fingerprint, String level, LocalDateTime ts) {
        // 幂等保护：同一指纹在库中已存在事件时复用，不重复建事件。
        // 典型场景：Redis 被清空但库中事件还在，此时计数会重新从 1 开始。
        AlertEvent existing = eventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getFingerprint, fingerprint)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("指纹 {} 已存在事件 {}，复用并累加计数", fingerprint, existing.getId());
            eventMapper.increaseCount(existing.getId(), ts);
            alertMapper.bindEvent(fingerprint, existing.getId());
            redis.opsForValue().set(CommonConstants.REDIS_ALERT_FP_EVENT + fingerprint,
                    String.valueOf(existing.getId()),
                    CommonConstants.ALERT_AGG_WINDOW_MINUTES + 1, TimeUnit.MINUTES);
            return new EventCreation(existing, false);
        }

        AlertEvent event = new AlertEvent();
        event.setFingerprint(fingerprint);
        event.setInstanceId(request.getInstanceId());
        event.setAlertType(alertType);
        event.setLevel(level);
        event.setTitle(buildTitle(request.getInstanceId(), alertType, ts));
        event.setSummary(truncate(request.getContent(), 900));
        event.setCount((int) (long) CommonConstants.ALERT_TRIGGER_COUNT);
        event.setStatus("NEW");
        event.setAnalyzeStatus("PENDING");
        event.setFirstTime(ts);
        event.setLastTime(ts);
        eventMapper.insert(event);

        // 把该指纹下已入库的原始告警挂到事件上
        alertMapper.bindEvent(fingerprint, event.getId());

        // 缓存 指纹 → 事件ID，供后续累加计数时快速定位
        redis.opsForValue().set(CommonConstants.REDIS_ALERT_FP_EVENT + fingerprint,
                String.valueOf(event.getId()),
                CommonConstants.ALERT_AGG_WINDOW_MINUTES + 1, TimeUnit.MINUTES);

        // 投递 Agent 分析（MQ 或本地异步，由 profile 决定实现）
        eventPublisher.publishAnalyze(event);

        return new EventCreation(event, true);
    }

    private Long resolveEventId(String fingerprint) {
        String cached = redis.opsForValue().get(CommonConstants.REDIS_ALERT_FP_EVENT + fingerprint);
        if (cached != null) {
            try {
                return Long.valueOf(cached);
            } catch (NumberFormatException ignored) {
                // 缓存脏数据，走数据库回查
            }
        }
        AlertEvent event = eventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getFingerprint, fingerprint)
                .last("LIMIT 1"));
        if (event == null) {
            return null;
        }
        redis.opsForValue().set(CommonConstants.REDIS_ALERT_FP_EVENT + fingerprint,
                String.valueOf(event.getId()),
                CommonConstants.ALERT_AGG_WINDOW_MINUTES + 1, TimeUnit.MINUTES);
        return event.getId();
    }

    @Override
    public Map<String, Object> ingestBatch(List<AlertIngestRequest> requests) {
        long start = System.currentTimeMillis();
        int total = requests.size();
        int created = 0;
        int merged = 0;
        List<Long> eventIds = new ArrayList<>();

        for (AlertIngestRequest req : requests) {
            AlertIngestResult r = ingest(req);
            if (Boolean.TRUE.equals(r.getEventCreated())) {
                created++;
                eventIds.add(r.getEventId());
            } else if (r.getEventId() != null) {
                merged++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("eventCreated", created);
        stats.put("aggregated", total - created);
        stats.put("mergedCount", merged);
        stats.put("eventIds", eventIds);
        stats.put("dedupRate", total == 0 ? 0
                : Math.round((total - created) * 10000.0 / total) / 100.0);
        stats.put("costMs", System.currentTimeMillis() - start);
        log.info("批量接入完成 total={} created={} cost={}ms", total, created,
                System.currentTimeMillis() - start);
        return stats;
    }

    @Override
    public PageResult<AlertEventView> pageEvents(long pageNum, long pageSize, String level,
                                                  String status, String keyword,
                                                  LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .eq(level != null && !level.isBlank(), AlertEvent::getLevel, level)
                .eq(status != null && !status.isBlank(), AlertEvent::getStatus, status)
                .ge(from != null, AlertEvent::getFirstTime, from)
                .lt(to != null, AlertEvent::getFirstTime, to)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(AlertEvent::getTitle, keyword)
                        .or().like(AlertEvent::getSummary, keyword)
                        .or().like(AlertEvent::getAlertType, keyword))
                // 高优先级在前，同级则新事件在前
                .orderByAsc(AlertEvent::getLevel)
                .orderByDesc(AlertEvent::getLastTime);

        Page<AlertEvent> page = eventMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AlertEventView> records = page.getRecords().stream()
                .map(this::toView)
                .toList();
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AlertEventView getEvent(Long id) {
        AlertEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ResultCode.ALERT_EVENT_NOT_FOUND);
        }
        return toView(event);
    }

    @Override
    public List<Map<String, Object>> listAlertsOfEvent(Long eventId) {
        List<Alert> alerts = alertMapper.selectList(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getEventId, eventId)
                .orderByDesc(Alert::getCreateTime)
                .last("LIMIT 200"));
        return alerts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("alertType", a.getAlertType());
            m.put("level", a.getLevel());
            m.put("title", a.getTitle());
            m.put("content", a.getContent());
            m.put("status", a.getStatus());
            m.put("createTime", a.getCreateTime());
            return m;
        }).toList();
    }

    @Override
    public void updateEventStatus(Long eventId, String status) {
        AlertEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.ALERT_EVENT_NOT_FOUND);
        }
        AlertEvent update = new AlertEvent();
        update.setId(eventId);
        update.setStatus(status);
        eventMapper.updateById(update);
    }

    @Override
    public void updateAnalysis(Long eventId, String category, String level,
                               String summary, String analyzeStatus) {
        AlertEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.ALERT_EVENT_NOT_FOUND);
        }
        AlertEvent update = new AlertEvent();
        update.setId(eventId);
        // 仅更新非空字段，避免把已有值覆盖为 null
        if (category != null && !category.isBlank()) {
            update.setCategory(category);
        }
        if (level != null && !level.isBlank()) {
            update.setLevel(level);
        }
        if (summary != null && !summary.isBlank()) {
            update.setSummary(truncate(summary, 900));
        }
        if (analyzeStatus != null && !analyzeStatus.isBlank()) {
            update.setAnalyzeStatus(analyzeStatus);
        }
        eventMapper.updateById(update);
        log.info("事件 {} 分析结论已回写：category={} level={} analyzeStatus={}",
                eventId, category, level, analyzeStatus);
    }

    @Override
    public Map<String, Object> dashboard() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long todayEvents = count(eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getFirstTime, todayStart)
                .lt(AlertEvent::getFirstTime, tomorrowStart)));

        long todayAlerts = count(alertMapper.selectCount(new LambdaQueryWrapper<Alert>()
                .ge(Alert::getCreateTime, todayStart)
                .lt(Alert::getCreateTime, tomorrowStart)));

        long pending = count(eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getStatus, "NEW", "ACK", "HANDLING")));

        // 各级别未关闭事件数
        Map<String, Long> levelDist = new LinkedHashMap<>();
        for (String lv : List.of("P0", "P1", "P2", "P3")) {
            levelDist.put(lv, count(eventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getLevel, lv)
                    .notIn(AlertEvent::getStatus, "RESOLVED", "CLOSED", "IGNORED"))));
        }

        // 近 24 小时按小时趋势：以「当前小时」为终点向前回推 24 个整点
        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        List<AlertEvent> recent = eventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getFirstTime, dayAgo)
                .orderByAsc(AlertEvent::getFirstTime));
        Map<String, Long> hourly = new LinkedHashMap<>();
        LocalDateTime hourCursor = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        for (int i = 23; i >= 0; i--) {
            String key = hourCursor.minusHours(i)
                    .format(DateTimeFormatter.ofPattern("HH:00"));
            hourly.put(key, 0L);
        }
        for (AlertEvent e : recent) {
            if (e.getFirstTime() == null) {
                continue;
            }
            String key = e.getFirstTime()
                    .withMinute(0).withSecond(0).withNano(0)
                    .format(DateTimeFormatter.ofPattern("HH:00"));
            hourly.computeIfPresent(key, (k, v) -> v + 1);
        }

        long aggregatedTotal = recent.stream().mapToLong(e -> e.getCount() == null ? 0 : e.getCount()).sum();
        long eventTotal = recent.size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayEvents", todayEvents);
        data.put("todayAlerts", todayAlerts);
        data.put("pendingEvents", pending);
        data.put("levelDistribution", levelDist);
        data.put("hourlyTrend", hourly.entrySet().stream()
                .map(e -> Map.of("time", e.getKey(), "count", e.getValue()))
                .toList());
        data.put("dedupRate", aggregatedTotal == 0 ? 0
                : Math.round((aggregatedTotal - eventTotal) * 10000.0 / aggregatedTotal) / 100.0);
        data.put("aggregatedAlerts24h", aggregatedTotal);
        data.put("events24h", eventTotal);
        data.put("recentEvents", recent.stream()
                .sorted((a, b) -> b.getLastTime().compareTo(a.getLastTime()))
                .limit(10).map(this::toView).toList());
        return data;
    }

    @Override
    public int rescanPending(int limit) {
        List<AlertEvent> pendingList = eventMapper.selectPending(limit);
        for (AlertEvent e : pendingList) {
            eventPublisher.publishAnalyze(e);
        }
        log.info("补偿扫描投递 {} 个待分析事件", pendingList.size());
        return pendingList.size();
    }

    private long count(Long n) {
        return n == null ? 0L : n;
    }

    private AlertEventView toView(AlertEvent e) {
        AlertEventView v = new AlertEventView();
        v.setId(e.getId());
        v.setFingerprint(e.getFingerprint());
        v.setInstanceId(e.getInstanceId());
        v.setAlertType(e.getAlertType());
        v.setCategory(e.getCategory());
        v.setLevel(e.getLevel());
        v.setLevelLabel(switch (e.getLevel() == null ? "P2" : e.getLevel()) {
            case "P0" -> "紧急";
            case "P1" -> "严重";
            case "P2" -> "一般";
            default -> "提示";
        });
        v.setTitle(e.getTitle());
        v.setSummary(e.getSummary());
        v.setCount(e.getCount());
        v.setStatus(e.getStatus());
        v.setAnalyzeStatus(e.getAnalyzeStatus());
        v.setFirstTime(e.getFirstTime());
        v.setLastTime(e.getLastTime());
        if (e.getFirstTime() != null && e.getLastTime() != null) {
            v.setDurationSeconds(Duration.between(e.getFirstTime(), e.getLastTime()).getSeconds());
        }
        return v;
    }

    private String buildTitle(Long instanceId, String alertType, LocalDateTime ts) {
        return String.format("%s 告警 @实例%d (%s)",
                alertType.toUpperCase(), instanceId, ts.format(TITLE_TIME_FMT));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
