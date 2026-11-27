package com.aiops.report.service.impl;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.ResultCode;
import com.aiops.common.exception.BizException;
import com.aiops.report.entity.FaultReport;
import com.aiops.report.mapper.FaultReportMapper;
import com.aiops.report.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表服务实现。
 *
 * <p>指标口径说明（避免答辩时被问住）：
 * <ul>
 *   <li><b>降噪率</b> = (原始告警数 - 聚合事件数) / 原始告警数，衡量聚合去重的效果；</li>
 *   <li><b>MTTR</b> = 工单从创建到关闭的平均时长（分钟），仅统计已关闭工单；</li>
 *   <li><b>事件数</b> 按 alert_event.first_time 归日，与告警接入时间一致。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final FaultReportMapper mapper;

    @Override
    public Map<String, Object> stats(int days) {
        int d = Math.min(Math.max(days, 1), 90);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(d);

        List<Map<String, Object>> trend = mapper.trendByDay(from, to);
        List<Map<String, Object>> category = mapper.countByCategory(from, to);
        List<Map<String, Object>> level = mapper.countByLevel(from, to);
        List<Map<String, Object>> topInstances = mapper.topInstances(from, to, 10);
        Map<String, Object> aggregation = mapper.aggregationEffect(from, to);
        List<Map<String, Object>> ticketStatus = mapper.ticketByStatus(from, to);
        Double mttrSeconds = mapper.avgMttrSeconds(from, to);

        // 补齐趋势的空白天，保证前端折线图连续
        List<Map<String, Object>> filledTrend = fillTrend(trend, d, to);

        long rawAlerts = num(aggregation == null ? null : aggregation.get("rawAlerts"));
        long events = num(aggregation == null ? null : aggregation.get("events"));
        double dedupRate = rawAlerts == 0 ? 0
                : Math.round((rawAlerts - events) * 10000.0 / rawAlerts) / 100.0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rangeDays", d);
        data.put("trend", filledTrend);
        data.put("categoryDistribution", category);
        data.put("levelDistribution", level);
        data.put("topInstances", topInstances);
        data.put("ticketStatus", ticketStatus);
        data.put("mttrMinutes", mttrSeconds == null ? 0
                : Math.round(mttrSeconds / 60.0 * 10) / 10.0);
        data.put("rawAlerts", rawAlerts);
        data.put("aggregatedEvents", events);
        data.put("dedupRate", dedupRate);
        data.put("generatedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return data;
    }

    /**
     * 补齐没有数据的日期，用 0 填充，避免前端图表断点。
     */
    private List<Map<String, Object>> fillTrend(List<Map<String, Object>> raw, int days,
                                                LocalDateTime to) {
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : raw) {
            byDay.put(String.valueOf(row.get("day")), row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = to.toLocalDate();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            Map<String, Object> row = byDay.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day.format(DAY_FMT));
            item.put("fullDay", key);
            item.put("eventCount", row == null ? 0L : num(row.get("eventCount")));
            item.put("alertCount", row == null ? 0L : num(row.get("alertCount")));
            out.add(item);
        }
        return out;
    }

    private long num(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public Map<String, Object> overview() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime todayStart = to.toLocalDate().atStartOfDay();
        LocalDateTime from = to.minusDays(1);

        Map<String, Object> agg = mapper.aggregationEffect(from, to);
        long rawAlerts = num(agg == null ? null : agg.get("rawAlerts"));
        long events = num(agg == null ? null : agg.get("events"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("events24h", events);
        data.put("rawAlerts24h", rawAlerts);
        data.put("dedupRate", rawAlerts == 0 ? 0
                : Math.round((rawAlerts - events) * 10000.0 / rawAlerts) / 100.0);
        data.put("todayStart", todayStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return data;
    }

    @Override
    public PageResult<FaultReport> pageReports(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<FaultReport> wrapper = new LambdaQueryWrapper<FaultReport>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(FaultReport::getTitle, keyword)
                        .or().like(FaultReport::getRootCause, keyword))
                .orderByDesc(FaultReport::getCreateTime);
        Page<FaultReport> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public FaultReport getReport(Long id) {
        FaultReport r = mapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报告不存在");
        }
        return r;
    }

    @Override
    public Long saveReport(FaultReport report) {
        if (report.getCreateTime() == null) {
            report.setCreateTime(LocalDateTime.now());
        }
        // 同一事件只保留最新一份报告
        if (report.getEventId() != null) {
            FaultReport existing = mapper.selectOne(new LambdaQueryWrapper<FaultReport>()
                    .eq(FaultReport::getEventId, report.getEventId())
                    .last("LIMIT 1"));
            if (existing != null) {
                report.setId(existing.getId());
                mapper.updateById(report);
                return existing.getId();
            }
        }
        mapper.insert(report);
        return report.getId();
    }

    @Override
    public FaultReport findByEventId(Long eventId) {
        if (eventId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<FaultReport>()
                .eq(FaultReport::getEventId, eventId)
                .orderByDesc(FaultReport::getId)
                .last("LIMIT 1"));
    }

    @Override
    public String exportMarkdown(Long id) {
        FaultReport r = getReport(id);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.getTitle()).append("\n\n");
        sb.append("> 生成时间：").append(r.getCreateTime()).append("\n\n");
        sb.append("## 根因结论\n").append(nvl(r.getRootCause())).append("\n\n");
        sb.append("## 证据链\n").append(nvl(r.getEvidence())).append("\n\n");
        sb.append("## 处置建议\n").append(nvl(r.getSuggestion())).append("\n\n");
        sb.append("## 报告正文\n").append(nvl(r.getReportContent())).append("\n");
        return sb.toString();
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "（暂无）" : s;
    }
}
