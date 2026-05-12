package com.volunteer.activity.service.impl;

import com.volunteer.activity.dto.AddActivityDTO;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolOrganizerProfile;
import com.volunteer.activity.mapper.VolOrganizerProfileMapper;
import com.volunteer.activity.service.AuditRuleEngine;
import com.volunteer.activity.service.OrganizerProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuditRuleEngineImpl implements AuditRuleEngine {

    @Autowired private VolOrganizerProfileMapper profileMapper;

    @Override
    public AuditResult evaluate(AddActivityDTO dto, Long organizerId) {
        List<RuleResult> hitRules = getHitRules(dto, organizerId);
        return buildAuditResult(hitRules);
    }

    @Override
    public AuditResult evaluateActivity(VolActivity activity) {
        List<RuleResult> hitRules = getHitRulesFromActivity(activity);
        return buildAuditResult(hitRules);
    }

    private AuditResult buildAuditResult(List<RuleResult> hitRules) {
        if (hitRules.isEmpty()) {
            return new AuditResult(hitRules, true, "ai_review", "通过所有规则检查");
        }

        boolean hasReject = hitRules.stream().anyMatch(r -> "reject".equals(r.action()));
        boolean hasManual = hitRules.stream().anyMatch(r -> "manual".equals(r.action()));

        String immediateAction;
        String message;

        if (hasReject) {
            immediateAction = "reject";
            List<String> rejectReasons = hitRules.stream()
                    .filter(r -> "reject".equals(r.action()))
                    .map(r -> r.ruleCode() + ": " + r.message())
                    .toList();
            message = "命中客观驳回规则：" + String.join("；", rejectReasons);
        } else if (hasManual) {
            immediateAction = "manual";
            List<String> manualReasons = hitRules.stream()
                    .filter(r -> "manual".equals(r.action()))
                    .map(r -> r.ruleCode() + ": " + r.message())
                    .toList();
            message = "命中转人工规则：" + String.join("；", manualReasons);
        } else {
            immediateAction = "ai_review";
            message = "命中标记规则";
        }

        return new AuditResult(hitRules, false, immediateAction, message);
    }

    @Override
    public List<RuleResult> getHitRules(AddActivityDTO dto, Long organizerId) {
        List<RuleResult> results = new ArrayList<>();

        // R-01: 地点模糊规则（通过 description 字数间接判断，或检查 title 中是否包含地点提示）
        // R-01 改用 description 字数判断
        if (dto.getDescription() == null || dto.getDescription().trim().length() < 30) {
            results.add(new RuleResult(true, "R-01", "描述空洞规则",
                    "活动描述不足30字，内容过于空洞",
                    "reject"));
        }

        // R-02: 时间异常规则
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            if (dto.getEndTime().isBefore(dto.getStartTime())) {
                results.add(new RuleResult(true, "R-02", "时间异常规则",
                        "结束时间早于开始时间",
                        "reject"));
            } else {
                long daysBetween = ChronoUnit.DAYS.between(dto.getStartTime(), dto.getEndTime());
                if (daysBetween > 30) {
                    results.add(new RuleResult(true, "R-02", "时间异常规则",
                            "活动时间跨度超过30天（" + daysBetween + "天）",
                            "reject"));
                }
            }
        }

        // R-05: 描述字数检查（与 R-01 合并，上面已处理）

        // R-09: 快速连续提交规则（1h 内提交超过 3 次）
        // 此规则需要查询最近提交记录，暂时跳过，需要在实现时注入 registrationMapper
        // 简化处理：在 OrganizerProfileService 中单独处理

        // 关键词黑名单检查（内存实现，运行时可扩展）
        String[] blacklistKeywords = {"刷单", "返利", "传销", "非法集资", "色情", "赌博", "代办证件"};
        String fullText = (dto.getTitle() != null ? dto.getTitle() : "")
                + " " + (dto.getDescription() != null ? dto.getDescription() : "");
        for (String keyword : blacklistKeywords) {
            if (fullText.contains(keyword)) {
                results.add(new RuleResult(true, "R-04", "关键词黑名单",
                        "标题或描述包含违禁词：" + keyword,
                        "reject"));
                break;
            }
        }

        return results;
    }

    private List<RuleResult> getHitRulesFromActivity(VolActivity activity) {
        List<RuleResult> results = new ArrayList<>();

        if (activity.getDescription() == null || activity.getDescription().trim().length() < 30) {
            results.add(new RuleResult(true, "R-01", "描述空洞规则",
                    "活动描述不足30字",
                    "reject"));
        }

        if (activity.getStartTime() != null && activity.getEndTime() != null) {
            if (activity.getEndTime().isBefore(activity.getStartTime())) {
                results.add(new RuleResult(true, "R-02", "时间异常规则",
                        "结束时间早于开始时间",
                        "reject"));
            }
        }

        String[] blacklistKeywords = {"刷单", "返利", "传销", "非法集资", "色情", "赌博", "代办证件"};
        String fullText = (activity.getTitle() != null ? activity.getTitle() : "")
                + " " + (activity.getDescription() != null ? activity.getDescription() : "");
        for (String keyword : blacklistKeywords) {
            if (fullText.contains(keyword)) {
                results.add(new RuleResult(true, "R-04", "关键词黑名单",
                        "标题或描述包含违禁词：" + keyword,
                        "reject"));
                break;
            }
        }

        return results;
    }
}
