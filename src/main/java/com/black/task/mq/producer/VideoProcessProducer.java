package com.black.task.mq.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public static final String TOPIC = "VIDEO_PROCESS_TOPIC";

    /**
     * 发送视频处理消息（首次提交）
     */
    public void sendProcessMessage(Long taskId) {
        log.info("发送视频处理消息 - taskId: {}", taskId);
        rocketMQTemplate.convertAndSend(TOPIC, taskId);
    }

    /**
     * 发送手动重试消息
     * 使用 RocketMQ 延迟消息，延迟约 5 秒后投递，避免立即重试打满系统
     */
    public void sendRetryMessage(Long taskId) {
        log.info("发送手动重试消息（延迟 5s） - taskId: {}", taskId);
        // RocketMQ 延迟级别: 1=1s, 2=5s, 3=10s, 4=30s, ...
        rocketMQTemplate.syncSend(TOPIC,
                MessageBuilder.withPayload(taskId).build(),
                3000,   // 发送超时 3 秒
                2);     // 延迟级别 2 = 5 秒
    }
}
