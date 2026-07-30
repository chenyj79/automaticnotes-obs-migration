package com.black.asr.po;

import com.black.asr.vo.VideoVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 视频信息实体类
 */
@Data
@Entity
@NoArgsConstructor
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "user_id")
    private Long userId;

    @Basic
    @Column(name = "framework_id")
    private Long frameworkId;

    @Basic
    @Column(name = "original_file_name")
    private String originalFileName;
    @Basic
    @Column(name = "duration")
    private Integer duration;
    @Basic
    @Column(name = "oss_url")
    private String ossUrl; // OBS访问URL
    @Basic
    @Column(name = "oss_object_name")
    private String ossObjectName; // OBS对象名称
    @Basic
    @Column(name = "file_size")
    private Long fileSize;

    @Basic
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary; // AI生成的视频简介

    @Basic
    @Column(name = "upload_time")
    @CreationTimestamp
    private LocalDateTime uploadTime;

    public VideoVo toVo() {
        VideoVo videoVo = new VideoVo();
        videoVo.setId(id);
        videoVo.setUserId(userId);
        videoVo.setFrameworkId(frameworkId);
        videoVo.setOriginalFileName(originalFileName);
        videoVo.setDuration(duration);
        videoVo.setOssUrl(ossUrl);
        videoVo.setOssObjectName(ossObjectName);
        videoVo.setFileSize(fileSize);
        videoVo.setSummary(summary);
        videoVo.setUploadTime(uploadTime);
        return videoVo;
    }
}
