package com.black.knowledge.controller;

import com.black.constant.AppConstants;
import com.black.knowledge.service.VideoScoreService;
import com.black.knowledge.vo.VideoScoreVo;
import com.black.model.ProcessResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频评分控制器
 * 评分计算由主流程（processVideo）自动触发，此处仅提供查询接口
 */
@RestController
@RequestMapping("/api/video-score")
public class VideoScoreController {

    @Autowired
    private VideoScoreService videoScoreService;

    /**
     * 查询视频的所有评分
     */
    @GetMapping("/video/{videoId}")
    public ResponseEntity<ProcessResult<VideoScoreVo>> getVideoScores(
            @PathVariable Long videoId) {
        VideoScoreVo scores = videoScoreService.getScoreByVideoId(videoId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, scores));
    }
}
