package com.black.asr.po;

import com.black.asr.vo.VideoSegmentVo;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频转写片段实体类
 */
@Data
@Entity
@NoArgsConstructor
public class VideoSegment {
    @Id
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "video_id")
    private Long videoId;

    @Basic
    @Column(name = "start_time")
    private Integer startTime;

    @Basic
    @Column(name = "end_time")
    private Integer endTime;

    @Basic
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Basic
    @Column(name = "polished_text", columnDefinition = "TEXT")
    private String polishedText;

    public VideoSegmentVo toVo() {
        VideoSegmentVo videoSegmentVo = new VideoSegmentVo();
        videoSegmentVo.setId(id);
        videoSegmentVo.setVideoId(videoId);
        videoSegmentVo.setStartTime(startTime);
        videoSegmentVo.setEndTime(endTime);
        videoSegmentVo.setRawText(rawText);
        videoSegmentVo.setPolishedText(polishedText);
        return videoSegmentVo;
    }
}