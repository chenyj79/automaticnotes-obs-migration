package com.black.task.service;

import com.black.task.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一管理基于 SSE (Server-Sent Events) 的任务进度推送服务
 */
@Slf4j
@Service
public class SseTaskService {

    // 缓存所有建立连接的 SseEmitter，支持一个任务多端订阅（多标签页）
    // Key: taskId, Value: List<SseEmitter>
    private final Map<Long, List<SseEmitter>> taskEmitters = new ConcurrentHashMap<>();

    /**
     * 为指定任务创建一个 SSE 连接
     * 默认超时时间设置为 30 分钟
     */
    public SseEmitter createEmitter(Long taskId) {
        // 设置 30 分钟超时，足以覆盖绝大部分长视频处理时间
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 支持同一个任务多客户端（或多浏览器标签页）同时订阅
        List<SseEmitter> emitterList = taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        emitterList.add(emitter);

        // 注册回调，在连接完成、超时或异常时清理缓存
        Runnable cleanup = () -> {
            log.info("清理 SseEmitter - taskId: {}", taskId);
            emitterList.remove(emitter);
            if (emitterList.isEmpty()) {
                taskEmitters.remove(taskId, emitterList);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        log.info("建立 SSE 订阅 - taskId: {}, 当前订阅数: {}", taskId, emitterList.size());
        return emitter;
    }

    /**
     * 向特定任务的订阅者广播状态更新
     */
    public void sendTaskStatusUpdate(Long taskId, TaskStatus status) {
        List<SseEmitter> emitterList = taskEmitters.get(taskId);
        if (emitterList != null && !emitterList.isEmpty()) {
            for (SseEmitter emitter : emitterList) {
                try {
                    // 发送纯文本状态，也可以发送 JSON
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(status.name()));
                    log.debug("SSE 推送成功 - taskId: {}, status: {}", taskId, status.name());

                    // 如果是最终状态，通知完毕后主动完成连接
                    if (status == TaskStatus.STATUS_SUCCESS || status == TaskStatus.STATUS_FAILED) {
                        emitter.complete();
                    }
                } catch (Exception e) { // 需捕获任意异常，避免导致外部事务的回滚
                    log.warn("SSE 推送失败 - taskId: {}, status: {}, 异常: {}", taskId, status.name(), e.getMessage());
                    // 推送失败说明该客户端连接已异常，直接将其从列表中移除
                    emitterList.remove(emitter);
                }
            }
        }
    }

    /**
     * 每隔 30 秒向所有存活的 SSE 连接发送一个注释心跳，
     * 防止 Nginx 或网关因为连接空闲太久而主动切断连接。
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        taskEmitters.forEach((taskId, emitterList) -> {
            for (SseEmitter emitter : emitterList) {
                try {
                    // 仅发送注释(comment)，前端 EventSource 默认会忽略注释，不会触发 onmessage
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    // 发送失败说明连接可能已断开，依靠正常的 cleanup 逻辑或主动移除即可
                    emitterList.remove(emitter);
                }
            }
        });
    }
}
