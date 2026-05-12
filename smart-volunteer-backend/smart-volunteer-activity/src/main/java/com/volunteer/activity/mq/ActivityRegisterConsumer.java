package com.volunteer.activity.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.activity.entity.VolLocalMessage;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolLocalMessageMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 活动报名 RocketMQ 消费者
 *
 * 消费 activity-register-topic，异步更新 MySQL 中的 joined_quota。
 * 与本地消息表结合，实现双重可靠性保障：
 *   - MQ 正常：实时消费，毫秒级更新
 *   - MQ 异常：LocalMessageCompensateTask 30秒兜底补偿
 */
@Slf4j
@Component
public class ActivityRegisterConsumer {

    @Autowired private VolActivityMapper activityMapper;
    @Autowired private VolLocalMessageMapper localMessageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DefaultMQPushConsumer consumer;

    public ActivityRegisterConsumer(@Value("${rocketmq.name-server}") String nameServer) throws Exception {
        consumer = new DefaultMQPushConsumer("activity-consumer-group");
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe("activity-register-topic", "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    String body = new String(msg.getBody());
                    JsonNode node = objectMapper.readTree(body);
                    Long activityId = node.get("activityId").asLong();
                    String messageId = node.get("messageId").asText();

                    // 更新 joined_quota
                    activityMapper.incrementJoinedQuota(activityId);

                    // 将本地消息表标记为已处理，避免补偿任务重复执行
                    LambdaUpdateWrapper<VolLocalMessage> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(VolLocalMessage::getMessageId, messageId)
                           .eq(VolLocalMessage::getStatus, 0)
                           .set(VolLocalMessage::getStatus, 1);
                    localMessageMapper.update(null, wrapper);

                    log.info("[MQ消费] 报名处理成功 activityId={} messageId={}", activityId, messageId);

                } catch (Exception e) {
                    log.error("[MQ消费] 处理失败，将重试: msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        log.info("RocketMQ Consumer 启动，订阅 topic=activity-register-topic");
    }

    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("RocketMQ Consumer 已关闭");
        }
    }
}
