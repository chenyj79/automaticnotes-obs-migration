package com.black.asr.vo;

import com.black.asr.po.VideoSegment;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VideoSegmentVo {
    private Long id;
    private Long videoId;
    private Integer startTime;
    private Integer endTime;
    private String rawText;
    private String polishedText;

    public VideoSegment toPo() {
        VideoSegment videoSegment = new VideoSegment();
        videoSegment.setId(id);
        videoSegment.setVideoId(videoId);
        videoSegment.setStartTime(startTime);
        videoSegment.setEndTime(endTime);
        videoSegment.setRawText(rawText);
        videoSegment.setPolishedText(polishedText);
        return videoSegment;
    }
}
