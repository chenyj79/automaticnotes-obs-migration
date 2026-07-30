package com.black.task.po;

import com.black.task.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long videoId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long frameworkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isReportViewed = false;

    // ===== 重试追踪字段 =====

    /** 当前已执行次数（含首次），默认 0 */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /** 最大允许执行次数，默认 3 */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    /** 最近一次失败原因 */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    // ===== ASR 指标字段 =====

    /** 实际使用的 ASR 引擎名称 (ALIYUN_TINGWU / LOCAL_WHISPER) */
    private String asrEngine;

    /** 转写阶段耗时（毫秒） */
    private Long transcriptionDurationMs;

    // ===== 断点续跑中间结果 =====

    /** 知识提取阶段的输出快照（JSON），用于评分阶段断点续跑时恢复 */
    @Column(columnDefinition = "LONGTEXT")
    private String extractedPointsJson;

    @Column
    @CreationTimestamp
    private LocalDateTime createTime;

    @Column
    @UpdateTimestamp
    private LocalDateTime updateTime;
}
