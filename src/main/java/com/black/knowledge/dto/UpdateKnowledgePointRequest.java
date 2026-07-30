package com.black.knowledge.dto;

import com.black.knowledge.enums.RelationType;
import lombok.Data;

import java.util.List;

/**
 * 更新知识点请求
 */
@Data
public class UpdateKnowledgePointRequest {
    private String title;
    private String content;
    private String category;
    private List<RelationUpdate> relationUpdates;
    private List<VideoUpdate> videoUpdates;

    @Data
    public static class RelationUpdate {
        private String action; // ADD, DELETE
        private Long sourcePointId;
        private Long targetPointId;
        private RelationType relationType;
    }

    @Data
    public static class VideoUpdate {
        private String action; // SET, DELETE
        private Long videoId;
        private List<AddKnowledgePointRequest.VideoIndexing> segments;
    }
}
