package com.black.task.mq.consumer;

import com.black.asr.service.VideoService;
import com.black.exception.BusinessException;
import com.black.knowledge.service.KnowledgePipelineService;
import com.black.task.po.VideoTask;
import com.black.task.service.VideoTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "VIDEO_PROCESS_TOPIC", consumerGroup = "video-process-group")
public class VideoTaskConsumer implements RocketMQListener<Long> {

    private final KnowledgePipelineService knowledgePipelineService;
    private final VideoTaskService videoTaskService;
    private final VideoService videoService;

    @Override
    public void onMessage(Long taskId) {
        log.info("接收到视频处理任务 - taskId: {}", taskId);

        VideoTask task;
        try {
            task = videoTaskService.getTaskById(taskId);
        } catch (Exception e) {
            log.error("[消费者] 任务不存在，丢弃消息 - taskId: {}", taskId, e);
            return;
        }

        if (task.getStatus().isTerminal()) {
            log.info("[幂等] 任务已处于终态，跳过 - taskId: {}, status: {}", taskId, task.getStatus());
            return;
        }

        task = videoTaskService.incrementAndGetRetryCount(taskId);
        int attempt = task.getRetryCount();

        if (attempt > task.getMaxRetries()) {
            log.warn("[重试上限] taskId: {}, 已执行 {} 次，超过上限 {}，标记为失败",
                    taskId, attempt, task.getMaxRetries());
            videoTaskService.markFinalFailed(task,
                    "超过最大执行次数 " + task.getMaxRetries() + "，最后失败原因: " + task.getFailureReason());
            try {
                videoService.cleanupFailedVideoRecord(task.getVideoId());
                log.info("[清理] 失败任务已清理视频记录 - taskId: {}, videoId: {}", taskId, task.getVideoId());
            } catch (Exception cleanupEx) {
                log.error("[清理] 清理失败 - taskId: {}, videoId: {}", taskId, task.getVideoId(), cleanupEx);
            }
            return;
        }

        log.info("[执行] 开始第 {}/{} 次执行 - taskId: {}, 当前状态: {}",
                attempt, task.getMaxRetries(), taskId, task.getStatus());
        try {
            knowledgePipelineService.executePipeline(taskId);
            log.info("视频处理任务执行成功 - taskId: {}", taskId);
        } catch (Exception e) {
            log.error("[任务异常] taskId: {}, 第 {}/{} 次执行失败",
                    taskId, attempt, task.getMaxRetries(), e);
            videoTaskService.recordFailureReason(task, e.getMessage());
            throw BusinessException.taskExecutionFailed(taskId, e.getMessage(), e);
        }
    }
}
