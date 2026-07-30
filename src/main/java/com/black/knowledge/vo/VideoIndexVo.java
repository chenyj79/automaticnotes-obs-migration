package com.black.knowledge.vo;

import lombok.Data;

/**
 * 视频索引视图对象
 * 用于返回检索到的视频片段信息
 */
@Data
public class VideoIndexVo {
    private Long videoId;
    private String videoTitle;
    private String ossUrl;
    private Integer startTime; // 起始时间（秒）
    private Integer endTime; // 结束时间（秒）
    private String knowledgePointTitle;
    private String matchedText; // 匹配到的文本片段
}
