package com.black.asr.vo;

import com.black.asr.po.Video;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class VideoVo {
    private Long id;
    private Long userId;
    private Long frameworkId;
    private String originalFileName;
    private Integer duration;
    private String ossUrl;
    private String ossObjectName;
    private Long fileSize;
    private String summary;
    private LocalDateTime uploadTime;

    public Video toPo() {
        Video video = new Video();
        video.setId(id);
        video.setUserId(userId);
        video.setFrameworkId(frameworkId);
        video.setOriginalFileName(originalFileName);
        video.setDuration(duration);
        video.setOssUrl(ossUrl);
        video.setOssObjectName(ossObjectName);
        video.setFileSize(fileSize);
        video.setSummary(summary);
        video.setUploadTime(uploadTime);
        return video;
    }
}
