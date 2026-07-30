package com.black.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 从视频提取知识点请求
 */
@Data
public class ExtractKnowledgeRequest {
    @NotNull(message = "视频ID不能为空")
    private Long videoId;

    @NotNull(message = "知识框架ID不能为空")
    private Long frameworkId;
}
