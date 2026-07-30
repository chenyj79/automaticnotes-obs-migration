package com.black.knowledge.vo;

import com.black.asr.vo.VideoVo;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 视频处理结果视图对象
 * 用于展示增量笔记生成后的反馈，包括提取的知识点和评分
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProcessResultVo {
    /** 视频基本信息 */
    private VideoVo video;
    
    /** 此次提取到的知识点详情（包含Action：NEW/UPDATE） */
    private List<ExtractedKnowledgePoint> extractedPoints;
    
    /** 视频评分及说明 */
    private VideoScoreVo score;
}
