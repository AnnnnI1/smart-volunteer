package com.volunteer.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.vo.PageVo;
import com.volunteer.credit.entity.VolCreditRecord;
import com.volunteer.credit.mapper.VolCreditBalanceMapper;
import com.volunteer.credit.mapper.VolCreditRecordMapper;
import com.volunteer.credit.mapper.VolRegistrationMapper;
import com.volunteer.credit.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CreditServiceImpl implements CreditService {

    @Autowired private VolCreditBalanceMapper balanceMapper;
    @Autowired private VolCreditRecordMapper  recordMapper;
    @Autowired private VolRegistrationMapper  registrationMapper;

    /**
     * 变更积分，写余额快照 + 流水明细。
     * 对 type=1/2/3 做幂等保护（同一 userId+activityId+type 只处理一次）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePoints(Long userId, Long activityId, int delta, int type, String remark) {
        // 幂等检查（type=4 管理员调整不限次数）
        if (type != 4 && activityId != null) {
            int exists = recordMapper.existsRecord(userId, activityId, type);
            if (exists > 0) {
                log.info("幂等跳过 userId={} activityId={} type={}", userId, activityId, type);
                return false;
            }
        }
        // 原子更新余额
        balanceMapper.upsertBalance(userId, delta);
        // 读取最新余额
        Integer newBalance = balanceMapper.selectBalance(userId);
        if (newBalance == null) newBalance = 0;
        // 余额不允许为负
        if (newBalance < 0) {
            balanceMapper.upsertBalance(userId, -delta); // 回滚
            log.warn("积分不足，跳过扣分 userId={}", userId);
            return false;
        }

        // 写流水
        VolCreditRecord record = new VolCreditRecord();
        record.setUserId(userId);
        record.setActivityId(activityId);
        record.setChangeType(type);
        record.setPoints(delta);
        record.setBalanceAfter(newBalance);
        record.setRemark(remark);
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);

        log.info("积分变更 userId={} delta={} type={} balance={}", userId, delta, type, newBalance);
        return true;
    }

    /**
     * 活动结束时，为所有报名成功的志愿者发放完成奖励（+50分）。
     */
    @Override
    public void awardAllForActivity(Long activityId) {
        List<Long> userIds = registrationMapper.selectUserIdsByActivity(activityId);
        log.info("活动[{}]结束，为 {} 名志愿者发放完成奖励", activityId, userIds.size());
        for (Long uid : userIds) {
            try {
                changePoints(uid, activityId, 50, 2, "活动完成奖励");
            } catch (Exception e) {
                log.error("为用户[{}]发放完成奖励失败", uid, e);
            }
        }
    }

    /**
     * 活动结束时，为所有缺席（status=4）的志愿者扣除缺席惩罚（-20分）。
     */
    @Override
    public void penalizeAbsentForActivity(Long activityId) {
        List<Long> userIds = registrationMapper.selectAbsentUserIdsByActivity(activityId);
        log.info("活动[{}]结束，对 {} 名缺席志愿者扣除缺席惩罚", activityId, userIds.size());
        for (Long uid : userIds) {
            try {
                changePoints(uid, activityId, -20, 6, "缺席扣分");
            } catch (Exception e) {
                log.error("为用户[{}]扣除缺席惩罚失败", uid, e);
            }
        }
    }

    @Override
    public ResponseResult getBalance(Long userId) {
        Integer balance = balanceMapper.selectBalance(userId);
        return ResponseResult.okResult(Map.of("balance", balance == null ? 0 : balance));
    }

    @Override
    public ResponseResult getRecords(Long userId, Integer page, Integer size) {
        Page<VolCreditRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolCreditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolCreditRecord::getUserId, userId)
               .orderByDesc(VolCreditRecord::getCreateTime);
        Page<VolCreditRecord> result = recordMapper.selectPage(pageParam, wrapper);
        return ResponseResult.okResult(new PageVo(result.getRecords(), result.getTotal()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult adminAdjust(Long userId, int delta, String remark) {
        changePoints(userId, null, delta, 4, remark == null ? "管理员调整" : remark);
        return ResponseResult.okResult();
    }
}
