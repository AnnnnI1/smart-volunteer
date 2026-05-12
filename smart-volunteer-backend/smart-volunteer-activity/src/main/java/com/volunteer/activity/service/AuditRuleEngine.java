package com.volunteer.activity.service;

import com.volunteer.activity.dto.AddActivityDTO;
import com.volunteer.activity.entity.VolActivity;

import java.util.List;
import java.util.Map;

public interface AuditRuleEngine {

    record RuleResult(boolean hit, String ruleCode, String ruleName, String message, String action) {}

    record AuditResult(
        List<RuleResult> hitRules,
        boolean passed,
        String immediateAction,
        String message
    ) {}

    AuditResult evaluate(AddActivityDTO dto, Long organizerId);

    AuditResult evaluateActivity(VolActivity activity);

    List<RuleResult> getHitRules(AddActivityDTO dto, Long organizerId);
}
