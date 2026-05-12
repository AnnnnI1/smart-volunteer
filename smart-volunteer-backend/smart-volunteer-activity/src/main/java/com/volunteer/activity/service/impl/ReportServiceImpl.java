package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.dto.ReportProcessDTO;
import com.volunteer.activity.dto.ReportSubmitDTO;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolReport;
import com.volunteer.activity.entity.VolReportAppeal;
import com.volunteer.activity.entity.VolPenaltyRecord;
import com.volunteer.activity.entity.VolUserCreditScore;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolOrganizerProfileMapper;
import com.volunteer.activity.mapper.VolPenaltyRecordMapper;
import com.volunteer.activity.mapper.VolReportAppealMapper;
import com.volunteer.activity.mapper.VolReportMapper;
import com.volunteer.activity.mapper.VolUserCreditScoreMapper;
import com.volunteer.activity.service.*;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import com.volunteer.common.vo.PageVo;
import com.volunteer.user.entity.User;
import com.volunteer.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl extends ServiceImpl<VolReportMapper, VolReport>
        implements ReportService {

    @Autowired private VolReportMapper reportMapper;
    @Autowired private VolReportAppealMapper appealMapper;
    @Autowired private VolPenaltyRecordMapper penaltyRecordMapper;
    @Autowired private VolUserCreditScoreMapper creditScoreMapper;
    @Autowired private VolOrganizerProfileMapper profileMapper;
    @Autowired private OrganizerProfileService organizerProfileService;
    @Autowired private AuditRuleEngine auditRuleEngine;
    @Autowired private PenaltyService penaltyService;
    @Autowired private NotificationService notificationService;
    @Autowired private UserMapper userMapper;
    @Autowired private com.volunteer.activity.mapper.VolActivityMapper activityMapper;

    @Value("${python.ai.url:http://localhost:9094}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 举报提交 ====================

    @Override
    @Transactional
    public ResponseResult submitReport(ReportSubmitDTO dto, String userId, String userRole) {
        Long reporterId = Long.parseLong(userId);
        int role = Integer.parseInt(userRole);

        // 前端传入的举报类型统一映射：fake_activity/fraud_charge/harassment/violation_content/other → AR（活动举报）
        // VR 仅用于组织者举报志愿者的场景
        String normalizedType = dto.getReportType();
        boolean isActivityReport = "fake_activity".equals(normalizedType)
                || "fraud_charge".equals(normalizedType)
                || "harassment".equals(normalizedType)
                || "violation_content".equals(normalizedType)
                || "other".equals(normalizedType)
                || "AR".equals(normalizedType);
        boolean isVolunteerReport = "VR".equals(normalizedType);

        // VR：志愿者或组织者均可举报违规志愿者（举报人不能举报自己）
        if (isVolunteerReport && role == 1) {
            // 志愿者可以举报其他志愿者违规（如同一活动中的不良行为）
            if (reporterId.equals(dto.getReportedUserId())) {
                return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH, "无法举报自己");
            }
        }

        // 举报人门槛检查
        VolUserCreditScore reporterCredit = creditScoreMapper.selectById(reporterId);
        if (reporterCredit != null && reporterCredit.getCreditScore() < 40) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH, "您的信用分不足40分，暂无法发起举报");
        }

        if (isActivityReport && dto.getActivityId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "活动举报必须关联活动ID");
        }

        // 活动举报：检查活动是否在可举报时间内
        if (isActivityReport && dto.getActivityId() != null) {
            VolActivity activity = activityMapper.selectById(dto.getActivityId());
            if (activity == null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
            }
            LocalDateTime reportableUntil = activity.getReportableUntil();
            if (reportableUntil == null) {
                reportableUntil = activity.getEndTime() != null
                        ? activity.getEndTime().plusDays(7)
                        : activity.getCreateTime().plusDays(7);
            }
            if (LocalDateTime.now().isAfter(reportableUntil)) {
                return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH,
                        "该活动已结束超过7天，举报入口已关闭");
            }
        }

        // 检查是否重复举报
        if (dto.getActivityId() != null && reporterId > 0) {
            int dupCount = reportMapper.countByActivityAndReporter(dto.getActivityId(), reporterId);
            if (dupCount > 0) {
                return ResponseResult.errorResult(AppHttpCodeEnum.DUPLICATE_RECORD, "您已举报过该活动，请勿重复提交");
            }
        }

        // 内部统一使用 AR / VR 分类存储
        String storedType = isVolunteerReport ? "VR" : "AR";

        VolReport report = new VolReport();
        report.setReportType(storedType);
        report.setCategoryCode(dto.getCategoryCode());
        report.setReporterId(reporterId);
        report.setReportedUserId(dto.getReportedUserId());
        report.setActivityId(dto.getActivityId());
        report.setDescription(dto.getDescription());
        if (dto.getEvidenceUrls() != null && !dto.getEvidenceUrls().isEmpty()) {
            try {
                report.setEvidenceUrls(objectMapper.writeValueAsString(dto.getEvidenceUrls()));
            } catch (Exception e) {
                report.setEvidenceUrls("[]");
            }
        }
        report.setStatus(0);
        report.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        report.setReportSource(1);
        reportMapper.insert(report);

        // 通知被举报人有新举报（仅当被举报人存在时）
        if (dto.getReportedUserId() != null) {
            String reportTypeName = isActivityReport ? "活动举报" : "志愿者违规";
            String categoryName = getCategoryCodeName(dto.getCategoryCode());
            notificationService.send(
                    dto.getReportedUserId(),
                    "您收到一条" + reportTypeName,
                    "您因「" + categoryName + "」被举报。举报内容：" + (dto.getDescription() != null ? dto.getDescription().substring(0, Math.min(50, dto.getDescription().length())) : "") + "。管理员将尽快核实处理。",
                    "REPORT_SUBMIT",
                    report.getId()
            );
        }

        // MR-02：同一活动被 >= 3 人举报 → 自动下架（改为审核驳回状态，移出用户可见列表）
        if (isActivityReport && dto.getActivityId() != null) {
            LambdaQueryWrapper<VolReport> arWrapper = new LambdaQueryWrapper<>();
            arWrapper.eq(VolReport::getActivityId, dto.getActivityId());
            arWrapper.eq(VolReport::getReportType, "AR");
            long arCount = reportMapper.selectCount(arWrapper);
            if (arCount >= 3) {
                VolActivity activityUpdate = new VolActivity();
                activityUpdate.setId(dto.getActivityId());
                activityUpdate.setIsReported(1);
                activityUpdate.setAuditStatus(2);
                activityUpdate.setRejectReason("该活动被多次举报，已被系统自动下架，请等待管理员复核");
                activityMapper.updateById(activityUpdate);
                log.info("活动被多人举报，自动下架（审核驳回） activityId={} count={}", dto.getActivityId(), arCount);
            }
        }

        log.info("举报提交成功 reportId={} type={} category={} reporter={}",
                report.getId(), dto.getReportType(), dto.getCategoryCode(), reporterId);

        return ResponseResult.okResult(Map.of(
                "reportId", report.getId(),
                "message", "举报已提交，我们会尽快处理"
        ));
    }

    // ==================== 举报列表 ====================

    @Override
    public ResponseResult listReports(Integer status, Integer reportType, Integer page, Integer size) {
        return listReports(status, reportType, page, size, null, null);
    }

    @Override
    public ResponseResult listReports(Integer status, Integer reportType, Integer page, Integer size, String reporterId, String reportedUserId) {
        Page<VolReport> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(VolReport::getStatus, status);
        }
        if (reportType != null) {
            if (reportType == 1) wrapper.eq(VolReport::getReportType, "AR");
            else if (reportType == 2) wrapper.eq(VolReport::getReportType, "VR");
        }
        if (reporterId != null) {
            wrapper.eq(VolReport::getReporterId, Long.parseLong(reporterId));
        }
        if (reportedUserId != null) {
            wrapper.eq(VolReport::getReportedUserId, Long.parseLong(reportedUserId));
        }
        wrapper.orderByDesc(VolReport::getCreatedAt);
        Page<VolReport> result = reportMapper.selectPage(pageParam, wrapper);
        List<Map<String, Object>> voList = result.getRecords().stream()
                .map(this::mapToReportVO).collect(Collectors.toList());
        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    private Map<String, Object> mapToReportVO(VolReport r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("reportType", r.getReportType());
        m.put("reportTypeDesc", "AR".equals(r.getReportType()) ? "活动举报" : "志愿者违规");
        m.put("categoryCode", r.getCategoryCode());
        m.put("categoryCodeName", getCategoryCodeName(r.getCategoryCode()));
        m.put("reporterId", r.getReporterId());
        m.put("reportedUserId", r.getReportedUserId());
        m.put("activityId", r.getActivityId());
        m.put("description", r.getDescription());
        m.put("status", r.getStatus());
        m.put("statusDesc", statusDesc(r.getStatus()));
        m.put("priority", r.getPriority());
        m.put("priorityDesc", priorityDesc(r.getPriority()));
        m.put("assignedTo", r.getAssignedTo());
        m.put("reportSource", r.getReportSource());
        m.put("createdAt", r.getCreatedAt());
        m.put("resolvedAt", r.getResolvedAt());
        m.put("adminDecision", r.getAdminDecision());
        m.put("adminPenalty", r.getAdminPenalty());
        if (r.getAiAnalysis() != null && !r.getAiAnalysis().isEmpty()) {
            try { m.put("aiAnalysis", objectMapper.readTree(r.getAiAnalysis())); }
            catch (Exception e) { m.put("aiAnalysis", null); }
        }
        // 填充举报人信息
        if (r.getReporterId() != null) {
            User reporterUser = userMapper.selectById(r.getReporterId());
            String reporterNickname = reporterUser != null ? reporterUser.getNickname() : null;
            String reporterUsername = reporterUser != null ? reporterUser.getUsername() : null;
            m.put("reporterNickname", reporterNickname);
            m.put("reporterUsername", reporterUsername);
            m.put("reporterEmail", reporterUser != null ? reporterUser.getEmail() : null);
            m.put("reporterAvatar", reporterUser != null ? reporterUser.getAvatar() : null);
        }
        // 填充被举报人信息
        if (r.getReportedUserId() != null) {
            User reportedUser = userMapper.selectById(r.getReportedUserId());
            m.put("reportedNickname", reportedUser != null ? reportedUser.getNickname() : null);
            m.put("reportedUsername", reportedUser != null ? reportedUser.getUsername() : null);
            m.put("reportedRole", reportedUser != null ? reportedUser.getRole() : null);
            m.put("reportedEmail", reportedUser != null ? reportedUser.getEmail() : null);
            m.put("reportedAvatar", reportedUser != null ? reportedUser.getAvatar() : null);
            VolUserCreditScore cs = creditScoreMapper.selectById(r.getReportedUserId());
            if (cs != null) {
                m.put("reportedCreditScore", cs.getCreditScore());
            }
        }
        // 填充关联活动标题
        if (r.getActivityId() != null) {
            VolActivity activity = activityMapper.selectById(r.getActivityId());
            if (activity != null) {
                m.put("activityTitle", activity.getTitle());
                m.put("activityStatus", activity.getStatus());
            }
        }
        return m;
    }

    private String statusDesc(Integer s) {
        if (s == null) return "未知";
        return switch (s) { case 0 -> "待受理"; case 1 -> "处理中"; case 2 -> "已结案"; case 3 -> "无效举报"; default -> "未知"; };
    }

    private String priorityDesc(Integer p) {
        if (p == null) return "低";
        return switch (p) { case 0 -> "低"; case 1 -> "中"; case 2 -> "高"; case 3 -> "紧急"; default -> "低"; };
    }

    private String getCategoryCodeName(String code) {
        if (code == null) return "未知";
        // 活动举报类（AR 前端传 fake_activity/fraud_charge/harassment/violation_content/other）
        if ("fake_activity".equals(code)) return "虚假活动";
        if ("fraud_charge".equals(code)) return "欺诈收费";
        if ("harassment".equals(code)) return "骚扰虐待";
        if ("violation_content".equals(code)) return "违规内容";
        // 志愿者违规类（VR 前端传 no_show/malicious_damage/rule_violation/other）
        if ("no_show".equals(code)) return "无故缺席";
        if ("malicious_damage".equals(code)) return "恶意破坏";
        if ("rule_violation".equals(code)) return "违反规定";
        if ("other".equals(code)) return "其他违规";
        // 兼容旧格式 AR-01/VR-02
        if ("AR-01".equals(code)) return "活动内容与实际不符";
        if ("AR-02".equals(code)) return "存在安全隐患";
        if ("AR-03".equals(code)) return "组织者不当行为";
        if ("AR-04".equals(code)) return "违规收集信息";
        if ("AR-05".equals(code)) return "虚假宣传";
        if ("AR-06".equals(code)) return "其他违规";
        if ("VR-01".equals(code)) return "无故缺席";
        if ("VR-02".equals(code)) return "故意破坏秩序";
        if ("VR-03".equals(code)) return "言语攻击骚扰";
        if ("VR-04".equals(code)) return "损坏公物";
        if ("VR-05".equals(code)) return "违反纪律";
        if ("VR-06".equals(code)) return "其他违规行为";
        return code;
    }

    // ==================== 举报详情 ====================

    @Override
    public ResponseResult getReportDetail(Long reportId, String userId, String userRole) {
        VolReport report = reportMapper.selectById(reportId);
        if (report == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "举报记录不存在");

        Map<String, Object> vo = mapToReportVO(report);

        if (report.getReporterId() != null) {
            User reporterUser = userMapper.selectById(report.getReporterId());
            vo.put("reporterNickname", reporterUser != null ? reporterUser.getNickname() : null);
            vo.put("reporterUsername", reporterUser != null ? reporterUser.getUsername() : null);
            vo.put("reporterEmail", reporterUser != null ? reporterUser.getEmail() : null);
            vo.put("reporterAvatar", reporterUser != null ? reporterUser.getAvatar() : null);
        }
        if (report.getReportedUserId() != null) {
            User reportedUser = userMapper.selectById(report.getReportedUserId());
            vo.put("reportedNickname", reportedUser != null ? reportedUser.getNickname() : null);
            vo.put("reportedUsername", reportedUser != null ? reportedUser.getUsername() : null);
            vo.put("reportedRole", reportedUser != null ? reportedUser.getRole() : null);
            vo.put("reportedEmail", reportedUser != null ? reportedUser.getEmail() : null);
            vo.put("reportedAvatar", reportedUser != null ? reportedUser.getAvatar() : null);
            VolUserCreditScore cs = creditScoreMapper.selectById(report.getReportedUserId());
            if (cs != null) {
                vo.put("reportedCreditScore", cs.getCreditScore());
                vo.put("reportedOrganizerLevel", cs.getOrganizerLevel());
            }
        }
        if (report.getActivityId() != null) {
            VolActivity activity = activityMapper.selectById(report.getActivityId());
            if (activity != null) {
                vo.put("activityTitle", activity.getTitle());
                vo.put("activityStatus", activity.getStatus());
            }
        }

        LambdaQueryWrapper<VolReportAppeal> appealWrapper = new LambdaQueryWrapper<>();
        appealWrapper.eq(VolReportAppeal::getReportId, reportId);
        List<VolReportAppeal> appeals = appealMapper.selectList(appealWrapper);
        if (!appeals.isEmpty()) {
            List<Map<String, Object>> appealList = appeals.stream().map(a -> {
                Map<String, Object> am = new HashMap<>();
                am.put("id", a.getId());
                am.put("appealReason", a.getAppealReason());
                am.put("adminResponse", a.getAdminResponse());
                am.put("decision", a.getDecision());
                am.put("decisionDesc", appealDecisionDesc(a.getDecision()));
                am.put("createdAt", a.getCreatedAt());
                am.put("decidedAt", a.getDecidedAt());
                return am;
            }).collect(Collectors.toList());
            vo.put("appeals", appealList);
        }
        return ResponseResult.okResult(vo);
    }

    private String appealDecisionDesc(Integer d) {
        if (d == null) return "处理中";
        return switch (d) { case 0 -> "维持原判"; case 1 -> "撤销惩罚"; case 2 -> "重新处理"; default -> "未知"; };
    }

    // ==================== 受理举报 ====================

    @Override
    @Transactional
    public ResponseResult acceptReport(Long reportId, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        VolReport report = reportMapper.selectById(reportId);
        if (report == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "举报记录不存在");
        if (report.getStatus() != 0) return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "该举报已被受理");
        report.setStatus(1);
        report.setAssignedTo(Long.parseLong(userId));
        reportMapper.updateById(report);
        // 通知被举报人：举报已被受理
        if (report.getReportedUserId() != null) {
            String reportTypeName = "AR".equals(report.getReportType()) ? "活动举报" : "志愿者违规";
            notificationService.send(
                    report.getReportedUserId(),
                    "您的举报已被受理",
                    "您发起的「" + reportTypeName + "」（#举报" + reportId + "）已被管理员受理，正在处理中，请耐心等待。",
                    "REPORT_ACCEPTED",
                    reportId
            );
        }
        // 通知举报人：举报已被受理
        if (report.getReporterId() != null) {
            String reportTypeName = "AR".equals(report.getReportType()) ? "活动举报" : "志愿者违规";
            notificationService.send(
                    report.getReporterId(),
                    "您的举报已受理",
                    "您发起的「" + reportTypeName + "」（#举报" + reportId + "）已被管理员受理，正在处理中。",
                    "REPORT_ACCEPTED",
                    reportId
            );
        }
        log.info("举报被受理 reportId={} admin={}", reportId, userId);
        return ResponseResult.okResult(Map.of("message", "已受理该举报"));
    }

    // ==================== 处理举报 ====================

    @Override
    @Transactional
    public ResponseResult processReport(Long reportId, ReportProcessDTO dto, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Long adminId = Long.parseLong(userId);
        VolReport report = reportMapper.selectById(reportId);
        if (report == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "举报记录不存在");

        int decision = dto.getDecision();

        if (decision == 0) {
            // 采纳 AI 建议
            if (report.getAiAnalysis() != null && !report.getAiAnalysis().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode aiNode = objectMapper.readTree(report.getAiAnalysis());
                    com.fasterxml.jackson.databind.JsonNode suggestedPenalty = aiNode.has("suggested_penalty")
                            ? aiNode.get("suggested_penalty") : null;
                    if (suggestedPenalty != null) {
                        executePenaltyFromAI(report, suggestedPenalty, adminId);
                    }
                } catch (Exception e) {
                    log.warn("解析 AI 惩罚建议失败: {}", e.getMessage());
                }
            }
            report.setAdminDecision("管理员采纳AI建议");
        } else if (decision == 1) {
            // 调整后执行
            if (dto.getPenaltyTypes() != null) {
                for (String penaltyType : dto.getPenaltyTypes()) {
                    switch (penaltyType) {
                        case "credit_deduct" -> {
                            if (dto.getCreditDeduct() != null && report.getReportedUserId() != null) {
                                penaltyService.deductCredit(report.getReportedUserId(), dto.getCreditDeduct(),
                                        reportId, report.getDescription(), adminId);
                            }
                        }
                        case "ban" -> {
                            if (dto.getBanDays() != null && report.getReportedUserId() != null) {
                                penaltyService.banUser(report.getReportedUserId(), 1, dto.getBanDays(),
                                        report.getDescription(), reportId, adminId);
                            }
                        }
                        case "activity_limit" -> {
                            if (Boolean.TRUE.equals(dto.getActivityLimit()) && report.getReportedUserId() != null) {
                                penaltyService.limitActivityCreation(report.getReportedUserId(), 30,
                                        report.getDescription(), reportId, adminId);
                            }
                        }
                        case "demotion" -> {
                            if (Boolean.TRUE.equals(dto.getDemotion()) && report.getReportedUserId() != null) {
                                penaltyService.demoteOrganizer(report.getReportedUserId(), reportId, adminId);
                            }
                        }
                    }
                }
            }
            report.setAdminDecision(dto.getAdminDecision() != null ? dto.getAdminDecision() : "管理员调整惩罚参数后执行");
        } else {
            report.setStatus(3);
            report.setAdminDecision(dto.getAdminDecision() != null ? dto.getAdminDecision() : "管理员否决AI建议，判定为无效举报");
            report.setResolvedAt(LocalDateTime.now());
            reportMapper.updateById(report);
            // 通知举报人：无效举报
            if (report.getReporterId() != null) {
                notificationService.send(
                        report.getReporterId(),
                        "举报处理结果通知",
                        "您发起的举报（#举报" + reportId + "）已处理完毕，判定为无效举报。理由：" + (dto.getAdminDecision() != null ? dto.getAdminDecision() : ""),
                        "REPORT_RESOLVED",
                        reportId
                );
            }
            log.info("举报被判定为无效 reportId={} admin={}", reportId, adminId);
            return ResponseResult.okResult(Map.of("message", "已将该举报标记为无效"));
        }

        report.setStatus(2);
        report.setResolvedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        // 通知被举报人：已结案
        if (report.getReportedUserId() != null) {
            String reportTypeName = "AR".equals(report.getReportType()) ? "活动举报" : "志愿者违规";
            notificationService.send(
                    report.getReportedUserId(),
                    "举报处理结果通知",
                    "针对您的「" + reportTypeName + "」（#举报" + reportId + "）已处理完毕，结果为已结案。具体惩罚措施请查看您的信用分变化。",
                    "REPORT_RESOLVED",
                    reportId
            );
        }
        // 通知举报人：已结案
        if (report.getReporterId() != null) {
            String reportTypeName = "AR".equals(report.getReportType()) ? "活动举报" : "志愿者违规";
            notificationService.send(
                    report.getReporterId(),
                    "举报处理结果通知",
                    "您发起的「" + reportTypeName + "」（#举报" + reportId + "）已处理完毕，结果为已结案。感谢您对平台环境的维护。",
                    "REPORT_RESOLVED",
                    reportId
            );
        }

        // 增加组织者被举报次数
        if ("VR".equals(report.getReportType()) && report.getActivityId() != null) {
            VolActivity activity = activityMapper.selectById(report.getActivityId());
            if (activity != null) organizerProfileService.incrementReport(activity.getOrganizerId());
        }

        log.info("举报处理完成 reportId={} decision={} admin={}", reportId, decision, adminId);
        return ResponseResult.okResult(Map.of("message", "处理完成"));
    }

    private void executePenaltyFromAI(VolReport report, com.fasterxml.jackson.databind.JsonNode penaltyNode, Long adminId) {
        if (penaltyNode == null || report.getReportedUserId() == null) return;

        com.fasterxml.jackson.databind.JsonNode creditDeductNode = penaltyNode.get("credit_deduct");
        if (creditDeductNode != null && creditDeductNode.isInt() && creditDeductNode.asInt() > 0) {
            penaltyService.deductCredit(report.getReportedUserId(), creditDeductNode.asInt(),
                    report.getId(), "AI建议-举报惩罚", adminId);
        }
        com.fasterxml.jackson.databind.JsonNode banDaysNode = penaltyNode.get("ban_days");
        if (banDaysNode != null && banDaysNode.isInt() && banDaysNode.asInt() > 0) {
            penaltyService.banUser(report.getReportedUserId(), 1, banDaysNode.asInt(),
                    "AI建议-举报惩罚", report.getId(), adminId);
        }
        String activityLimit = penaltyNode.has("activity_limit") && !penaltyNode.get("activity_limit").isNull()
                ? penaltyNode.get("activity_limit").asText() : null;
        if ("temporary".equals(activityLimit)) {
            penaltyService.limitActivityCreation(report.getReportedUserId(), 30,
                    "AI建议-举报惩罚", report.getId(), adminId);
        }
        if (penaltyNode.has("demotion") && penaltyNode.get("demotion").asBoolean()) {
            penaltyService.demoteOrganizer(report.getReportedUserId(), report.getId(), adminId);
        }
    }

    // ==================== AI 批量分析 ====================

    @Override
    @Transactional
    public ResponseResult batchAnalyzeReports(List<Long> reportIds, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long reportId : reportIds) {
            VolReport report = reportMapper.selectById(reportId);
            if (report == null) continue;
            try {
                Map<String, Object> analysisResult = analyzeReportWithAI(report);
                if (analysisResult != null) {
                    report.setAiAnalysis(objectMapper.writeValueAsString(analysisResult));
                    Object riskLevel = analysisResult.get("risk_level");
                    if (riskLevel != null) {
                        int priority = "high".equals(riskLevel.toString()) ? 2
                                : "medium".equals(riskLevel.toString()) ? 1 : 0;
                        report.setPriority(priority);
                    }
                    reportMapper.updateById(report);
                    results.add(Map.of("reportId", reportId, "analysis", analysisResult, "success", true));
                    log.info("AI分析举报 reportId={} riskLevel={}", reportId, riskLevel);
                }
            } catch (Exception e) {
                log.warn("AI分析举报失败 reportId={} error={}", reportId, e.getMessage());
                results.add(Map.of("reportId", reportId, "success", false, "error", e.getMessage()));
            }
        }
        return ResponseResult.okResult(Map.of("results", results));
    }

    private Map<String, Object> analyzeReportWithAI(VolReport report) {
        try {
            Map<String, Object> historySummary = new HashMap<>();
            if (report.getReportedUserId() != null) {
                VolUserCreditScore cs = creditScoreMapper.selectById(report.getReportedUserId());
                if (cs != null) {
                    historySummary.put("total_reports", cs.getTotalReports());
                    historySummary.put("total_violations", cs.getTotalViolations());
                    historySummary.put("credit_score", cs.getCreditScore());
                    historySummary.put("organizer_level", cs.getOrganizerLevel());
                }
            }

            Map<String, Object> requestBody = Map.of(
                    "report_type", report.getReportType() != null ? report.getReportType() : "",
                    "category_code", report.getCategoryCode() != null ? report.getCategoryCode() : "",
                    "description", report.getDescription() != null ? report.getDescription() : "",
                    "evidence_urls", new ArrayList<String>(),
                    "reported_user_id", report.getReportedUserId() != null ? report.getReportedUserId() : 0,
                    "activity_id", report.getActivityId() != null ? report.getActivityId() : 0,
                    "reporter_id", report.getReporterId() != null ? report.getReporterId() : 0,
                    "history_summary", historySummary
            );

            String responseStr = WebClient.create(pythonAiUrl)
                    .post().uri("/ai/analyze-report").bodyValue(requestBody)
                    .retrieve().bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .onErrorReturn("{\"code\":500}")
                    .block();

            if (responseStr == null || !responseStr.contains("\"code\":200")) return null;

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseStr);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            if (dataNode == null) return null;

            return objectMapper.convertValue(dataNode, Map.class);
        } catch (Exception e) {
            log.warn("AI分析举报异常: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 统计 ====================

    @Override
    public ResponseResult getReportStatistics(String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);

        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", reportMapper.selectCount(new LambdaQueryWrapper<VolReport>().eq(VolReport::getStatus, 0)));
        stats.put("processing", reportMapper.selectCount(new LambdaQueryWrapper<VolReport>().eq(VolReport::getStatus, 1)));
        stats.put("resolved", reportMapper.selectCount(new LambdaQueryWrapper<VolReport>().eq(VolReport::getStatus, 2)));
        stats.put("invalid", reportMapper.selectCount(new LambdaQueryWrapper<VolReport>().eq(VolReport::getStatus, 3)));
        long total = reportMapper.selectCount(null);
        stats.put("total", total);
        return ResponseResult.okResult(stats);
    }

    // ==================== 惩罚记录列表 ====================

    @Override
    public ResponseResult listPenaltyRecords(Long userId, Integer page, Integer size) {
        Page<VolPenaltyRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolPenaltyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(VolPenaltyRecord::getCreatedAt);
        Page<VolPenaltyRecord> result = penaltyRecordMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("userId", p.getUserId());
            m.put("reportId", p.getReportId());
            m.put("penaltyType", p.getPenaltyType());
            m.put("penaltyValue", p.getPenaltyValue());
            m.put("penaltyContentDesc", parsePenaltyContent(p.getPenaltyType(), p.getPenaltyValue()));
            m.put("operatorId", p.getOperatorId());
            m.put("reason", p.getReason());
            m.put("isReversed", p.getIsReversed());
            m.put("createdAt", p.getCreatedAt());
            // 填充被惩罚用户信息
            if (p.getUserId() != null) {
                User penalizedUser = userMapper.selectById(p.getUserId());
                m.put("reportedNickname", penalizedUser != null ? penalizedUser.getNickname() : null);
                m.put("reportedUsername", penalizedUser != null ? penalizedUser.getUsername() : null);
                m.put("reportedEmail", penalizedUser != null ? penalizedUser.getEmail() : null);
                m.put("reportedAvatar", penalizedUser != null ? penalizedUser.getAvatar() : null);
            }
            // 填充举报人信息（通过关联的举报记录）
            if (p.getReportId() != null) {
                VolReport r = reportMapper.selectById(p.getReportId());
                if (r != null) {
                    m.put("reporterId", r.getReporterId());
                    if (r.getReporterId() != null) {
                        User reporter = userMapper.selectById(r.getReporterId());
                        m.put("reporterNickname", reporter != null ? reporter.getNickname() : null);
                        m.put("reporterUsername", reporter != null ? reporter.getUsername() : null);
                        m.put("reporterEmail", reporter != null ? reporter.getEmail() : null);
                        m.put("reporterAvatar", reporter != null ? reporter.getAvatar() : null);
                    }
                }
            }
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    // ==================== 申诉提交 ====================

    @Override
    @Transactional
    public ResponseResult submitAppeal(Long reportId, String appealReason, String userId, String userRole) {
        Long appellantId = Long.parseLong(userId);
        VolReport report = reportMapper.selectById(reportId);
        if (report == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "举报记录不存在");
        if (report.getReportedUserId() == null || !report.getReportedUserId().equals(appellantId))
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH, "只有被举报人可以申诉");
        if (report.getStatus() != 2)
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "只能在举报结案后申诉");

        LambdaQueryWrapper<VolReportAppeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolReportAppeal::getReportId, reportId);
        if (appealMapper.selectCount(wrapper) > 0)
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "该举报已申诉过，请勿重复申诉");

        VolReportAppeal appeal = new VolReportAppeal();
        appeal.setReportId(reportId);
        appeal.setAppellantId(appellantId);
        appeal.setAppealReason(appealReason);
        appealMapper.insert(appeal);

        // 通知举报人：有新申诉
        if (report.getReporterId() != null) {
            notificationService.send(
                    report.getReporterId(),
                    "被举报人提交了申诉",
                    "您发起的举报（#举报" + reportId + "）被举报人已提交申诉，管理员将进行复核。",
                    "APPEAL_SUBMITTED",
                    appeal.getId()
            );
        }
        // 通知被举报人：申诉提交成功
        notificationService.send(
                appellantId,
                "申诉提交成功",
                "您对举报（#举报" + reportId + "）的申诉已提交，管理员将进行复核，请耐心等待结果。",
                "APPEAL_RESULT",
                appeal.getId()
        );

        log.info("申诉提交 appealId={} reportId={} appellant={}", appeal.getId(), reportId, appellantId);
        return ResponseResult.okResult(Map.of("appealId", appeal.getId(), "message", "申诉已提交"));
    }

    // ==================== 申诉列表 ====================

    @Override
    public ResponseResult listAppeals(Integer decision, Integer page, Integer size, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Page<VolReportAppeal> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolReportAppeal> wrapper = new LambdaQueryWrapper<>();
        if (decision != null) wrapper.eq(VolReportAppeal::getDecision, decision);
        wrapper.orderByDesc(VolReportAppeal::getCreatedAt);
        Page<VolReportAppeal> result = appealMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("reportId", a.getReportId());
            m.put("appellantId", a.getAppellantId());
            m.put("appealReason", a.getAppealReason());
            m.put("adminResponse", a.getAdminResponse());
            m.put("decision", a.getDecision());
            m.put("decisionDesc", appealDecisionDesc(a.getDecision()));
            m.put("decidedBy", a.getDecidedBy());
            m.put("createdAt", a.getCreatedAt());
            m.put("decidedAt", a.getDecidedAt());
            if (a.getAppellantId() != null) {
                User appellant = userMapper.selectById(a.getAppellantId());
                m.put("appellantNickname", appellant != null ? appellant.getNickname() : null);
                m.put("appellantUsername", appellant != null ? appellant.getUsername() : null);
                m.put("appellantEmail", appellant != null ? appellant.getEmail() : null);
                m.put("appellantAvatar", appellant != null ? appellant.getAvatar() : null);
            }
            VolReport r = reportMapper.selectById(a.getReportId());
            if (r != null) {
                m.put("reportType", r.getReportType());
                m.put("categoryCode", r.getCategoryCode());
                m.put("activityId", r.getActivityId());
                m.put("reportedUserId", r.getReportedUserId());
                m.put("adminPenalty", r.getAdminPenalty());
                m.put("reporterId", r.getReporterId());
                // 填充举报人信息
                if (r.getReporterId() != null) {
                    User reporter = userMapper.selectById(r.getReporterId());
                    m.put("reporterNickname", reporter != null ? reporter.getNickname() : null);
                    m.put("reporterUsername", reporter != null ? reporter.getUsername() : null);
                    m.put("reporterEmail", reporter != null ? reporter.getEmail() : null);
                    m.put("reporterAvatar", reporter != null ? reporter.getAvatar() : null);
                }
                // 填充被举报人信息
                if (r.getReportedUserId() != null) {
                    User reported = userMapper.selectById(r.getReportedUserId());
                    m.put("reportedNickname", reported != null ? reported.getNickname() : null);
                    m.put("reportedUsername", reported != null ? reported.getUsername() : null);
                    m.put("reportedEmail", reported != null ? reported.getEmail() : null);
                    m.put("reportedAvatar", reported != null ? reported.getAvatar() : null);
                }
            }
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    @Override
    public ResponseResult listAppeals(Integer decision, Integer page, Integer size, String currentUserId) {
        Page<VolReportAppeal> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolReportAppeal> wrapper = new LambdaQueryWrapper<>();
        if (decision != null) wrapper.eq(VolReportAppeal::getDecision, decision);
        wrapper.orderByDesc(VolReportAppeal::getCreatedAt);
        Page<VolReportAppeal> result = appealMapper.selectPage(pageParam, wrapper);
        List<Map<String, Object>> voList = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("reportId", a.getReportId());
            m.put("appellantId", a.getAppellantId());
            m.put("appealContent", a.getAppealReason());
            m.put("adminReply", a.getAdminResponse());
            m.put("status", a.getDecision());
            m.put("decisionDesc", appealDecisionDesc(a.getDecision()));
            m.put("decidedBy", a.getDecidedBy());
            m.put("createdAt", a.getCreatedAt());
            m.put("decidedAt", a.getDecidedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    // ==================== 处理申诉 ====================

    @Override
    @Transactional
    public ResponseResult processAppeal(Long appealId, Integer decision, String adminResponse, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Long adminId = Long.parseLong(userId);
        VolReportAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "申诉记录不存在");
        if (appeal.getDecision() != null) return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "该申诉已处理");

        appeal.setDecision(decision);
        appeal.setAdminResponse(adminResponse);
        appeal.setDecidedBy(adminId);
        appeal.setDecidedAt(LocalDateTime.now());
        appealMapper.updateById(appeal);

        String decisionResult = decision == 1 ? "申诉通过，惩罚已撤销" : decision == 2 ? "申诉驳回，继续执行原惩罚" : "申诉已处理";
        String resultNote = decision == 1 ? "您的惩罚措施已被撤销，信用分已恢复。" : decision == 2 ? "经复核，您的申诉被驳回，原惩罚措施继续执行。" : "您的申诉已有处理结果，请查看详情。";

        // 通知申诉人结果
        notificationService.send(
                appeal.getAppellantId(),
                "申诉结果通知",
                "您对举报（#举报" + appeal.getReportId() + "）的申诉已有处理结果：" + decisionResult + "。" + resultNote,
                "APPEAL_RESULT",
                appealId
        );

        if (decision == 1) {
            VolReport report = reportMapper.selectById(appeal.getReportId());
            if (report != null && report.getReportedUserId() != null) {
                reversePenalties(report.getReportedUserId(), report.getId(), adminId);
                log.info("申诉通过，撤销惩罚 userId={} reportId={}", report.getReportedUserId(), report.getId());
            }
        }

        log.info("申诉处理完成 appealId={} decision={} admin={}", appealId, decision, adminId);
        return ResponseResult.okResult(Map.of("message", "申诉处理完成"));
    }

    private void reversePenalties(Long userId, Long reportId, Long adminId) {
        LambdaQueryWrapper<VolPenaltyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolPenaltyRecord::getUserId, userId);
        wrapper.eq(VolPenaltyRecord::getReportId, reportId);
        wrapper.eq(VolPenaltyRecord::getIsReversed, 0);
        List<VolPenaltyRecord> penalties = penaltyRecordMapper.selectList(wrapper);

        for (VolPenaltyRecord p : penalties) {
            p.setIsReversed(1);
            p.setReversedBy(adminId);
            p.setReversedAt(LocalDateTime.now());
            penaltyRecordMapper.updateById(p);

            if ("credit_deduct".equals(p.getPenaltyType()) && p.getPenaltyValue() != null) {
                try {
                    Map<String, Object> pv = objectMapper.readValue(p.getPenaltyValue(), Map.class);
                    Object deduct = pv.get("credit_deduct");
                    if (deduct instanceof Number) {
                        int amount = ((Number) deduct).intValue();
                        penaltyService.recordPenalty("credit_recover", Map.of("credit_recover", amount),
                                userId, reportId, adminId, null, "申诉撤销-恢复积分");
                    }
                } catch (Exception e) {
                    log.warn("解析惩罚值失败: {}", e.getMessage());
                }
            }
        }
    }

    // ==================== 信用分管理 ====================

    @Override
    public ResponseResult listCreditScores(Integer riskLevel, Integer page, Integer size, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Page<VolUserCreditScore> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolUserCreditScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(VolUserCreditScore::getCreditScore);
        Page<VolUserCreditScore> result = creditScoreMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream().map(cs -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", cs.getUserId());
            m.put("creditScore", cs.getCreditScore());
            m.put("creditLevel", creditLevel(cs.getCreditScore()));
            m.put("totalViolations", cs.getTotalViolations());
            m.put("totalReports", cs.getTotalReports());
            m.put("banType", cs.getBanType());
            m.put("banUntil", cs.getBanUntil());
            m.put("organizerLevel", cs.getOrganizerLevel());
            m.put("lastViolationAt", cs.getLastViolationAt());
            if (cs.getUserId() != null) {
                m.put("userNickname", userMapper.selectNicknameById(cs.getUserId()));
                m.put("userUsername", userMapper.selectUsernameById(cs.getUserId()));
                User user = userMapper.selectById(cs.getUserId());
                if (user != null) m.put("userRole", user.getRole());
            }
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    private String creditLevel(Integer score) {
        if (score == null) return "未知";
        if (score >= 80) return "优秀";
        if (score >= 60) return "良好";
        if (score >= 40) return "关注";
        if (score >= 20) return "警告";
        return "失信";
    }

    private String parsePenaltyContent(String penaltyType, String penaltyValue) {
        if (penaltyType == null) return "-";
        StringBuilder sb = new StringBuilder();
        if ("credit_deduct".equals(penaltyType)) {
            sb.append("扣除信用分");
            if (penaltyValue != null && !penaltyValue.isEmpty()) {
                try {
                    Map<?, ?> pv = objectMapper.readValue(penaltyValue, Map.class);
                    Object deduct = pv.get("credit_deduct");
                    if (deduct != null) sb.append(deduct).append("分");
                } catch (Exception e) {
                    sb.append("（").append(penaltyValue).append("）");
                }
            }
        } else if ("ban".equals(penaltyType)) {
            sb.append("账号封禁");
            if (penaltyValue != null && !penaltyValue.isEmpty()) {
                try {
                    Map<?, ?> pv = objectMapper.readValue(penaltyValue, Map.class);
                    Object days = pv.get("ban_days");
                    if (days != null) sb.append(days).append("天");
                } catch (Exception e) {
                    sb.append("（").append(penaltyValue).append("）");
                }
            }
        } else if ("activity_limit".equals(penaltyType)) {
            sb.append("限制发起活动");
            if (penaltyValue != null && !penaltyValue.isEmpty()) {
                try {
                    Map<?, ?> pv = objectMapper.readValue(penaltyValue, Map.class);
                    Object days = pv.get("days");
                    if (days != null) sb.append(days).append("天");
                } catch (Exception e) {
                    sb.append("（").append(penaltyValue).append("）");
                }
            }
        } else if ("demotion".equals(penaltyType)) {
            sb.append("组织者降级");
        } else if ("credit_recover".equals(penaltyType)) {
            sb.append("信用分恢复");
            if (penaltyValue != null && !penaltyValue.isEmpty()) {
                try {
                    Map<?, ?> pv = objectMapper.readValue(penaltyValue, Map.class);
                    Object recover = pv.get("credit_recover");
                    if (recover != null) sb.append(recover).append("分");
                } catch (Exception e) {
                    sb.append("（").append(penaltyValue).append("）");
                }
            }
        } else if ("unban".equals(penaltyType)) {
            sb.append("账号解封");
        } else {
            sb.append(penaltyType);
            if (penaltyValue != null && !penaltyValue.isEmpty()) {
                sb.append("（").append(penaltyValue).append("）");
            }
        }
        return sb.toString();
    }

    @Override
    public ResponseResult getUserCreditScore(Long targetUserId, String userId, String userRole) {
        VolUserCreditScore cs = creditScoreMapper.selectById(targetUserId);
        if (cs == null) {
            cs = new VolUserCreditScore();
            cs.setUserId(targetUserId);
            cs.setCreditScore(70);
            cs.setTotalViolations(0);
            cs.setTotalReports(0);
            cs.setBanType(0);
            cs.setOrganizerLevel(0);
            creditScoreMapper.insert(cs);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("userId", cs.getUserId());
        m.put("creditScore", cs.getCreditScore());
        m.put("creditLevel", creditLevel(cs.getCreditScore()));
        m.put("totalViolations", cs.getTotalViolations());
        m.put("totalReports", cs.getTotalReports());
        m.put("banType", cs.getBanType());
        m.put("banUntil", cs.getBanUntil());
        m.put("banReason", cs.getBanReason());
        m.put("activityLimitUntil", cs.getActivityLimitUntil());
        m.put("organizerLevel", cs.getOrganizerLevel());
        m.put("demotionCount", cs.getDemotionCount());
        m.put("lastViolationAt", cs.getLastViolationAt());
        return ResponseResult.okResult(m);
    }

    @Override
    public ResponseResult getUserCreditScore(Long targetUserId, String currentUserId) {
        VolUserCreditScore cs = creditScoreMapper.selectById(targetUserId);
        if (cs == null) {
            cs = new VolUserCreditScore();
            cs.setUserId(targetUserId);
            cs.setCreditScore(70);
            cs.setTotalViolations(0);
            cs.setTotalReports(0);
            cs.setBanType(0);
            cs.setOrganizerLevel(0);
            creditScoreMapper.insert(cs);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("userId", cs.getUserId());
        m.put("creditScore", cs.getCreditScore());
        m.put("creditLevel", creditLevel(cs.getCreditScore()));
        m.put("totalViolations", cs.getTotalViolations());
        m.put("totalReports", cs.getTotalReports());
        m.put("banStatus", cs.getBanStatus());
        m.put("banUntil", cs.getBanUntil());
        m.put("banReason", cs.getBanReason());
        m.put("activityLimit", cs.getActivityLimit());
        m.put("activityLimitUntil", cs.getActivityLimitUntil());
        m.put("organizerLevel", cs.getOrganizerLevel());
        m.put("demotionCount", cs.getDemotionCount());
        m.put("lastDeductTime", cs.getLastViolationAt());
        m.put("totalDeductCount", cs.getTotalViolations());
        return ResponseResult.okResult(m);
    }

    @Override
    @Transactional
    public ResponseResult forceUnban(Long targetUserId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        penaltyService.unbanUser(targetUserId, 0L);
        return ResponseResult.okResult(Map.of("message", "解封成功"));
    }

    @Override
    @Transactional
    public ResponseResult reversePenalty(Long penaltyId, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Long adminId = Long.parseLong(userId);
        VolPenaltyRecord penalty = penaltyRecordMapper.selectById(penaltyId);
        if (penalty == null) return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "惩罚记录不存在");
        if (penalty.getIsReversed() != null && penalty.getIsReversed() == 1)
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "该惩罚已被撤销");

        penalty.setIsReversed(1);
        penalty.setReversedBy(adminId);
        penalty.setReversedAt(LocalDateTime.now());
        penaltyRecordMapper.updateById(penalty);

        // 积分恢复（仅针对扣分记录）
        if ("credit_deduct".equals(penalty.getPenaltyType()) && penalty.getUserId() != null) {
            try {
                if (penalty.getPenaltyValue() != null) {
                    Map<?, ?> pv = objectMapper.readValue(penalty.getPenaltyValue(), Map.class);
                    Object deduct = pv.get("credit_deduct");
                    if (deduct instanceof Number) {
                        int amount = ((Number) deduct).intValue();
                        penaltyService.recordPenalty("credit_recover", Map.of("credit_recover", amount),
                                penalty.getUserId(), penalty.getReportId(), adminId, null, "管理员撤销-恢复积分");
                    }
                }
            } catch (Exception e) {
                log.warn("解析惩罚值失败: {}", e.getMessage());
            }
        }

        // 解除封禁
        if ("ban".equals(penalty.getPenaltyType()) && penalty.getUserId() != null) {
            penaltyService.unbanUser(penalty.getUserId(), adminId);
        }

        log.info("惩罚记录被撤销 penaltyId={} adminId={}", penaltyId, adminId);
        return ResponseResult.okResult(Map.of("message", "撤销成功"));
    }

    @Override
    public ResponseResult listBlacklist(Integer page, Integer size, String userId, String userRole) {
        if (!isAdmin(userRole)) return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        Page<VolUserCreditScore> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolUserCreditScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(VolUserCreditScore::getBanType, 1, 2);
        wrapper.orderByDesc(VolUserCreditScore::getLastViolationAt);
        Page<VolUserCreditScore> result = creditScoreMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream().map(cs -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", cs.getUserId());
            m.put("creditScore", cs.getCreditScore());
            m.put("creditLevel", creditLevel(cs.getCreditScore()));
            m.put("banType", cs.getBanType());
            m.put("banUntil", cs.getBanUntil());
            m.put("banReason", cs.getBanReason());
            m.put("totalViolations", cs.getTotalViolations());
            m.put("lastViolationAt", cs.getLastViolationAt());
            if (cs.getUserId() != null) {
                m.put("userNickname", userMapper.selectNicknameById(cs.getUserId()));
                m.put("userUsername", userMapper.selectUsernameById(cs.getUserId()));
                User user = userMapper.selectById(cs.getUserId());
                if (user != null) m.put("userRole", user.getRole());
            }
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    private boolean isAdmin(String userRole) {
        return Integer.parseInt(userRole) == 0;
    }
}
