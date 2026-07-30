package com.black.knowledge.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgePointVo {
    private Long id;
    private Long frameworkId;
    private String title;
    private String content;
    private String category;
    private List<PointVideoIndexingVo> videoIndexings; // All videos associated with this point
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
