package com.volunteer.credit.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.credit.entity.VolDlq;
import com.volunteer.credit.mapper.VolDlqMapper;
import com.volunteer.credit.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 积分 MQ 消费者，订阅两个 topic：
 *   activity-checkin-topic   → 现场签到 +10 分（type=5）
 *   activity-complete-topic  → 活动结束：已签到者 +50 分（type=2），缺席者 -20 分（type=6）
 */
@Slf4j
@Component
public class CreditConsumer {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Autowired
    private CreditService creditService;

    @Autowired
    private VolDlqMapper dlqMapper;

    private static final int MAX_RETRY = 3;

    private DefaultMQPushConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe("activity-checkin-topic",  "*");
        consumer.subscribe("activity-complete-topic", "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    String body  = new String(msg.getBody(), StandardCharsets.UTF_8);
                    String topic = msg.getTopic();
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    if ("activity-checkin-topic".equals(topic)) {
                        Long userId     = toLong(payload.get("userId"));
                        Long activityId = toLong(payload.get("activityId"));
                        if (userId != null && activityId != null) {
                            creditService.changePoints(userId, activityId, 10, 5, "现场签到奖励");
                        }

                    } else if ("activity-complete-topic".equals(topic)) {
                        Long activityId = toLong(payload.get("activityId"));
                        if (activityId != null) {
                            creditService.awardAllForActivity(activityId);
                            creditService.penalizeAbsentForActivity(activityId);
                        }
                    }

                } catch (Exception e) {
                    log.error("积分消费异常 msgId={} 重试次数={}", msg.getMsgId(), msg.getReconsumeTimes(), e);
                    // 超过最大重试次数，写入死信队列补偿表，不再重试
                    if (msg.getReconsumeTimes() >= MAX_RETRY) {
                        saveToDlq(msg, e);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        log.info("积分 MQ Consumer 启动，NameServer={}", nameServer);
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) consumer.shutdown();
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private void saveToDlq(MessageExt msg, Exception cause) {
        try {
            VolDlq dlq = new VolDlq();
            dlq.setMsgId(msg.getMsgId());
            dlq.setTopic(msg.getTopic());
            dlq.setBody(new String(msg.getBody(), StandardCharsets.UTF_8));
            dlq.setErrorMsg(cause.getMessage() != null
                    ? cause.getMessage().substring(0, Math.min(cause.getMessage().length(), 500))
                    : cause.getClass().getName());
            dlq.setReconsumeTimes(msg.getReconsumeTimes());
            dlq.setStatus(0);
            dlqMapper.insert(dlq);
            log.warn("消息已转入DLQ补偿表 msgId={} topic={}", msg.getMsgId(), msg.getTopic());
        } catch (Exception ex) {
            log.error("写入DLQ表失败 msgId={}", msg.getMsgId(), ex);
        }
    }
}
