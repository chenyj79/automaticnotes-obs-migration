package com.black.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 手动添加知识点请求
 */
@Data
public class AddKnowledgePointRequest {
    @NotNull(message = "知识框架ID不能为空")
    private Long frameworkId;

    @NotBlank(message = "知识点标题不能为空")
    private String title;

    private String content;

    /** 知识点类别（如：定义、公式、例题、方法） */
    private String category;

    /** 关联的视频列表（可选） */
    private List<VideoRefRequest> videoRefs;

    @Data
    public static class VideoRefRequest {
        private Long videoId;
        private List<VideoIndexing> segments;
    }

    @Data
    public static class VideoIndexing {
        private Integer start;
        private Integer end;
    }
}
