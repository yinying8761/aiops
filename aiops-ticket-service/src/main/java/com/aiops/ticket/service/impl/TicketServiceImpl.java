package com.aiops.ticket.service.impl;

import com.aiops.common.api.PageResult;
import com.aiops.common.api.ResultCode;
import com.aiops.common.exception.BizException;
import com.aiops.common.security.UserContextHolder;
import com.aiops.ticket.domain.TicketStatus;
import com.aiops.ticket.dto.AutoTicketRequest;
import com.aiops.ticket.entity.Ticket;
import com.aiops.ticket.entity.TicketLog;
import com.aiops.ticket.mapper.TicketLogMapper;
import com.aiops.ticket.mapper.TicketMapper;
import com.aiops.ticket.service.TicketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单服务实现。
 *
 * <p>关键设计：
 * <ul>
 *   <li>工单号用「TK + 日期 + 当日序号」格式，人可读且便于检索；</li>
 *   <li>每次状态变更都写 ticket_log，形成完整处置时间线；</li>
 *   <li>派单规则按优先级自动匹配：P0/P1 派给值班负责人，P2/P3 轮转到运维工程师。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** P0/P1 由值班负责人优先响应，符合文档 1.3 的角色职责 */
    private static final List<String> URGENT_ASSIGNEES = List.of("ops_duty");
    private static final List<String> NORMAL_ASSIGNEES = List.of("ops_db", "ops_backend");

    private final TicketMapper ticketMapper;
    private final TicketLogMapper logMapper;

    /** 用于生成并发安全的当日流水号 */
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ticket autoCreate(AutoTicketRequest req) {
        if (req.getEventId() != null) {
            // 幂等：同一事件只建一张工单，避免 Agent 重试导致重复派单
            Ticket existing = findByEventId(req.getEventId());
            if (existing != null) {
                log.info("事件 {} 已存在工单 {}，跳过重复建单", req.getEventId(), existing.getTicketNo());
                return existing;
            }
        }

        Ticket ticket = new Ticket();
        ticket.setEventId(req.getEventId());
        ticket.setTitle(req.getTitle() == null ? "自动创建工单" : req.getTitle());
        ticket.setPriority(req.getPriority() == null ? "P2" : req.getPriority());
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setSuggestion(buildSuggestion(req));
        ticket.setCreateBy("agent-service");
        ticket.setCreateTime(LocalDateTime.now());
        ticket.setVersion(1);

        // 先生成单号，再据其哈希派单：这样派单轮转与单号一一对应，
        // 不会出现「用上一条工单的单号算轮转」的问题。
        ticket.setTicketNo(nextTicketNo());
        String assignee = req.getAssignee() != null && !req.getAssignee().isBlank()
                ? req.getAssignee() : pickAssignee(ticket.getPriority(), ticket.getTicketNo());
        ticket.setAssignee(assignee);

        insertTicket(ticket, "agent-service");

        log.info("自动建单成功 {} eventId={} priority={} assignee={}",
                ticket.getTicketNo(), req.getEventId(), ticket.getPriority(), assignee);
        return ticket;
    }

    /**
     * 插入工单。
     * <p>单号由 Redis 原子序列生成，正常情况下不会冲突。
     * 这里保留一次兜底：万一 Redis 数据被清空导致重号，
     * 直接抛出明确错误而不是在已标记 rollback-only 的事务里空转重试
     * （那是之前的 bug：一旦 DuplicateKeyException，事务必须回滚，
     *  后续重试必然继续失败）。
     */
    private void insertTicket(Ticket ticket, String operator) {
        try {
            ticketMapper.insert(ticket);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.error("工单号冲突 {}，可能是 Redis 序列数据被清空", ticket.getTicketNo(), e);
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    "工单号冲突，请重试一次");
        }
        writeLog(ticket.getId(), null, TicketStatus.PENDING, operator,
                "工单创建，已派发给 " + ticket.getAssignee());
    }

    /**
     * 把根因与步骤拼成结构化的处置建议文本，供运维直接照做。
     */
    private String buildSuggestion(AutoTicketRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getSummary() != null && !req.getSummary().isBlank()) {
            sb.append("【问题摘要】").append(req.getSummary()).append("\n\n");
        }
        if (req.getRootCause() != null && !req.getRootCause().isBlank()) {
            sb.append("【根因结论】").append(req.getRootCause()).append("\n\n");
        }
        if (req.getSteps() != null && !req.getSteps().isEmpty()) {
            sb.append("【处置步骤】\n");
            for (int i = 0; i < req.getSteps().size(); i++) {
                sb.append(i + 1).append(". ").append(req.getSteps().get(i)).append("\n");
            }
            sb.append("\n");
        }
        if (req.getSuggestion() != null && !req.getSuggestion().isBlank()) {
            sb.append("【整体建议】").append(req.getSuggestion()).append("\n");
        }
        return sb.toString().trim();
    }

    private String pickAssignee(String priority, String ticketNo) {
        if ("P0".equals(priority) || "P1".equals(priority)) {
            return URGENT_ASSIGNEES.get(0);
        }
        // 按工单号哈希轮转，保证同一批工单分散到不同人
        int idx = Math.abs(ticketNo == null ? 0 : ticketNo.hashCode()) % NORMAL_ASSIGNEES.size();
        return NORMAL_ASSIGNEES.get(idx);
    }

    /**
     * 生成工单号：TK + 当日日期 + 4 位当日序号。
     *
     * <p>序号取自 Redis 原子自增（key 按天分桶，当天首次使用时用数据库当日最大序号初始化）。
     * 这样并发建单不会再撞号——之前用「查数据库最大值 + 重试」的方案有两个致命问题：
     * <ol>
     *   <li>并发时两个请求读到同一个最大值，算出同一个单号；</li>
     *   <li>唯一键冲突会把当前事务标记为 rollback-only，重试时查到的最大值仍不变，
     *       导致 5 次重试全部失败。</li>
     * </ol>
     */
    private String nextTicketNo() {
        String day = LocalDateTime.now().format(NO_FMT);
        String prefix = "TK" + day;
        String seqKey = "aiops:ticket:seq:" + day;

        Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
        if (seq != null && seq == 1L) {
            // 首次使用该 key：若库中已有当日工单（例如 Redis 被清空过），
            // 用库里的最大序号做基线，避免与历史单号重复
            String max = ticketMapper.maxTicketNo(prefix);
            int base = 0;
            if (max != null && max.length() > prefix.length()) {
                try {
                    base = Integer.parseInt(max.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    base = 0;
                }
            }
            if (base > 0) {
                seq = stringRedisTemplate.opsForValue().increment(seqKey, base);
            }
            // key 保留 2 天，过期后由上面的基线逻辑接续
            stringRedisTemplate.expire(seqKey, java.time.Duration.ofDays(2));
        }

        return prefix + String.format("%04d", seq == null ? 1L : seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ticket create(Ticket ticket) {
        if (ticket.getStatus() == null) {
            ticket.setStatus(TicketStatus.PENDING);
        }
        if (ticket.getPriority() == null) {
            ticket.setPriority("P2");
        }
        ticket.setCreateTime(LocalDateTime.now());
        ticket.setCreateBy(UserContextHolder.currentUsernameOrSystem());
        ticket.setVersion(1);
        // 手动建单同样先生成单号，再派单
        if (ticket.getTicketNo() == null || ticket.getTicketNo().isBlank()) {
            ticket.setTicketNo(nextTicketNo());
        }
        if (ticket.getAssignee() == null || ticket.getAssignee().isBlank()) {
            ticket.setAssignee(pickAssignee(ticket.getPriority(), ticket.getTicketNo()));
        }
        insertTicket(ticket, UserContextHolder.currentUsernameOrSystem());
        return ticket;
    }

    @Override
    public PageResult<Ticket> page(long pageNum, long pageSize, String status,
                                   String priority, String keyword, String assignee) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(status != null && !status.isBlank(), Ticket::getStatus, status)
                .eq(priority != null && !priority.isBlank(), Ticket::getPriority, priority)
                .eq(assignee != null && !assignee.isBlank(), Ticket::getAssignee, assignee)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Ticket::getTitle, keyword)
                        .or().like(Ticket::getTicketNo, keyword))
                .orderByAsc(Ticket::getStatus)
                .orderByDesc(Ticket::getCreateTime);

        Page<Ticket> page = ticketMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public Ticket get(Long id) {
        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.TICKET_NOT_FOUND);
        }
        return t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ticket transit(Long id, String target, String operator, String remark) {
        Ticket ticket = get(id);
        String from = ticket.getStatus();
        String to = target == null ? null : target.toUpperCase();

        // 状态机校验：非法跃迁直接拒绝
        TicketStatus.assertTransit(from, to);

        Ticket update = new Ticket();
        update.setId(id);
        update.setStatus(to);
        update.setVersion(ticket.getVersion());

        if (TicketStatus.PROCESSING.equals(to) && ticket.getAcceptTime() == null) {
            update.setAcceptTime(LocalDateTime.now());
        }
        if (TicketStatus.CLOSED.equals(to)) {
            update.setCloseTime(LocalDateTime.now());
        }
        if (remark != null && !remark.isBlank()) {
            update.setSolution(remark);
        }

        int rows = ticketMapper.updateById(update);
        if (rows == 0) {
            // 乐观锁冲突：并发处置同一工单
            throw new BizException(ResultCode.TICKET_STATUS_ILLEGAL,
                    "工单已被他人修改，请刷新后重试");
        }

        String op = operator == null || operator.isBlank()
                ? UserContextHolder.currentUsernameOrSystem() : operator;
        writeLog(id, from, to, op, remark);

        log.info("工单 {} 状态流转 {} → {} by {}", ticket.getTicketNo(), from, to, op);
        return get(id);
    }

    @Override
    public List<TicketLog> logs(Long id) {
        return logMapper.selectList(new LambdaQueryWrapper<TicketLog>()
                .eq(TicketLog::getTicketId, id)
                .orderByAsc(TicketLog::getId));
    }

    @Override
    public Map<String, Object> board() {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        List<Map<String, Object>> byStatus = ticketMapper.countByStatus(from);
        List<Map<String, Object>> byPriority = ticketMapper.countByPriority(from);

        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (String s : List.of(TicketStatus.PENDING, TicketStatus.PROCESSING,
                TicketStatus.RESOLVED, TicketStatus.CLOSED)) {
            statusMap.put(s, 0L);
        }
        for (Map<String, Object> row : byStatus) {
            String s = String.valueOf(row.get("status"));
            Object c = row.get("cnt");
            statusMap.put(s, c instanceof Number n ? n.longValue() : 0L);
        }

        Map<String, Long> priorityMap = new LinkedHashMap<>();
        for (String p : List.of("P0", "P1", "P2", "P3")) {
            priorityMap.put(p, 0L);
        }
        for (Map<String, Object> row : byPriority) {
            String p = String.valueOf(row.get("priority"));
            Object c = row.get("cnt");
            priorityMap.put(p, c instanceof Number n ? n.longValue() : 0L);
        }

        Double avgSeconds = ticketMapper.avgHandleSeconds(from.minusDays(60));
        double mttrMinutes = avgSeconds == null ? 0 : Math.round(avgSeconds / 60.0 * 10) / 10.0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byStatus", statusMap.entrySet().stream()
                .map(e -> Map.of("status", e.getKey(),
                        "label", TicketStatus.label(e.getKey()), "count", e.getValue()))
                .toList());
        data.put("byPriority", priorityMap);
        data.put("mttrMinutes", mttrMinutes);
        data.put("pendingCount", statusMap.get(TicketStatus.PENDING));
        data.put("processingCount", statusMap.get(TicketStatus.PROCESSING));
        return data;
    }

    @Override
    public Ticket findByEventId(Long eventId) {
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEventId, eventId)
                .orderByDesc(Ticket::getId)
                .last("LIMIT 1"));
    }

    private void writeLog(Long ticketId, String from, String to, String operator, String remark) {
        TicketLog logRow = new TicketLog();
        logRow.setTicketId(ticketId);
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setOperator(operator);
        logRow.setRemark(remark);
        logRow.setCreateTime(LocalDateTime.now());
        logMapper.insert(logRow);
    }
}
