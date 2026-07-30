package com.black.asr.po;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 转写指标记录实体
 * 每次 ASR 引擎调用（无论成功或失败）都会产生一条记录
 * 用于统计各引擎的性能表现
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的视频 ID */
    private Long videoId;

    /** 关联的任务 ID（可为空，A/B 测试时无任务） */
    private Long taskId;

    /** 使用的 ASR 引擎: ALIYUN_TINGWU / LOCAL_WHISPER */
    @Column(nullable = false)
    private String asrEngine;

    /** 是否转写成功 */
    @Column(nullable = false)
    private Boolean success;

    /** 转写耗时（毫秒） */
    private Long durationMs;

    /** 产出片段数 */
    private Integer segmentCount;

    /** 转写文本总字符数 */
    private Integer totalCharCount;

    /** 视频时长（秒） */
    private Integer videoDuration;

    /** 视频文件大小（字节） */
    private Long fileSize;

    /** 失败时的错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 是否为降级调用（阿里云失败后降级到 Whisper） */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isFallback = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
