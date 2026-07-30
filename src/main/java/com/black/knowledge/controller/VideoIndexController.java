package com.black.knowledge.controller;

import com.black.constant.AppConstants;
import com.black.knowledge.service.VideoIndexService;
import com.black.knowledge.vo.VideoIndexVo;
import com.black.model.ProcessResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频索引控制器
 * 根据关键词或知识点检索相关视频片段
 */
@RestController
@RequestMapping("/api/video-index")
public class VideoIndexController {

    @Autowired
    private VideoIndexService videoIndexService;

    /**
     * 关键词搜索视频片段
     *
     * @param keyword     搜索关键词
     * @param frameworkId 可选，限定在某个框架内搜索
     */
    @GetMapping("/search")
    public ResponseEntity<ProcessResult<List<VideoIndexVo>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long frameworkId) {
        List<VideoIndexVo> results = videoIndexService.searchByKeyword(keyword, frameworkId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, results));
    }
}
