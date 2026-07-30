package com.black.task.controller;

import com.black.task.enums.TaskStatus;
import com.black.exception.BusinessException;
import com.black.knowledge.vo.VideoProcessResultVo;
import com.black.model.ProcessResult;
import com.black.task.po.VideoTask;
import com.black.task.service.VideoTaskService;
import com.black.task.mq.producer.VideoProcessProducer;
import com.black.task.vo.PendingTaskVo;
import java.util.List;
import com.alibaba.fastjson2.JSON;
import com.black.user.annotation.CurrentUser;
import com.black.user.security.LoginUser;
import com.black.task.service.SseTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final VideoTaskService videoTaskService;
    private final VideoProcessProducer videoProcessProducer;
    private final SseTaskService sseTaskService;
    private final StringRedisTemplate stringRedisTemplate;

    @PostMapping("/{taskId}/sse-ticket")
    public ResponseEntity<ProcessResult<String>> getSseTicket(@PathVariable Long taskId,
                                                              @CurrentUser LoginUser loginUser) {
        videoTaskService.getTaskAndCheckOwner(taskId, loginUser.getId());
        String ticket = UUID.randomUUID().toString().replace("-", "");
        String value = loginUser.getId() + "_" + taskId;
        stringRedisTemplate.opsForValue().set("sse:ticket:" + ticket, value, 30, TimeUnit.MINUTES);
        return ResponseEntity.ok(ProcessResult.success("获取订阅凭证成功", ticket));
    }

    @GetMapping(value = "/{taskId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTaskStatus(@PathVariable Long taskId, @RequestParam("ticket") String ticket) {
        String value = stringRedisTemplate.opsForValue().get("sse:ticket:" + ticket);
        if (value == null) {
            throw BusinessException.unauthorized();
        }
        
        String[] parts = value.split("_");
        if (parts.length != 2 || !parts[1].equals(String.valueOf(taskId))) {
            throw BusinessException.taskAccessDenied();
        }

        SseEmitter emitter = sseTaskService.createEmitter(taskId);
        VideoTask task = videoTaskService.getTaskAndCheckOwner(taskId, Long.parseLong(parts[0]));
        sseTaskService.sendTaskStatusUpdate(taskId, task.getStatus());
        
        return emitter;
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<ProcessResult<TaskStatus>> getTaskStatus(@PathVariable Long taskId,
                                                                   @CurrentUser LoginUser loginUser) {
        VideoTask task = videoTaskService.getTaskAndCheckOwner(taskId, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("获取状态成功", task.getStatus()));
    }

    @GetMapping("/video/{videoId}/status")
    public ResponseEntity<ProcessResult<TaskStatus>> getVideoTaskStatus(@PathVariable Long videoId,
                                                                       @CurrentUser LoginUser loginUser) {
        VideoTask task = videoTaskService.getTaskByVideoId(videoId);
        if (!task.getUserId().equals(loginUser.getId())) {
            throw BusinessException.taskAccessDenied();
        }
        return ResponseEntity.ok(ProcessResult.success("获取状态成功", task.getStatus()));
    }

    @GetMapping("/framework/{frameworkId}/pending")
    public ResponseEntity<ProcessResult<List<PendingTaskVo>>> getPendingTasks(@PathVariable Long frameworkId,
                                                                              @CurrentUser LoginUser loginUser) {
        List<PendingTaskVo> pendingTasks = videoTaskService.getPendingTasksByFrameworkId(frameworkId, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("获取进行中的任务成功", pendingTasks));
    }

    @GetMapping("/{taskId}/result")
    public ResponseEntity<ProcessResult<VideoProcessResultVo>> getTaskResult(@PathVariable Long taskId,
                                                                             @CurrentUser LoginUser loginUser) {
        VideoTask task = videoTaskService.getTaskAndCheckOwner(taskId, loginUser.getId());

        if (TaskStatus.STATUS_SUCCESS != task.getStatus()) {
            return ResponseEntity.ok(ProcessResult.failure("分析任务尚未完成或已失败，当前状态: " + task.getStatus().getDescription()));
        }

        if (task.getResultJson() == null) {
            return ResponseEntity.ok(ProcessResult.failure("分析结果不存在"));
        }

        VideoProcessResultVo result = JSON.parseObject(task.getResultJson(), VideoProcessResultVo.class);
        return ResponseEntity.ok(ProcessResult.success("获取分析报表成功", result));
    }

    @RequestMapping(value = "/{taskId}/viewed", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ProcessResult<Void>> markReportViewed(@PathVariable Long taskId,
                                                                @CurrentUser LoginUser loginUser) {
        videoTaskService.markReportViewed(taskId, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("标记已读成功"));
    }

    @RequestMapping(value = "/{taskId}/cancel", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ProcessResult<Void>> cancelTask(@PathVariable Long taskId,
                                                              @CurrentUser LoginUser loginUser) {
        videoTaskService.cancelTask(taskId, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("任务已取消"));
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<ProcessResult<Void>> retryTask(@PathVariable Long taskId,
                                                         @CurrentUser LoginUser loginUser) {
        VideoTask task = videoTaskService.getTaskAndCheckOwner(taskId, loginUser.getId());

        if (!task.getStatus().isRetryable()) {
            throw BusinessException.taskNotRetryable(taskId, task.getStatus().getDescription());
        }

        videoTaskService.resetForManualRetry(task);
        videoProcessProducer.sendRetryMessage(taskId);

        return ResponseEntity.ok(ProcessResult.success("已提交重试，任务将在数秒后重新执行"));
    }
}
