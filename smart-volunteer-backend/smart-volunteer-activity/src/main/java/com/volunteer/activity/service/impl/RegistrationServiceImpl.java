package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolCheckinCode;
import com.volunteer.activity.entity.VolLocalMessage;
import com.volunteer.activity.entity.VolRegistration;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolCheckinCodeMapper;
import com.volunteer.activity.mapper.VolLocalMessageMapper;
import com.volunteer.activity.mapper.VolRegistrationMapper;
import com.volunteer.activity.service.RegistrationService;
import com.volunteer.activity.utils.RedisCache;
import com.volunteer.activity.vo.MyRegistrationVO;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import com.volunteer.common.vo.PageVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RegistrationServiceImpl extends ServiceImpl<VolRegistrationMapper, VolRegistration>
        implements RegistrationService {

    private static final String QUOTA_KEY   = "activity:quota:";
    private static final String MQ_TOPIC    = "activity-register-topic";
    private static final String CHECKIN_MQ  = "activity-checkin-topic";

    @Autowired private VolRegistrationMapper registrationMapper;
    @Autowired private VolActivityMapper activityMapper;
    @Autowired private VolLocalMessageMapper localMessageMapper;
    @Autowired private VolCheckinCodeMapper checkinCodeMapper;
    @Autowired private RedisCache redisCache;
    @Autowired private DefaultMQProducer defaultMQProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成个人专属签到码（SHA-256 前8位，不可预测，不可碰撞）
     */
    private String generateCheckinCode(Long userId, Long activityId, Long registrationId) {
        String raw = userId + "-" + activityId + "-" + registrationId + "-" + System.currentTimeMillis();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).substring(0, 8).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 报名流程：
     * 1. 校验活动状态
     * 2. Redis 原子扣减名额（防超发）
     * 3. 写本地消息表 + 写报名记录（同一事务，保证原子性）
     * 4. 生成个人专属签到码（一人一码）
     * 5. 事务提交后发送 RocketMQ 消息（异步解耦）
     * 6. MQ 消费者异步更新 joined_quota（最终一致性）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult register(Long userId, Long activityId) {

        // 1. 校验活动
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动当前不在报名阶段");
        }

        // 2. Redis 原子扣减名额（核心防超发）
        String quotaKey = QUOTA_KEY + activityId;

        // key 缺失时（如 Redis 重启后 key 丢失）用 SETNX 原子重建，避免 DECR 从 -1 起算导致死锁
        if (!Boolean.TRUE.equals(redisCache.hasKey(quotaKey))) {
            int dbRemain = Math.max(activity.getTotalQuota() - activity.getJoinedQuota(), 0);
            redisCache.setIfAbsent(quotaKey, String.valueOf(dbRemain));
        }

        Long remain = redisCache.decrement(quotaKey);
        if (remain < 0) {
            redisCache.increment(quotaKey);
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_FULL);
        }

        String messageId = UUID.randomUUID().toString().replace("-", "");
        try {
            // 3. 写本地消息表（事务内，与报名记录同生共死）
            VolLocalMessage message = new VolLocalMessage();
            message.setMessageId(messageId);
            message.setBusinessType("ACTIVITY_REGISTER");
            message.setContent(objectMapper.writeValueAsString(
                    Map.of("userId", userId, "activityId", activityId)));
            message.setStatus(0);
            localMessageMapper.insert(message);

            // 4. 优先尝试重新激活已取消的记录
            int reactivated = registrationMapper.reactivateCancelled(userId, activityId);
            if (reactivated == 0) {
                VolRegistration registration = new VolRegistration();
                registration.setUserId(userId);
                registration.setActivityId(activityId);
                registration.setStatus(0);
                registrationMapper.insert(registration);
                // 4.1 为新报名记录生成个人专属签到码
                String checkinCode = generateCheckinCode(userId, activityId, registration.getId());
                registration.setCheckinCode(checkinCode);
                registrationMapper.updateById(registration);
                log.info("报名成功，生成个人签到码 userId={} activityId={} code={}", userId, activityId, checkinCode);
            } else {
                // 重新激活的记录也需要生成新的签到码
                LambdaQueryWrapper<VolRegistration> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(VolRegistration::getUserId, userId)
                       .eq(VolRegistration::getActivityId, activityId)
                       .eq(VolRegistration::getStatus, 0);
                VolRegistration existingReg = registrationMapper.selectOne(wrapper);
                if (existingReg != null && existingReg.getCheckinCode() == null) {
                    String checkinCode = generateCheckinCode(userId, activityId, existingReg.getId());
                    existingReg.setCheckinCode(checkinCode);
                    registrationMapper.updateById(existingReg);
                    log.info("重新激活报名，生成个人签到码 userId={} activityId={} code={}", userId, activityId, checkinCode);
                }
            }

        } catch (DuplicateKeyException e) {
            redisCache.increment(quotaKey);
            return ResponseResult.errorResult(AppHttpCodeEnum.ALREADY_REGISTERED);
        } catch (Exception e) {
            redisCache.increment(quotaKey);
            log.error("报名写库异常 userId={} activityId={}", userId, activityId, e);
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR);
        }

        // 5. 事务提交后发送 RocketMQ 消息
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("activityId", activityId, "userId", userId, "messageId", messageId));
            Message mqMsg = new Message(MQ_TOPIC, payload.getBytes());
            SendResult result = defaultMQProducer.send(mqMsg);
            log.info("MQ消息已发送 topic={} activityId={} sendStatus={}", MQ_TOPIC, activityId, result.getSendStatus());
        } catch (Exception e) {
            log.warn("MQ消息发送失败，将由本地消息表补偿 messageId={}", messageId);
        }

        return ResponseResult.okResult();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult cancel(Long userId, Long activityId) {
        LambdaQueryWrapper<VolRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolRegistration::getUserId, userId)
               .eq(VolRegistration::getActivityId, activityId)
               .eq(VolRegistration::getStatus, 0);
        VolRegistration reg = registrationMapper.selectOne(wrapper);

        if (Objects.isNull(reg)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "未找到有效报名记录");
        }

        reg.setStatus(1); // 已取消
        registrationMapper.updateById(reg);

        // 归还 Redis 名额
        redisCache.increment(QUOTA_KEY + activityId);
        // MySQL joined_quota 递减
        activityMapper.decrementJoinedQuota(activityId);

        // 通知积分服务扣减积分（MQ 异步，失败不影响取消结果）
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", userId, "activityId", activityId));
            defaultMQProducer.send(new Message("activity-cancel-topic", payload.getBytes()));
        } catch (Exception e) {
            log.warn("积分扣减 MQ 发送失败 userId={} activityId={}", userId, activityId);
        }

        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult myRegistrations(Long userId, Integer page, Integer size) {
        Page<VolRegistration> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolRegistration::getUserId, userId)
               .orderByDesc(VolRegistration::getCreateTime);
        Page<VolRegistration> result = registrationMapper.selectPage(pageParam, wrapper);

        // 批量查询活动标题，一次 IN 查询避免 N+1
        List<Long> activityIds = result.getRecords().stream()
                .map(VolRegistration::getActivityId).distinct().collect(Collectors.toList());
        Map<Long, String> titleMap = activityIds.isEmpty() ? Map.of() :
                activityMapper.selectBatchIds(activityIds).stream()
                        .collect(Collectors.toMap(VolActivity::getId, VolActivity::getTitle));

        List<MyRegistrationVO> voList = result.getRecords().stream().map(reg -> {
            MyRegistrationVO vo = new MyRegistrationVO();
            vo.setId(reg.getId());
            vo.setUserId(reg.getUserId());
            vo.setActivityId(reg.getActivityId());
            vo.setStatus(reg.getStatus());
            vo.setCreateTime(reg.getCreateTime());
            vo.setActivityTitle(titleMap.getOrDefault(reg.getActivityId(), "活动" + reg.getActivityId()));
            vo.setCheckinCode(reg.getCheckinCode());
            return vo;
        }).collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    /**
     * 管理员生成/刷新签到码（活动必须处于进行中 status=2）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult generateCheckinCode(Long userId, Long activityId) {
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "仅进行中的活动可生成签到码");
        }
        // 废弃旧码
        checkinCodeMapper.deactivateAll(activityId);
        // 生成 6 位随机数字码，60秒后过期
        String code = String.format("%06d", new Random().nextInt(1000000));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusSeconds(60);
        VolCheckinCode checkinCode = new VolCheckinCode();
        checkinCode.setActivityId(activityId);
        checkinCode.setCode(code);
        checkinCode.setCreateTime(now);
        checkinCode.setIsActive(1);
        checkinCode.setExpireAt(expireAt);
        checkinCodeMapper.insert(checkinCode);
        log.info("签到码已生成 activityId={} code={} expireAt={}", activityId, code, expireAt);
        return ResponseResult.okResult(Map.of("code", code, "expireAt", expireAt.toString()));
    }

    /**
     * 个人专属码签到（防作弊核心）：通过 checkinCode 精确匹配报名记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult checkinByCode(String checkinCode) {
        // 通过签到码精确匹配报名记录（单表 PK 查询，性能更优）
        VolRegistration reg = registrationMapper.selectByCheckinCode(checkinCode);
        if (Objects.isNull(reg)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "签到码无效或已使用");
        }

        // 校验活动状态
        VolActivity activity = activityMapper.selectById(reg.getActivityId());
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动不在进行中，无法签到");
        }

        // 更新为已签到
        reg.setStatus(2);
        registrationMapper.updateById(reg);
        log.info("个人签到码签到成功 userId={} activityId={} code={}", reg.getUserId(), reg.getActivityId(), checkinCode);

        // 通知积分服务发签到奖励
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", reg.getUserId(), "activityId", reg.getActivityId()));
            defaultMQProducer.send(new Message(CHECKIN_MQ, payload.getBytes()));
            log.info("签到MQ已发送 userId={} activityId={}", reg.getUserId(), reg.getActivityId());
        } catch (Exception e) {
            log.warn("签到MQ发送失败 userId={} activityId={}", reg.getUserId(), reg.getActivityId());
        }

        return ResponseResult.okResult(Map.of(
                "userId", reg.getUserId(),
                "activityId", reg.getActivityId(),
                "message", "签到成功"
        ));
    }

    /**
     * 志愿者现场签到：比对签到码，将报名状态从 0 改为 2，发 MQ 消息给积分服务
     * @deprecated 保留兼容，新系统请使用 checkinByCode
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult checkin(Long userId, Long activityId, String code) {
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动不在进行中，无法签到");
        }
        // 校验签到码（selectActive 已过滤过期码）
        VolCheckinCode activeCode = checkinCodeMapper.selectActive(activityId);
        if (activeCode == null || !activeCode.getCode().equals(code)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "签到码错误或已失效，请向组织者获取最新签到码");
        }
        // 查找有效报名记录
        LambdaQueryWrapper<VolRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolRegistration::getUserId, userId)
               .eq(VolRegistration::getActivityId, activityId)
               .eq(VolRegistration::getStatus, 0);
        VolRegistration reg = registrationMapper.selectOne(wrapper);
        if (Objects.isNull(reg)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "未找到有效报名记录，或已签到");
        }
        // 更新为已签到
        reg.setStatus(2);
        registrationMapper.updateById(reg);

        // 通知积分服务发签到奖励
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", userId, "activityId", activityId));
            defaultMQProducer.send(new Message(CHECKIN_MQ, payload.getBytes()));
            log.info("签到MQ已发送 userId={} activityId={}", userId, activityId);
        } catch (Exception e) {
            log.warn("签到MQ发送失败 userId={} activityId={}", userId, activityId);
        }

        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getActivityRegistrations(Long activityId, String userId, String userRole) {
        int role = Integer.parseInt(userRole);
        if (role != 0 && role != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (role == 2 && !Objects.equals(Long.parseLong(userId), activity.getOrganizerId())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return ResponseResult.okResult(registrationMapper.selectWithUserInfo(activityId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult manualCheckin(Long activityId, Long targetUserId, String userId, String userRole) {
        int role = Integer.parseInt(userRole);
        if (role != 0 && role != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (role == 2 && !Objects.equals(Long.parseLong(userId), activity.getOrganizerId())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        if (activity.getStatus() != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动不在进行中，无法签到");
        }
        LambdaQueryWrapper<VolRegistration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolRegistration::getUserId, targetUserId)
               .eq(VolRegistration::getActivityId, activityId)
               .eq(VolRegistration::getStatus, 0);
        VolRegistration reg = registrationMapper.selectOne(wrapper);
        if (Objects.isNull(reg)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "未找到有效报名记录，或已签到");
        }
        reg.setStatus(2);
        registrationMapper.updateById(reg);

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("userId", targetUserId, "activityId", activityId));
            defaultMQProducer.send(new Message(CHECKIN_MQ, payload.getBytes()));
        } catch (Exception e) {
            log.warn("手动签到MQ发送失败 targetUserId={} activityId={}", targetUserId, activityId);
        }
        return ResponseResult.okResult();
    }
}
