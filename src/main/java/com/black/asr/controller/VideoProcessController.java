package com.black.asr.controller;

import com.black.asr.service.VideoService;
import com.black.asr.service.VideoSegmentService;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.asr.po.VideoSegment;
import com.black.constant.AppConstants;
import com.black.knowledge.annotation.CheckFrameworkOwner;
import com.black.model.ProcessResult;
import com.black.user.annotation.CurrentUser;
import com.black.user.security.LoginUser;
import com.black.task.po.VideoTask;
import com.black.task.service.VideoTaskService;
import com.black.task.mq.producer.VideoProcessProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.black.asr.vo.VideoUploadReqVo;

import java.util.List;

import com.black.asr.vo.VideoSearchResVo;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 视频处理控制器
 * 提供RESTful API接口，使用阿里云通义听悟进行视频转写
 */
@RestController
@RequestMapping("/api/video")
public class VideoProcessController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoSegmentService videoSegmentService;

    @Autowired
    private VideoTaskService videoTaskService;

    @Autowired
    private VideoProcessProducer videoProcessProducer;

    /**
     * 保存前端直传视频的记录（文件已通过预签名URL上传至华为云OBS）
     */
    @PostMapping("/upload")
    @CheckFrameworkOwner("#reqVo.frameworkId")
    public ResponseEntity<ProcessResult<VideoVo>> uploadVideo(
            @RequestBody @Valid VideoUploadReqVo reqVo,
            @CurrentUser LoginUser loginUser) {
        VideoVo video = videoService.saveVideoRecord(reqVo, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPLOAD_SUCCESS, video));
    }

    /**
     * 发起视频处理任务(直接读取已经上传的视频)
     */
    @PostMapping("/process")
    @CheckFrameworkOwner("#reqVo.frameworkId")
    public ResponseEntity<ProcessResult<Long>> processVideo(
            @RequestBody @Valid VideoUploadReqVo reqVo,
            @CurrentUser LoginUser loginUser) {
        // 1. 保存前端传进来的OBS视频记录
        VideoVo video = videoService.saveVideoRecord(reqVo, loginUser.getId());

        // 2. 创建异步处理任务
        VideoTask task = videoTaskService.createTask(video.getId(), loginUser.getId(), reqVo.getFrameworkId());

        // 3. 发送 RocketMQ 消息触发异步处理
        videoProcessProducer.sendProcessMessage(task.getId());

        return ResponseEntity.ok(ProcessResult.success("视频记录保存成功，已提交后台分析任务", task.getId()));
    }

    /**
     * 获取当前用的视频列表（分页）
     */
    @GetMapping("/my")
    public ResponseEntity<ProcessResult<Page<VideoVo>>> getMyVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoginUser loginUser) {

        Long userId = loginUser.getId();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "uploadTime"));

        Page<VideoVo> videoPage = videoService.getMyVideo(userId, pageable);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.GET_VIDEO_LIST_SUCCESS, videoPage));
    }

    /**
     * 获取视频明细
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessResult<VideoVo>> getVideoDetail(
            @PathVariable Long id,
            @CurrentUser LoginUser loginUser) {
        VideoVo video = videoService.getVideoById(id, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("获取成功", video));
    }

    /**
     * 全局全文检索视频片段（支持多关键词，逗号/空格分隔，AND逻辑）
     */
    @GetMapping("/search-segments")
    public ResponseEntity<ProcessResult<List<VideoSearchResVo>>> searchSegments(
            @RequestParam String keywords,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @CurrentUser LoginUser loginUser) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime start = LocalDateTime.parse(startDate + " 00:00:00", formatter);
        LocalDateTime end = LocalDateTime.parse(endDate + " 23:59:59", formatter);

        // 按逗号或空格分割关键词，过滤空字符串
        String[] keywordArray = keywords.split("[,，\\s]+");
        List<String> keywordList = java.util.Arrays.stream(keywordArray)
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(java.util.stream.Collectors.toList());

        List<VideoSearchResVo> results = videoService.searchVideoSegments(
                loginUser.getId(), keywordList, start, end);
        return ResponseEntity.ok(ProcessResult.success("检索成功", results));
    }

    /**
     * 根据视频ID获取所有转写片段
     */
    @GetMapping("/{id}/segments")
    public ResponseEntity<ProcessResult<List<VideoSegmentVo>>> getVideoSegments(
            @PathVariable Long id,
            @CurrentUser LoginUser userDetails) {
        videoService.getVideoById(id, userDetails.getId());// 验证视频属于当前用户
        List<VideoSegmentVo> segments = videoSegmentService.findByVideoId(id)
                .stream().map(VideoSegment::toVo).toList();
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, segments));
    }

    /**
     * 获取指定框架下的视频列表
     */
    @GetMapping("/framework/{frameworkId}")
    @CheckFrameworkOwner("#frameworkId")
    public ResponseEntity<ProcessResult<List<VideoVo>>> getFrameworkVideos(
            @PathVariable Long frameworkId) {
        List<VideoVo> videos = videoService.getVideosByFrameworkId(frameworkId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, videos));
    }

    /**
     * 删除视频（包括OBS文件和所有相关数据库记录）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ProcessResult<Void>> deleteVideo(
            @PathVariable Long id,
            @CurrentUser LoginUser loginUser) {
        videoService.deleteVideo(id, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success("视频删除成功", null));
    }
}