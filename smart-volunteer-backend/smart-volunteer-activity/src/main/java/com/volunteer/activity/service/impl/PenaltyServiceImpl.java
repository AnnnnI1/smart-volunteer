package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.entity.VolOrganizerProfile;
import com.volunteer.activity.entity.VolPenaltyRecord;
import com.volunteer.activity.entity.VolUserCreditScore;
import com.volunteer.activity.mapper.VolOrganizerProfileMapper;
import com.volunteer.activity.mapper.VolPenaltyRecordMapper;
import com.volunteer.activity.mapper.VolUserCreditScoreMapper;
import com.volunteer.activity.service.OrganizerProfileService;
import com.volunteer.activity.service.PenaltyService;
import com.volunteer.common.entity.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class PenaltyServiceImpl implements PenaltyService {

    @Autowired private VolUserCreditScoreMapper creditScoreMapper;
    @Autowired private VolPenaltyRecordMapper penaltyRecordMapper;
    @Autowired private VolOrganizerProfileMapper profileMapper;
    @Autowired private OrganizerProfileService organizerProfileService;
    @Autowired private WebClient.Builder webClientBuilder;

    @Value("${credit.service.url:http://localhost:9095}")
    private String creditServiceUrl;

    @Value("${python.ai.url:http://localhost:9094}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void ensureCreditScoreExists(Long userId) {
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        if (cs == null) {
            cs = new VolUserCreditScore();
            cs.setUserId(userId);
            cs.setCreditScore(70);
            cs.setTotalViolations(0);
            cs.setTotalReports(0);
            cs.setBanType(0);
            cs.setOrganizerLevel(0);
            creditScoreMapper.insert(cs);
            log.info("新建用户信用分记录 userId={}", userId);
        }
    }

    @Override
    @Transactional
    public ResponseResult deductCredit(Long userId, Integer amount, Long reportId, String reason, Long operatorId) {
        ensureCreditScoreExists(userId);
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        int before = cs.getCreditScore();
        int after = Math.max(0, cs.getCreditScore() - amount);
        cs.setCreditScore(after);
        cs.setTotalViolations(cs.getTotalViolations() + 1);
        cs.setLastViolationAt(LocalDateTime.now());

        // 信用分低于40自动封禁
        if (after < 40 && cs.getBanType() == 0) {
            cs.setBanType(1);
            cs.setBanUntil(LocalDateTime.now().plusDays(7));
            cs.setBanReason("信用分低于40分，自动临时封禁");
        }

        creditScoreMapper.updateById(cs);

        recordPenalty("credit_deduct", Map.of("credit_deduct", amount, "before", before, "after", after),
                userId, reportId, operatorId, null, reason);

        log.info("积分扣除 userId={} amount={} before={} after={}", userId, amount, before, after);
        return ResponseResult.okResult(Map.of("message", "积分扣除成功", "deducted", amount, "creditScore", after));
    }

    @Override
    @Transactional
    public ResponseResult banUser(Long userId, Integer banType, Integer days, String reason, Long reportId, Long operatorId) {
        ensureCreditScoreExists(userId);
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        cs.setBanType(banType);
        if (banType == 1) {
            cs.setBanUntil(LocalDateTime.now().plusDays(days));
        } else if (banType == 2) {
            cs.setBanUntil(null);
        }
        cs.setBanReason(reason);
        cs.setTotalViolations(cs.getTotalViolations() + 1);
        cs.setLastViolationAt(LocalDateTime.now());
        creditScoreMapper.updateById(cs);

        recordPenalty("ban", Map.of("ban_type", banType, "days", days),
                userId, reportId, operatorId, null, reason);

        log.info("用户封禁 userId={} banType={} days={}", userId, banType, days);
        return ResponseResult.okResult(Map.of("message", "封禁成功"));
    }

    @Override
    @Transactional
    public ResponseResult unbanUser(Long userId, Long operatorId) {
        ensureCreditScoreExists(userId);
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        cs.setBanType(0);
        cs.setBanUntil(null);
        cs.setBanReason(null);
        creditScoreMapper.updateById(cs);

        recordPenalty("unban", Map.of("operator_id", operatorId),
                userId, null, operatorId, null, "管理员强制解封");

        log.info("用户解封 userId={} operator={}", userId, operatorId);
        return ResponseResult.okResult(Map.of("message", "解封成功"));
    }

    @Override
    @Transactional
    public ResponseResult limitActivityCreation(Long userId, Integer days, String reason, Long reportId, Long operatorId) {
        ensureCreditScoreExists(userId);
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        cs.setActivityLimitUntil(LocalDateTime.now().plusDays(days));
        cs.setTotalViolations(cs.getTotalViolations() + 1);
        cs.setLastViolationAt(LocalDateTime.now());
        creditScoreMapper.updateById(cs);

        recordPenalty("activity_limit", Map.of("days", days),
                userId, reportId, operatorId, null, reason);

        log.info("限制发起活动 userId={} days={}", userId, days);
        return ResponseResult.okResult(Map.of("message", "已限制发起活动"));
    }

    @Override
    @Transactional
    public ResponseResult demoteOrganizer(Long userId, Long reportId, Long operatorId) {
        ensureCreditScoreExists(userId);
        VolUserCreditScore cs = creditScoreMapper.selectById(userId);
        int currentLevel = cs.getOrganizerLevel() != null ? cs.getOrganizerLevel() : 1;
        int newLevel = Math.max(0, currentLevel - 1);
        cs.setOrganizerLevel(newLevel);
        cs.setDemotionCount(cs.getDemotionCount() + 1);
        cs.setActivityLimitUntil(LocalDateTime.now().plusDays(90));
        cs.setTotalViolations(cs.getTotalViolations() + 1);
        cs.setLastViolationAt(LocalDateTime.now());
        creditScoreMapper.updateById(cs);

        recordPenalty("demotion", Map.of("before_level", currentLevel, "after_level", newLevel),
                userId, reportId, operatorId, null, "举报惩罚-组织者降级");

        log.info("组织者降级 userId={} before={} after={}", userId, currentLevel, newLevel);
        return ResponseResult.okResult(Map.of("message", "组织者已降级"));
    }

    @Override
    @Transactional
    public ResponseResult recordPenalty(String penaltyType, Map<String, Object> penaltyValue, Long userId,
                                       Long reportId, Long operatorId, String aiSuggested, String reason) {
        ensureCreditScoreExists(userId);

        VolPenaltyRecord record = new VolPenaltyRecord();
        record.setUserId(userId);
        record.setReportId(reportId);
        record.setPenaltyType(penaltyType);
        try {
            record.setPenaltyValue(objectMapper.writeValueAsString(penaltyValue));
        } catch (Exception e) {
            record.setPenaltyValue("{}");
        }
        record.setOperatorId(operatorId);
        record.setAiSuggested(aiSuggested);
        record.setReason(reason);
        record.setIsReversed(0);
        record.setCreatedAt(LocalDateTime.now());
        penaltyRecordMapper.insert(record);

        return ResponseResult.okResult(Map.of("penaltyId", record.getId()));
    }

    @Override
    @Transactional
    public void checkAndRecoverCredit() {
        LambdaQueryWrapper<VolUserCreditScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(VolUserCreditScore::getCreditScore, 70);
        wrapper.gt(VolUserCreditScore::getCreditScore, 0);
        wrapper.isNull(VolUserCreditScore::getLastViolationAt);
        // 恢复逻辑：无违规30天后每天+1分，封顶70分
        // 这里简化处理：每日扫描所有用户，检查最后违规时间
        // 实际应使用定时任务，但当前实现作为占位
        log.debug("信用分恢复检查执行");
    }
}
