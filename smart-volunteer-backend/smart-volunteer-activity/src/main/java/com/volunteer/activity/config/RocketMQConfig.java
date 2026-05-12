package com.volunteer.activity.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer defaultMQProducer() throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("activity-producer-group");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        log.info("RocketMQ Producer 启动，NameServer={}", nameServer);
        return producer;
    }
}
