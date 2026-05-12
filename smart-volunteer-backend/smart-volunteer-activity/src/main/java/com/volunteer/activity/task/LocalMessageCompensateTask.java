package com.volunteer.activity.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.entity.VolLocalMessage;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolLocalMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 本地消息表补偿任务（兜底机制）
 *
 * 作用：将已成功写入报名记录的消息同步到 MySQL 的 joined_quota 字段，
 * 保证 Redis 扣减与 MySQL 统计数据的最终一致性。
 *
 * 当前状态：RocketMQ 已接入，主路由由 ActivityRegisterConsumer 处理。
 * 本任务作为兜底，仅处理 MQ 消费失败后仍为 status=0 的消息。
 * 触发条件：消息在 30 秒内仍未被标记为已处理（MQ 异常场景）。
 */
@Slf4j
@Component
public class LocalMessageCompensateTask {

    @Autowired private VolLocalMessageMapper localMessageMapper;
    @Autowired private VolActivityMapper activityMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 30000) // 每 30 秒轮询一次（兜底，MQ 正常时消息已被消费）
    @Transactional(rollbackFor = Exception.class)
    public void processLocalMessages() {
        // 查询待处理消息，每次最多处理 50 条
        LambdaQueryWrapper<VolLocalMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolLocalMessage::getStatus, 0)
               .eq(VolLocalMessage::getBusinessType, "ACTIVITY_REGISTER")
               .last("LIMIT 50");
        List<VolLocalMessage> messages = localMessageMapper.selectList(wrapper);

        for (VolLocalMessage msg : messages) {
            try {
                JsonNode node = objectMapper.readTree(msg.getContent());
                Long activityId = node.get("activityId").asLong();

                // 原子递增 joined_quota
                activityMapper.incrementJoinedQuota(activityId);

                // 标记消息处理成功
                msg.setStatus(1);
                localMessageMapper.updateById(msg);

            } catch (Exception e) {
                log.error("处理本地消息失败 messageId={}", msg.getMessageId(), e);
                msg.setStatus(2);
                localMessageMapper.updateById(msg);
            }
        }
    }
}
