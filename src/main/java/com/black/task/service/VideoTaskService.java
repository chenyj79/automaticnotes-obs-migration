package com.black.task.service;

import com.black.task.enums.TaskStatus;
import com.black.exception.BusinessException;
import com.black.task.po.VideoTask;
import com.black.task.repository.VideoTaskRepository;
import com.black.asr.repository.VideoRepository;
import com.black.task.vo.PendingTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTaskService {

    private final VideoTaskRepository videoTaskRepository;
    private final VideoRepository videoRepository;
    private final SseTaskService sseTaskService;

    public List<PendingTaskVo> getPendingTasksByFrameworkId(Long frameworkId, Long userId) {
        List<TaskStatus> pendingStatuses = Arrays.asList(
                TaskStatus.STATUS_PENDING,
                TaskStatus.STATUS_TRANSCRIPTING,
                TaskStatus.STATUS_EXTRACTING,
                TaskStatus.STATUS_SCORING
        );
        List<VideoTask> tasks = videoTaskRepository.findPendingOrUnviewedTasks(frameworkId, userId, pendingStatuses);
        
        return tasks.stream().map(task -> {
            String filename = videoRepository.findById(task.getVideoId())
                    .map(v -> v.getOriginalFileName() != null && !v.getOriginalFileName().isEmpty() ? v.getOriginalFileName() : "未知视频")
                    .orElse("未知视频");
            return PendingTaskVo.builder()
                    .id(task.getId())
                    .videoId(task.getVideoId())
                    .filename(filename)
                    .status(task.getStatus().name())
                    .build();
        }).collect(Collectors.toList());
    }

    public VideoTask getTaskById(Long taskId) {
        return videoTaskRepository.findById(taskId)
                .orElseThrow(() -> BusinessException.taskNotFound(taskId));
    }

    public VideoTask getTaskAndCheckOwner(Long taskId, Long userId) {
        VideoTask task = getTaskById(taskId);
        
        if (!task.getUserId().equals(userId)) {
            throw BusinessException.taskAccessDenied();
        }
        return task;
    }

    public VideoTask getTaskByVideoId(Long videoId) {
        return videoTaskRepository.findByVideoId(videoId)
                .orElseThrow(() -> BusinessException.taskNotFound(videoId));
    }

    @Transactional
    public VideoTask createTask(Long videoId, Long userId, Long frameworkId) {
        VideoTask task = VideoTask.builder()
                .videoId(videoId)
                .userId(userId)
                .frameworkId(frameworkId)
                .status(TaskStatus.STATUS_PENDING)
                .build();
        return videoTaskRepository.save(task);
    }

    @Transactional
    public void updateStatus(VideoTask task, TaskStatus status) {
        task.setStatus(status);
        videoTaskRepository.save(task);
        sseTaskService.sendTaskStatusUpdate(task.getId(), status);
    }

    @Transactional
    public void completeTask(VideoTask task, String resultJson) {
        task.setStatus(TaskStatus.STATUS_SUCCESS);
        task.setResultJson(resultJson);
        videoTaskRepository.save(task);
        sseTaskService.sendTaskStatusUpdate(task.getId(), TaskStatus.STATUS_SUCCESS);
    }

    @Transactional
    public void markReportViewed(Long taskId, Long userId) {
        VideoTask task = getTaskAndCheckOwner(taskId, userId);
        task.setIsReportViewed(true);
        videoTaskRepository.save(task);
    }

    @Transactional
    public void cancelTask(Long taskId, Long userId) {
        VideoTask task = getTaskAndCheckOwner(taskId, userId);
        
        if (task.getStatus().isTerminal()) {
            throw BusinessException.operationFailed("任务已完成或已取消，无法取消");
        }
        
        log.info("取消任务 - taskId: {}, userId: {}, 当前状态: {}", 
                taskId, userId, task.getStatus());
        
        task.setStatus(TaskStatus.STATUS_CANCELLED);
        videoTaskRepository.save(task);
        sseTaskService.sendTaskStatusUpdate(taskId, TaskStatus.STATUS_CANCELLED);
        
        log.info("任务已取消 - taskId: {}", taskId);
    }

    public boolean isTaskCancelled(Long taskId) {
        try {
            VideoTask task = getTaskById(taskId);
            return task.getStatus() == TaskStatus.STATUS_CANCELLED;
        } catch (Exception e) {
            log.error("检查任务取消状态异常 - taskId: {}", taskId, e);
            return false;
        }
    }

    @Transactional
    public VideoTask incrementAndGetRetryCount(Long taskId) {
        videoTaskRepository.incrementRetryCount(taskId);
        return getTaskById(taskId);
    }

    @Transactional
    public void recordFailureReason(VideoTask task, String reason) {
        if (reason != null && reason.length() > 2000) {
            reason = reason.substring(0, 2000) + "...";
        }
        task.setFailureReason(reason);
        videoTaskRepository.save(task);
    }

    @Transactional
    public void markFinalFailed(VideoTask task, String reason) {
        task.setStatus(TaskStatus.STATUS_FAILED);
        if (reason != null && reason.length() > 2000) {
            reason = reason.substring(0, 2000) + "...";
        }
        task.setFailureReason(reason);
        videoTaskRepository.save(task);
        sseTaskService.sendTaskStatusUpdate(task.getId(), TaskStatus.STATUS_FAILED);
    }

    @Transactional
    public void resetForManualRetry(VideoTask task) {
        task.setRetryCount(0);
        task.setFailureReason(null);
        videoTaskRepository.save(task);
    }

    @Transactional
    public VideoTask save(VideoTask task) {
        return videoTaskRepository.save(task);
    }
}
