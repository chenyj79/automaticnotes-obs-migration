package com.black.asr.service;

import com.black.asr.vo.VideoVo;
import com.black.exception.BusinessException;
import com.black.asr.po.Video;
import com.black.asr.repository.VideoRepository;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.knowledge.enums.DraftStatus;
import com.black.knowledge.repository.KnowledgePointDraftRepository;
import com.black.knowledge.repository.KnowledgePointVideoRefRepository;
import com.black.knowledge.repository.VideoScoreRepository;
import com.black.util.ObsUtil;
import com.black.task.repository.VideoTaskRepository;
import com.black.task.service.VideoTaskService;
import com.black.task.enums.TaskStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.black.asr.vo.VideoUploadReqVo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

import com.black.asr.vo.VideoSearchResVo;
import com.black.asr.po.VideoSegment;
import java.time.LocalDateTime;

/**
 * 文件上传服务
 * 直接上传到华为云OBS，无需本地存储
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {
    private final ObsUtil obsUtil;

    private final VideoRepository videoRepository;
    private final VideoSegmentRepository videoSegmentRepository;
    private final VideoScoreRepository videoScoreRepository;
    private final KnowledgePointVideoRefRepository knowledgePointVideoRefRepository;
    private final KnowledgePointDraftRepository knowledgePointDraftRepository;
    private final VideoTaskRepository videoTaskRepository;
    private final VideoTaskService videoTaskService;

    @PersistenceContext
    private EntityManager em;

    @Value("${file.upload.maxSize:6442450944}") // 默认6GB
    private long maxFileSize;

    /**
     * 持久化前端直传的视频信息记录
     *
     * @param reqVo  前端提交的文件元数据
     * @param userId 用户ID
     * @return VideoVo
     */
    public VideoVo saveVideoRecord(VideoUploadReqVo reqVo, Long userId) {
        // 创建Video对象
        Video video = new Video();
        video.setUserId(userId);
        video.setFrameworkId(reqVo.getFrameworkId());
        video.setOriginalFileName(reqVo.getOriginalFileName());
        video.setDuration(reqVo.getDuration());
        video.setFileSize(reqVo.getFileSize());
        video.setOssUrl(reqVo.getOssUrl());
        video.setOssObjectName(reqVo.getOssObjectName());

        videoRepository.save(video);

        return video.toVo();
    }

    /**
     * 删除OBS文件
     *
     * @param video 视频信息，包含OBS信息
     * @return 是否删除成功
     */
    public boolean deleteFile(Video video) {
        if (video.getOssObjectName() != null) {
            return obsUtil.delete(video.getOssObjectName());
        }
        return false;
    }

    public Page<VideoVo> getMyVideo(Long userId, Pageable pageable) {
        return videoRepository.findByUserId(userId, pageable)
                .map(Video::toVo)
                .map(this::refreshSignedUrl);
    }

    /**
     * 获取指定框架下的视频列表
     */
    public List<VideoVo> getVideosByFrameworkId(Long frameworkId) {
        return videoRepository.findByFrameworkId(frameworkId).stream()
                .map(Video::toVo)
                .map(this::refreshSignedUrl)
                .collect(Collectors.toList());
    }

    public List<VideoSearchResVo> searchVideoSegments(Long userId, List<String> keywords,
            LocalDateTime startDate, LocalDateTime endDate) {
        if (keywords.isEmpty()) {
            return List.of();
        }

        // 动态构建 JPQL，多个关键词 AND 匹配
        StringBuilder jpql = new StringBuilder(
                "SELECT s FROM VideoSegment s, Video v WHERE s.videoId = v.id " +
                "AND v.userId = :userId AND v.uploadTime >= :start AND v.uploadTime <= :end");

        for (int i = 0; i < keywords.size(); i++) {
            jpql.append(" AND (s.rawText LIKE :kw").append(i)
                .append(" OR s.polishedText LIKE :kw").append(i).append(")");
        }
        jpql.append(" ORDER BY v.uploadTime DESC, s.startTime ASC");

        TypedQuery<VideoSegment> query = em.createQuery(jpql.toString(), VideoSegment.class);
        query.setParameter("userId", userId);
        query.setParameter("start", startDate);
        query.setParameter("end", endDate);
        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("kw" + i, "%" + keywords.get(i) + "%");
        }

        return query.getResultList().stream().map(segment -> {
            VideoSearchResVo vo = new VideoSearchResVo();
            videoRepository.findById(segment.getVideoId()).ifPresent(video -> vo.setVideo(video.toVo()));
            vo.setSegment(segment.toVo());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 根据视频ID和用户ID获取视频，验证归属权
     */
    public VideoVo getVideoById(Long videoId, Long userId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> BusinessException.videoNotFound(videoId));

        if (!video.getUserId().equals(userId)) {
            throw BusinessException.unauthorized();
        }

        return refreshSignedUrl(video.toVo());
    }

    /**
     * 刷新华为OBS视频的签名URL（每次请求时自动续期）
     */
    private VideoVo refreshSignedUrl(VideoVo vo) {
        if (vo.getOssObjectName() != null && !vo.getOssObjectName().isBlank()
                && obsUtil.getBucketName() != null && !obsUtil.getBucketName().isBlank()) {
            try {
                String freshUrl = obsUtil.generatePresignedGetUrl(vo.getOssObjectName(), 86400);
                vo.setOssUrl(freshUrl);
            } catch (Exception e) {
                log.warn("刷新OBS签名URL失败 - objectName: {}", vo.getOssObjectName(), e);
            }
        }
        return vo;
    }

    /**
     * 清理失败任务遗留的视频相关数据，避免前端列表出现失败条目。
     */
    @Transactional
    public void cleanupFailedVideoRecord(Long videoId) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video != null && video.getOssObjectName() != null && !video.getOssObjectName().isBlank()) {
            try {
                boolean deleted = deleteFile(video);
                if (!deleted) {
                    log.warn("删除OBS原视频失败（返回false）- videoId: {}, objectName: {}", videoId, video.getOssObjectName());
                }
            } catch (Exception e) {
                log.warn("删除OBS原视频异常 - videoId: {}, objectName: {}", videoId, video.getOssObjectName(), e);
            }
        }

        knowledgePointDraftRepository.deleteByVideoIdAndStatusIn(videoId,
                List.of(DraftStatus.PENDING, DraftStatus.REJECTED));
        knowledgePointVideoRefRepository.deleteByVideoId(videoId);
        videoSegmentRepository.deleteByVideoId(videoId);
        videoScoreRepository.deleteByVideoId(videoId);
        videoRepository.deleteById(videoId);
    }

    /**
     * 删除视频（包括OBS文件和所有相关数据库记录）
     *
     * @param videoId 视频ID
     * @param userId  用户ID（用于权限验证）
     */
    @Transactional
    public void deleteVideo(Long videoId, Long userId) {
        // 1. 验证视频存在且属于当前用户
        Video video = videoRepository.findByIdAndUserId(videoId, userId)
                .orElseThrow(() -> BusinessException.videoNotFound(videoId));
        log.info("开始删除视频 - videoId: {}, userId: {}, ossObjectName: {}",
                videoId, userId, video.getOssObjectName());

        // 1.5 取消与该视频相关的进行中任务
        try {
            videoTaskRepository.findByVideoId(videoId).ifPresent(task -> {
                if (task.getStatus() != TaskStatus.STATUS_SUCCESS &&
                        task.getStatus() != TaskStatus.STATUS_FAILED &&
                        task.getStatus() != TaskStatus.STATUS_CANCELLED) {
                    log.info("取消与视频相关的任务 - videoId: {}, taskId: {}, 当前状态: {}",
                            videoId, task.getId(), task.getStatus());
                    videoTaskService.updateStatus(task, TaskStatus.STATUS_CANCELLED);
                    log.info("相关任务已取消 - videoId: {}, taskId: {}", videoId, task.getId());
                }
            });
        } catch (Exception e) {
            log.error("取消相关任务异常 - videoId: {}", videoId, e);
        }

        try {
            knowledgePointDraftRepository.deleteByVideoIdAndStatusIn(videoId,
                    List.of(DraftStatus.PENDING, DraftStatus.REJECTED));
            log.info("未审核草稿删除成功 - videoId: {}", videoId);
        } catch (Exception e) {
            log.error("删除未审核草稿异常 - videoId: {}", videoId, e);
        }
        // 2. 删除OBS文件
        if (video.getOssObjectName() != null && !video.getOssObjectName().isBlank()) {
            try {
                boolean deleted = deleteFile(video);
                if (!deleted) {
                    log.warn("删除OBS视频失败（返回false）- videoId: {}, objectName: {}",
                            videoId, video.getOssObjectName());
                } else {
                    log.info("OBS视频删除成功 - videoId: {}, objectName: {}",
                            videoId, video.getOssObjectName());
                }
            } catch (Exception e) {
                log.error("删除OBS视频异常 - videoId: {}, objectName: {}",
                        videoId, video.getOssObjectName(), e);
                throw BusinessException.operationFailed("删除OBS文件失败: " + e.getMessage());
            }
        }
        // 3. 删除知识点视频引用
        try {
            knowledgePointVideoRefRepository.deleteByVideoId(videoId);
            log.info("知识点视频引用删除成功 - videoId: {}", videoId);
        } catch (Exception e) {
            log.error("删除知识点视频引用异常 - videoId: {}", videoId, e);
        }
        // 4. 删除视频片段
        try {
            videoSegmentRepository.deleteByVideoId(videoId);
            log.info("视频片段删除成功 - videoId: {}", videoId);
        } catch (Exception e) {
            log.error("删除视频片段异常 - videoId: {}", videoId, e);
        }
        // 5. 删除视频评分
        try {
            videoScoreRepository.deleteByVideoId(videoId);
            log.info("视频评分删除成功 - videoId: {}", videoId);
        } catch (Exception e) {
            log.error("删除视频评分异常 - videoId: {}", videoId, e);
        }
        // 6. 删除视频记录
        try {
            videoRepository.deleteById(videoId);
            log.info("视频记录删除成功 - videoId: {}", videoId);
        } catch (Exception e) {
            log.error("删除视频记录异常 - videoId: {}", videoId, e);
            throw BusinessException.operationFailed("删除视频记录失败: " + e.getMessage());
        }
        log.info("视频删除完成 - videoId: {}, userId: {}", videoId, userId);
    }

}
