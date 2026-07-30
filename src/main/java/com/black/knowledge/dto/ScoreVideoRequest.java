package com.black.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 视频评分请求
 */
@Data
public class ScoreVideoRequest {
    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    @NotNull(message = "知识框架ID不能为空")
    private Long frameworkId;
}
