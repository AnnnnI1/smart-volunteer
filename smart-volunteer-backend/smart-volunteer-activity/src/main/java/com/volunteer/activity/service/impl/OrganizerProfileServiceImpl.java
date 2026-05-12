package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.VolOrganizerProfile;
import com.volunteer.activity.mapper.VolOrganizerProfileMapper;
import com.volunteer.activity.service.OrganizerProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OrganizerProfileServiceImpl implements OrganizerProfileService {

    @Autowired private VolOrganizerProfileMapper profileMapper;

    private static final double REJECT_RATE_THRESHOLD = 0.30;
    private static final int BYPASS_THRESHOLD = 3;
    private static final int RISK_HIGH_THRESHOLD = 2;

    @Override
    public void ensureProfileExists(Long organizerId) {
        VolOrganizerProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<VolOrganizerProfile>()
                        .eq(VolOrganizerProfile::getOrganizerId, organizerId)
        );
        if (profile == null) {
            profile = new VolOrganizerProfile();
            profile.setOrganizerId(organizerId);
            profile.setTotalSubmissions(0);
            profile.setTotalRejected(0);
            profile.setTotalReports(0);
            profile.setAvgParticipantCancelRate(BigDecimal.ZERO);
            profile.setBypassAttempts(0);
            profile.setRiskLevel(0);
            profileMapper.insert(profile);
            log.info("新建组织者画像 organizerId={}", organizerId);
        }
    }

    @Override
    public void incrementSubmission(Long organizerId) {
        ensureProfileExists(organizerId);
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile != null) {
            profile.setTotalSubmissions(profile.getTotalSubmissions() + 1);
            updateRiskLevel(organizerId);
            profileMapper.updateById(profile);
        }
    }

    @Override
    public void incrementRejected(Long organizerId) {
        ensureProfileExists(organizerId);
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile != null) {
            profile.setTotalRejected(profile.getTotalRejected() + 1);
            profile.setBypassAttempts(profile.getBypassAttempts() + 1);
            updateRiskLevel(organizerId);
            profileMapper.updateById(profile);
            log.info("组织者被驳回 incrementRejected organizerId={}", organizerId);
        }
    }

    @Override
    public void incrementReport(Long organizerId) {
        ensureProfileExists(organizerId);
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile != null) {
            profile.setTotalReports(profile.getTotalReports() + 1);
            updateRiskLevel(organizerId);
            profileMapper.updateById(profile);
        }
    }

    @Override
    public void incrementBypassAttempt(Long organizerId) {
        ensureProfileExists(organizerId);
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile != null) {
            profile.setBypassAttempts(profile.getBypassAttempts() + 1);
            updateRiskLevel(organizerId);
            profileMapper.updateById(profile);
        }
    }

    @Override
    public void setForceManualReview(Long organizerId, LocalDateTime until) {
        ensureProfileExists(organizerId);
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile != null) {
            profile.setForceManualReviewUntil(until);
            profileMapper.updateById(profile);
        }
    }

    @Override
    public void updateRiskLevel(Long organizerId) {
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile == null) return;

        int riskLevel = 0;

        // 驳回率 > 30%
        if (profile.getTotalSubmissions() > 0) {
            double rejectRate = (double) profile.getTotalRejected() / profile.getTotalSubmissions();
            if (rejectRate > REJECT_RATE_THRESHOLD) {
                riskLevel = Math.max(riskLevel, 1);
            }
            if (rejectRate > 0.5) {
                riskLevel = Math.max(riskLevel, 2);
            }
        }

        // 绕过尝试 > 3 次
        if (profile.getBypassAttempts() >= BYPASS_THRESHOLD) {
            riskLevel = Math.max(riskLevel, 2);
        }

        // 举报率 > 20%（当 total_submissions > 0 时有意义）
        if (profile.getTotalSubmissions() > 0) {
            double reportRate = (double) profile.getTotalReports() / profile.getTotalSubmissions();
            if (reportRate > 0.2) {
                riskLevel = Math.max(riskLevel, 1);
            }
            if (reportRate > 0.4) {
                riskLevel = Math.max(riskLevel, 2);
            }
        }

        profile.setRiskLevel(riskLevel);
        profile.setLastUpdated(LocalDateTime.now());
        profileMapper.updateById(profile);

        // 画像异常时自动强制人工审核（30天）
        if (riskLevel >= RISK_HIGH_THRESHOLD) {
            profile.setForceManualReviewUntil(LocalDateTime.now().plusDays(30));
            profileMapper.updateById(profile);
        }

        log.info("更新组织者画像风险等级 organizerId={} riskLevel={}", organizerId, riskLevel);
    }

    @Override
    public VolOrganizerProfile getProfile(Long organizerId) {
        return profileMapper.selectOne(
                new LambdaQueryWrapper<VolOrganizerProfile>()
                        .eq(VolOrganizerProfile::getOrganizerId, organizerId)
        );
    }

    @Override
    public Map<String, Object> getProfileSummary(Long organizerId) {
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile == null) {
            ensureProfileExists(organizerId);
            profile = getProfile(organizerId);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("organizerId", organizerId);
        summary.put("totalSubmissions", profile.getTotalSubmissions());
        summary.put("totalRejected", profile.getTotalRejected());
        summary.put("totalReports", profile.getTotalReports());
        summary.put("bypassAttempts", profile.getBypassAttempts());
        summary.put("riskLevel", profile.getRiskLevel());
        summary.put("forceManualReviewUntil", profile.getForceManualReviewUntil());

        if (profile.getTotalSubmissions() > 0) {
            double rejectRate = (double) profile.getTotalRejected() / profile.getTotalSubmissions();
            double reportRate = (double) profile.getTotalReports() / profile.getTotalSubmissions();
            summary.put("rejectRate", Math.round(rejectRate * 100) + "%");
            summary.put("reportRate", Math.round(reportRate * 100) + "%");
        } else {
            summary.put("rejectRate", "0%");
            summary.put("reportRate", "0%");
        }

        boolean forceManual = shouldForceManualReview(organizerId);
        summary.put("shouldForceManualReview", forceManual);
        summary.put("forceReason", forceManual ? "组织者画像异常，进入强制人工审核" : null);

        return summary;
    }

    @Override
    public boolean shouldForceManualReview(Long organizerId) {
        VolOrganizerProfile profile = getProfile(organizerId);
        if (profile == null) return false;

        LocalDateTime now = LocalDateTime.now();
        if (profile.getForceManualReviewUntil() != null
                && profile.getForceManualReviewUntil().isAfter(now)) {
            return true;
        }

        if (profile.getRiskLevel() != null && profile.getRiskLevel() >= RISK_HIGH_THRESHOLD) {
            return true;
        }

        return false;
    }
}
