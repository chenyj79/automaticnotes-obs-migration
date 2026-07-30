package com.black.asr.po;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ASR A/B 基准测试结果实体
 * 记录同一视频分别使用两个 ASR 引擎转写的结果及 CER/WER 对比指标
 * 支持后续上传人工参考文本计算绝对准确率
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrBenchmarkResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的视频 ID */
    @Column(nullable = false)
    private Long videoId;

    // ===== 阿里云通义听悟引擎结果 =====

    private Boolean aliyunSuccess;
    private Long aliyunDurationMs;
    private Integer aliyunSegmentCount;
    private Integer aliyunCharCount;
    @Column(columnDefinition = "LONGTEXT")
    private String aliyunFullText;
    @Column(columnDefinition = "TEXT")
    private String aliyunErrorMessage;

    // ===== 本地 Whisper 引擎结果 =====

    private Boolean whisperSuccess;
    private Long whisperDurationMs;
    private Integer whisperSegmentCount;
    private Integer whisperCharCount;
    @Column(columnDefinition = "LONGTEXT")
    private String whisperFullText;
    @Column(columnDefinition = "TEXT")
    private String whisperErrorMessage;

    // ===== 双引擎互比指标 =====

    /** 两引擎输出之间的 CER */
    private Double crossEngineCer;
    /** 两引擎输出之间的 WER */
    private Double crossEngineWer;
    /** 两引擎输出之间的 标点 F1 */
    private Double crossEnginePuncF1;

    // ===== 基于人工参考文本的绝对指标 =====

    /** 人工参考文本（可后续上传） */
    @Column(columnDefinition = "LONGTEXT")
    private String referenceText;

    /** 阿里云 vs 参考文本 CER */
    private Double aliyunReferenceCer;
    /** 阿里云 vs 参考文本 WER */
    private Double aliyunReferenceWer;
    /** 阿里云 vs 参考文本 标点 F1 */
    private Double aliyunReferencePuncF1;

    /** Whisper vs 参考文本 CER */
    private Double whisperReferenceCer;
    /** Whisper vs 参考文本 WER */
    private Double whisperReferenceWer;
    /** Whisper vs 参考文本 标点 F1 */
    private Double whisperReferencePuncF1;

    /** 孤立标点恢复评估 F1（排除 ASR 干扰，直接基于参考文本剥离标点后恢复） */
    private Double isolatedPuncF1;

    // ===== 视频元数据（方便实验分析） =====

    /** 视频时长（秒） */
    private Integer videoDuration;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 视频名称 */
    private String videoName;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
