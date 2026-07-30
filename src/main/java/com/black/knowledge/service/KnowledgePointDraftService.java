package com.black.knowledge.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import cn.hutool.core.util.StrUtil;
import com.black.asr.po.VideoSegment;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.exception.BusinessException;
import com.black.knowledge.dto.KnowledgePointDraftRequest;
import com.black.knowledge.dto.ReviewDraftRequest;
import com.black.knowledge.enums.DraftStatus;
import com.black.knowledge.po.KnowledgePoint;
import com.black.knowledge.po.KnowledgePointDraft;
import com.black.knowledge.po.KnowledgePointVideoRef;
import com.black.knowledge.repository.KnowledgePointDraftRepository;
import com.black.knowledge.repository.KnowledgePointVideoRefRepository;
import com.black.knowledge.repository.KnowledgePointRepository;
import com.black.knowledge.service.KnowledgeRelationService;
import com.black.knowledge.vo.KnowledgePointDraftVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识点草稿服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePointDraftService {

    private final KnowledgePointDraftRepository draftRepository;

    @Autowired
    @Lazy
    private KnowledgePointService knowledgePointService;

    private final KnowledgePointRepository pointRepository;
    private final KnowledgePointVideoRefRepository videoRefRepository;
    private final KnowledgeRelationService relationService;
    private final VideoSegmentRepository segmentRepository;
    private final AiService aiService;

    /**
     * 保存知识点草稿
     */
    @Transactional
    public KnowledgePointDraftVo saveDraft(KnowledgePointDraftRequest request) {
        KnowledgePointDraft draft = new KnowledgePointDraft();
        draft.setVideoId(request.getVideoId());
        draft.setFrameworkId(request.getFrameworkId());
        draft.setTitle(request.getTitle());
        draft.setContent(request.getContent());
        draft.setCategory(request.getCategory());
        draft.setAiSuggestion(request.getAiSuggestion());
        draft.setStatus(DraftStatus.PENDING);

        KnowledgePointDraft saved = draftRepository.save(draft);
        log.info("保存知识点草稿成功 - id: {}", saved.getId());
        return saved.toVo();
    }

    /**
     * 批量保存草稿
     */
    @Transactional
    public void saveDrafts(List<KnowledgePointDraft> drafts) {
        if (!drafts.isEmpty()) {
            draftRepository.saveAll(drafts);
            log.info("批量保存知识点草稿成功 - 数量: {}", drafts.size());
        }
    }

    /**
     * 审核草稿
     */
    @Transactional
    public void reviewDraft(Long draftId, ReviewDraftRequest request) {
        KnowledgePointDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> BusinessException.draftNotFound(draftId));

        if (!DraftStatus.PENDING.equals(draft.getStatus())) {
            throw BusinessException.draftAlreadyReviewed(draftId);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            draft.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            draft.setContent(request.getContent());
        }
        if (request.getCategory() != null) {
            draft.setCategory(request.getCategory());
        }

        if (request.getApproved()) {
            KnowledgePoint knowledgePoint = null;

            if (draft.getAction() != null) {
                switch (draft.getAction()) {
                    case NEW:
                        knowledgePoint = createNewKnowledgePoint(draft);
                        break;
                    case UPDATE:
                        knowledgePoint = updateExistingKnowledgePoint(draft);
                        break;
                    case REDUNDANT:
                        log.info("草稿审核通过，知识点冗余，跳过创建 - draftId: {}", draftId);
                        if (draft.getExistingPointId() != null) {
                            knowledgePoint = pointRepository.findById(draft.getExistingPointId()).orElse(null);
                        }
                        break;
                }
            } else {
                knowledgePoint = createNewKnowledgePoint(draft);
            }

            if (knowledgePoint != null) {
                draft.setExistingPointId(knowledgePoint.getId());
                if (draft.getTimestamps() != null && !draft.getTimestamps().isEmpty()) {
                    Optional<KnowledgePointVideoRef> existingRefOpt = videoRefRepository
                            .findByKnowledgePointIdAndVideoId(knowledgePoint.getId(), draft.getVideoId());
                    if (existingRefOpt.isPresent()) {
                        KnowledgePointVideoRef existingRef = existingRefOpt.get();
                        // 合并时间戳
                        String existingTimestamps = existingRef.getTimestamps();
                        String newTimestamps = draft.getTimestamps();

                        if (existingTimestamps == null || existingTimestamps.isEmpty()
                                || "[]".equals(existingTimestamps)) {
                            existingRef.setTimestamps(newTimestamps);
                        } else if (newTimestamps != null && !newTimestamps.isEmpty() && !"[]".equals(newTimestamps)) {
                            // 简单的 JSON 数组合并：移除前一个的 ']' 和后一个的 '['，用 ',' 连接
                            if (existingTimestamps.endsWith("]") && newTimestamps.startsWith("[")) {
                                String merged = existingTimestamps.substring(0, existingTimestamps.length() - 1)
                                        + ","
                                        + newTimestamps.substring(1);
                                existingRef.setTimestamps(merged);
                            }
                        }

                        // TODO: 考虑是否需要合并 segmentSummary，目前先保留旧的或者由后续逻辑触发生成
                        generateAndSaveSegmentSummary(knowledgePoint, existingRef);

                        videoRefRepository.save(existingRef);
                        log.info("草稿审核通过，合并现有视频引用时间戳 - draftId: {}, knowledgePointId: {}", draftId,
                                knowledgePoint.getId());
                    } else {
                        KnowledgePointVideoRef ref = new KnowledgePointVideoRef();
                        ref.setKnowledgePointId(knowledgePoint.getId());
                        ref.setVideoId(draft.getVideoId());
                        ref.setTimestamps(draft.getTimestamps());

                        // TODO: 触发AI生成片段总结 (可以在保存时异步生成，或者在此处同步生成)
                        generateAndSaveSegmentSummary(knowledgePoint, ref);

                        videoRefRepository.save(ref);
                        log.info("草稿审核通过，保存新视频引用 - draftId: {}, knowledgePointId: {}", draftId, knowledgePoint.getId());
                    }
                }
            }

            draft.setStatus(DraftStatus.APPROVED);
        } else {
            draft.setStatus(DraftStatus.REJECTED);
            log.info("草稿审核拒绝 - draftId: {}", draftId);
        }

        draft.setReviewComment(request.getComment());
        draftRepository.save(draft);
    }

    /**
     * 异步生成关联视频片段的AI概述，每个片段独立生成并保存为JSON数组
     */
    @Async
    public void generateAndSaveSegmentSummary(KnowledgePoint knowledgePoint, KnowledgePointVideoRef ref) {
        if (ref.getTimestamps() == null || ref.getTimestamps().isEmpty() || "[]".equals(ref.getTimestamps())) {
            return;
        }
        try {
            JSONArray timestamps = JSON.parseArray(ref.getTimestamps());
            List<VideoSegment> allSegments = segmentRepository.findByVideoId(ref.getVideoId());
            JSONArray segmentSummaries = new JSONArray();

            for (int i = 0; i < timestamps.size(); i++) {
                JSONObject ts = timestamps.getJSONObject(i);
                Integer start = ts.getInteger("start");
                Integer end = ts.getInteger("end");
                if (start == null || end == null)
                    continue;

                StringBuilder matchedText = new StringBuilder();
                for (VideoSegment seg : allSegments) {
                    if (seg.getStartTime() != null && seg.getEndTime() != null
                            && seg.getStartTime() < end
                            && seg.getEndTime() > start) {
                        if (!matchedText.isEmpty()) {
                            matchedText.append("\n");
                        }
                        matchedText.append(seg.getPolishedText() != null ? seg.getPolishedText() : seg.getRawText());
                    }
                }

                String summary = "暂无概述";
                if (!matchedText.isEmpty()) {
                    summary = aiService.generateSegmentSummary(knowledgePoint.getTitle(), matchedText.toString());
                }

                JSONObject summaryObj = new JSONObject();
                summaryObj.put("start", start);
                summaryObj.put("end", end);
                summaryObj.put("summary", summary);
                segmentSummaries.add(summaryObj);
            }

            if (!segmentSummaries.isEmpty()) {
                ref.setSegmentSummary(segmentSummaries.toJSONString());
                videoRefRepository.save(ref);
            }
        } catch (Exception e) {
            log.error("异步生成片段概述失败 - knowledgePointId: {}, videoId: {}", knowledgePoint.getId(), ref.getVideoId(), e);
        }
    }

    /**
     * 创建新知识点
     */
    private KnowledgePoint createNewKnowledgePoint(KnowledgePointDraft draft) {
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        knowledgePoint.setFrameworkId(draft.getFrameworkId());
        knowledgePoint.setTitle(draft.getTitle());
        knowledgePoint.setContent(draft.getContent());
        knowledgePoint.setCategory(draft.getCategory());
        return knowledgePointService.saveKnowledgePoint(knowledgePoint);
    }

    /**
     * 更新现有知识点
     */
    private KnowledgePoint updateExistingKnowledgePoint(KnowledgePointDraft draft) {
        if (draft.getExistingPointId() == null) {
            // 没有现有知识点ID，降级为新建
            return createNewKnowledgePoint(draft);
        }

        KnowledgePoint existingPoint = pointRepository.findById(draft.getExistingPointId())
                .orElseThrow(() -> BusinessException.knowledgePointNotFound(draft.getExistingPointId()));

        // 更新分类
        if (StrUtil.isBlank(existingPoint.getCategory()) && StrUtil.isNotBlank(draft.getCategory())) {
            existingPoint.setCategory(draft.getCategory());
        }

        // 更新内容
        if (draft.getContent() != null) {
            String existingContent = existingPoint.getContent() != null ? existingPoint.getContent() : "";
            existingPoint.setContent(existingContent + "\n\n---\n\n### 【补充】\n" + draft.getContent());
        }

        // 保存更新
        existingPoint = pointRepository.save(existingPoint);

        // 更新向量存储
        knowledgePointService.removeFromVectorStore(existingPoint.getId());
        knowledgePointService.addToVectorStore(existingPoint);

        return existingPoint;
    }

    /**
     * 获取待审核的草稿
     */
    public List<KnowledgePointDraftVo> getPendingDrafts(Long frameworkId) {
        List<KnowledgePointDraft> drafts = draftRepository.findByFrameworkIdAndStatusOrderByCreateTimeDesc(frameworkId,
                DraftStatus.PENDING);
        return drafts.stream().map(KnowledgePointDraft::toVo).toList();
    }

    public List<KnowledgePointDraftVo> getAllDrafts(Long frameworkId) {
        if (frameworkId == null || frameworkId == 0 || frameworkId == -1) {
            List<KnowledgePointDraft> drafts = draftRepository.findAllByOrderByCreateTimeDesc();
            return drafts.stream().map(KnowledgePointDraft::toVo).toList();
        }
        List<KnowledgePointDraft> drafts = draftRepository.findByFrameworkIdOrderByCreateTimeDesc(frameworkId);
        return drafts.stream().map(KnowledgePointDraft::toVo).toList();
    }

    /**
     * 获取视频相关的草稿
     */
    public List<KnowledgePointDraftVo> getVideoDrafts(Long videoId) {
        List<KnowledgePointDraft> drafts = draftRepository.findByVideoIdOrderByCreateTimeDesc(videoId);
        return drafts.stream().map(KnowledgePointDraft::toVo).toList();
    }

    /**
     * 获取草稿详情
     */
    public KnowledgePointDraftVo getDraftById(Long draftId) {
        KnowledgePointDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> BusinessException.draftNotFound(draftId));
        return draft.toVo();
    }

    /**
     * 删除草稿
     */
    @Transactional
    public void deleteDraft(Long draftId) {
        draftRepository.deleteById(draftId);
        log.info("删除草稿成功 - id: {}", draftId);
    }

    /**
     * 清理视频相关的草稿
     */
    @Transactional
    public void cleanupVideoDrafts(Long videoId) {
        draftRepository.deleteByVideoId(videoId);
        log.info("清理视频草稿成功 - videoId: {}", videoId);
    }
}
