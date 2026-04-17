package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.dto.AddActivityDTO;
import com.volunteer.activity.dto.UpdateActivityDTO;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolActivityAuditLog;
import com.volunteer.activity.mapper.VolActivityAuditLogMapper;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolProfileSimpleMapper;
import com.volunteer.activity.mapper.VolRegistrationMapper;
import com.volunteer.activity.service.ActivityService;
import com.volunteer.activity.utils.RedisCache;
import com.volunteer.activity.vo.ActivityVO;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import com.volunteer.common.utils.BeanCopyUtils;
import com.volunteer.common.vo.PageVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ActivityServiceImpl extends ServiceImpl<VolActivityMapper, VolActivity>
        implements ActivityService {

    private static final String QUOTA_KEY = "activity:quota:";

    @Autowired private VolActivityMapper  activityMapper;
    @Autowired private VolRegistrationMapper registrationMapper;
    @Autowired private VolProfileSimpleMapper profileMapper;
    @Autowired private RedisCache         redisCache;
    @Autowired private DefaultMQProducer  defaultMQProducer;
    @Autowired private VolActivityAuditLogMapper auditLogMapper;

    @Value("${python.ai.url:http://localhost:9094}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 判断是否有管理/组织权限：role=0(管理员)或2(组织者) */
    private boolean hasManageRole(String userRole) {
        int role = Integer.parseInt(userRole);
        return role == 0 || role == 2;
    }

    /** 判断是否为管理员：role=0 */
    private boolean isAdmin(String userRole) {
        return Integer.parseInt(userRole) == 0;
    }

    /** 判断组织者是否拥有该活动 */
    private boolean ownsActivity(VolActivity activity, String userId) {
        return activity.getOrganizerId() != null
                && activity.getOrganizerId().equals(Long.parseLong(userId));
    }

    @Override
    @Transactional
    public ResponseResult addActivity(AddActivityDTO dto, String userId, String userRole) {
        if (!hasManageRole(userRole)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = BeanCopyUtils.copyBean(dto, VolActivity.class);
        activity.setStatus(0);
        activity.setAuditStatus(0);  // 默认待审
        activity.setJoinedQuota(0);
        activity.setOrganizerId(Long.parseLong(userId));
        activityMapper.insert(activity);

        // 调用 Python AI 服务进行活动风控初审
        try {
            Map<String, Object> auditRequest = Map.of(
                    "activity_id", activity.getId(),
                    "title", activity.getTitle(),
                    "description", activity.getDescription() != null ? activity.getDescription() : "",
                    "organizer_name", "组织者"
            );

            String responseStr = WebClient.create(pythonAiUrl)
                    .post()
                    .uri("/ai/audit/activity")
                    .bodyValue(auditRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .onErrorReturn("{\"code\":500,\"msg\":\"AI服务不可用\"}")
                    .block();

            if (responseStr != null && responseStr.contains("\"code\":200")) {
                objectMapper.readTree(responseStr);
                log.info("AI风控审核完成 activityId={}", activity.getId());
            }

            // 解析审核结果
            Map<String, Object> auditResult = parseAuditResponse(responseStr);
            boolean passed = (boolean) auditResult.getOrDefault("passed", true);
            String riskTags = (String) auditResult.getOrDefault("riskTags", "");
            String auditReportJson = responseStr != null ? responseStr : "{}";

            // 写入审核日志
            VolActivityAuditLog auditLog = new VolActivityAuditLog();
            auditLog.setActivityId(activity.getId());
            auditLog.setAuditResult(auditReportJson);
            auditLog.setRiskTags(riskTags);
            auditLog.setPassed(passed ? 1 : 0);
            auditLogMapper.insert(auditLog);

            if (passed) {
                // 合规：审核通过，活动变为「已发布/未开始(status=0, auditStatus=1)」
                // 开放报名需组织者/管理员手动操作（status 0→1）
                activity.setAuditStatus(1);
                activity.setStatus(0);
                activityMapper.updateById(activity);
                log.info("活动AI风控通过，已发布（待开放报名） activityId={}", activity.getId());
            } else {
                // 不合规：保持待审状态
                activity.setAuditStatus(2);
                activityMapper.updateById(activity);
                String reason = (String) auditResult.getOrDefault("suggestion", "内容不合规，请修改后重新提交");
                log.info("活动AI风控驳回 activityId={} reason={}", activity.getId(), reason);
                return ResponseResult.okResult(Map.of(
                        "activityId", activity.getId(),
                        "passed", false,
                        "reason", reason,
                        "message", "您的活动内容未通过AI风控审核，请修改后重新提交"
                ));
            }
        } catch (Exception e) {
            log.warn("AI风控审核调用失败，活动进入待人工审核状态 activityId={} error={}", activity.getId(), e.getMessage());
            // AI服务不可用时，活动保持 status=0/auditStatus=0 待人工审核，不自动通过
            activity.setAuditStatus(0);
            activity.setStatus(0);
            activityMapper.updateById(activity);
            return ResponseResult.okResult(Map.of(
                    "activityId", activity.getId(),
                    "passed", false,
                    "reason", "AI风控服务暂时不可用，活动已提交，待管理员人工审核后开放报名",
                    "message", "活动已提交，AI服务暂不可用，请等待管理员审核"
            ));
        }

        return ResponseResult.okResult(Map.of("activityId", activity.getId(), "passed", true, "message", "活动发布成功"));
    }

    /**
     * 解析 AI 审核响应
     */
    private Map<String, Object> parseAuditResponse(String responseStr) {
        try {
            if (responseStr == null || responseStr.isEmpty()) {
                return Map.of("passed", true, "riskTags", "", "suggestion", "");
            }
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseStr);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            if (dataNode == null) {
                return Map.of("passed", true, "riskTags", "", "suggestion", "");
            }
            boolean passed = dataNode.has("is_compliant") ? dataNode.get("is_compliant").asBoolean() : true;
            String riskTags = "";
            if (dataNode.has("risk_reasons") && dataNode.get("risk_reasons").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (com.fasterxml.jackson.databind.JsonNode tag : dataNode.get("risk_reasons")) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(tag.asText());
                }
                riskTags = sb.toString();
            }
            String suggestion = dataNode.has("suggestion") ? dataNode.get("suggestion").asText() : "";
            return Map.of("passed", passed, "riskTags", riskTags, "suggestion", suggestion);
        } catch (Exception e) {
            log.warn("解析AI审核响应失败: {}", e.getMessage());
            return Map.of("passed", true, "riskTags", "", "suggestion", "");
        }
    }

    @Override
    @Transactional
    public ResponseResult updateActivity(Long id, UpdateActivityDTO dto, String userId, String userRole) {
        if (!hasManageRole(userRole)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(id);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (!isAdmin(userRole) && !ownsActivity(activity, userId)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        if (activity.getStatus() >= 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "进行中或已结束的活动不可编辑");
        }
        VolActivity update = BeanCopyUtils.copyBean(dto, VolActivity.class);
        update.setId(id);
        activityMapper.updateById(update);
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult deleteActivity(Long id, String userId, String userRole) {
        if (!hasManageRole(userRole)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(id);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (!isAdmin(userRole) && !ownsActivity(activity, userId)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        if (activity.getStatus() == 1 || activity.getStatus() == 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "报名中或进行中的活动不可删除");
        }
        activityMapper.deleteById(id);
        redisCache.delete(QUOTA_KEY + id);
        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getActivityById(Long id) {
        VolActivity activity = activityMapper.selectById(id);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        ActivityVO vo = toVO(activity);
        return ResponseResult.okResult(vo);
    }

    @Override
    public ResponseResult listActivities(Integer status, boolean includeAll, Integer page, Integer size) {
        Page<VolActivity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolActivity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(VolActivity::getStatus, status);
        }
        // 非管理员全量模式：只展示已通过审核（auditStatus=1）的活动，屏蔽待审核/驳回
        if (!includeAll) {
            wrapper.eq(VolActivity::getAuditStatus, 1);
        }
        wrapper.orderByDesc(VolActivity::getCreateTime);
        Page<VolActivity> result = activityMapper.selectPage(pageParam, wrapper);

        List<ActivityVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return ResponseResult.okResult(new com.volunteer.common.vo.PageVo(voList, result.getTotal()));
    }

    @Override
    @Transactional
    public ResponseResult updateStatus(Long id, Integer newStatus, String userId, String userRole) {
        if (!hasManageRole(userRole)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(id);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (!isAdmin(userRole) && !ownsActivity(activity, userId)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        // 状态只能单向流转
        if (newStatus <= activity.getStatus()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "状态只能向后流转");
        }
        // 未审核通过的活动不能推进状态（需先通过 /approve 接口进行人工审核）
        if (activity.getAuditStatus() == null || activity.getAuditStatus() != 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动尚未通过审核，无法推进状态");
        }
        activity.setStatus(newStatus);
        activityMapper.updateById(activity);

        // 开放报名时：将名额写入 Redis，供高并发扣减
        if (newStatus == 1) {
            int remain = activity.getTotalQuota() - activity.getJoinedQuota();
            redisCache.set(QUOTA_KEY + id, String.valueOf(remain));
        }
        // 活动结束时：清除 Redis 名额 key，标记缺席，并通知积分服务发放完成奖励
        if (newStatus == 3) {
            redisCache.delete(QUOTA_KEY + id);
            // 将已报名未签到的记录标记为缺席
            registrationMapper.markAbsent(id);

            // 累加已签到志愿者的服务时长
            List<Long> checkedInUserIds = registrationMapper.selectCheckedInUserIds(id);
            if (!checkedInUserIds.isEmpty() && activity.getStartTime() != null && activity.getEndTime() != null) {
                long hours = ChronoUnit.HOURS.between(activity.getStartTime(), activity.getEndTime());
                int hoursToAdd = (int) Math.max(1, hours);
                profileMapper.batchAddHours(checkedInUserIds, hoursToAdd);
                log.info("服务时长已累加 activityId={} hours={} users={}", id, hoursToAdd, checkedInUserIds.size());
            }

            try {
                String payload = objectMapper.writeValueAsString(Map.of("activityId", id));
                defaultMQProducer.send(new Message("activity-complete-topic", payload.getBytes()));
                log.info("已发送活动完成消息 activityId={}", id);
            } catch (Exception e) {
                log.warn("活动完成 MQ 发送失败 activityId={}", id);
            }
        }
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult approveActivity(Long id, String userId, String userRole) {
        if (!isAdmin(userRole)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(id);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getAuditStatus() != null && activity.getAuditStatus() == 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动已通过审核，无需重复操作");
        }
        activity.setAuditStatus(1);  // 仅通过审核，status 保持不变（组织者再手动开放报名）
        activityMapper.updateById(activity);
        log.info("管理员人工审核通过活动 activityId={}", id);
        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getMyActivities(String userId, Integer page, Integer size) {
        Page<VolActivity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolActivity::getOrganizerId, Long.parseLong(userId));
        wrapper.orderByDesc(VolActivity::getCreateTime);
        Page<VolActivity> result = activityMapper.selectPage(pageParam, wrapper);

        List<ActivityVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return ResponseResult.okResult(new com.volunteer.common.vo.PageVo(voList, result.getTotal()));
    }

    /** 组装 VO，从 Redis 读取实时剩余名额，并查询组织者昵称 */
    private ActivityVO toVO(VolActivity activity) {
        ActivityVO vo = BeanCopyUtils.copyBean(activity, ActivityVO.class);
        String remainStr = redisCache.get(QUOTA_KEY + activity.getId());
        if (remainStr != null) {
            vo.setRemainQuota(Integer.parseInt(remainStr));
        } else {
            vo.setRemainQuota(activity.getTotalQuota() - activity.getJoinedQuota());
        }
        if (activity.getOrganizerId() != null) {
            String nickname = activityMapper.selectNicknameById(activity.getOrganizerId());
            vo.setOrganizerName(nickname);
        }
        return vo;
    }

    @Override
    public ResponseResult listAuditLogs(Integer passed, Integer page, Integer size) {
        Page<VolActivityAuditLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolActivityAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (passed != null) {
            wrapper.eq(VolActivityAuditLog::getPassed, passed);
        }
        wrapper.orderByDesc(VolActivityAuditLog::getCreateTime);
        Page<VolActivityAuditLog> result = auditLogMapper.selectPage(pageParam, wrapper);

        // 填充活动标题
        List<Long> activityIds = result.getRecords().stream()
                .map(VolActivityAuditLog::getActivityId)
                .collect(Collectors.toList());
        Map<Long, String> titleMap = activityIds.isEmpty() ? Map.of() :
                activityMapper.selectBatchIds(activityIds).stream()
                        .collect(Collectors.toMap(VolActivity::getId, VolActivity::getTitle));

        List<Map<String, Object>> voList = result.getRecords().stream().map(log -> {
            java.util.HashMap<String, Object> m = new java.util.HashMap<>();
            m.put("id", log.getId());
            m.put("activityId", log.getActivityId());
            m.put("activityTitle", titleMap.getOrDefault(log.getActivityId(), "活动" + log.getActivityId()));
            m.put("auditResult", log.getAuditResult() != null ? log.getAuditResult() : "");
            m.put("riskTags", log.getRiskTags() != null ? log.getRiskTags() : "");
            m.put("passed", log.getPassed());
            m.put("createTime", log.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }
}
