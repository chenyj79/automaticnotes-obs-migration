package com.black.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointVideoIndexingVo {
    private Long videoId;
    private String videoTitle;
    private String timestamps; // JSON format: [{"start":10,"end":30}, ...]
    private String videoSummary; // AI summarized video overview
}
