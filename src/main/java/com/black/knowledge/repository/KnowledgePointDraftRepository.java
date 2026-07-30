package com.black.knowledge.repository;

import com.black.knowledge.enums.DraftStatus;
import com.black.knowledge.po.KnowledgePointDraft;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;

/**
 * 知识点草稿仓库
 */
public interface KnowledgePointDraftRepository extends JpaRepository<KnowledgePointDraft, Long> {

    /**
     * 根据框架ID和状态查询草稿，按创建时间降序
     */
    List<KnowledgePointDraft> findByFrameworkIdAndStatusOrderByCreateTimeDesc(Long frameworkId, DraftStatus status);

    /**
     * 根据视频ID查询草稿
     */
    List<KnowledgePointDraft> findByVideoIdOrderByCreateTimeDesc(Long videoId);

    /**
     * 查询所有草稿
     */
    List<KnowledgePointDraft> findAllByOrderByCreateTimeDesc();

    /**
     * 根据视频ID和状态查询草稿，按创建时间降序
     */
    List<KnowledgePointDraft> findByVideoIdAndStatusOrderByCreateTimeDesc(Long videoId, DraftStatus status);

    /**
     * 根据框架ID查询所有草稿
     */
    List<KnowledgePointDraft> findByFrameworkIdOrderByCreateTimeDesc(Long frameworkId);

    /**
     * 删除指定框架的所有草稿
     */
    void deleteByFrameworkId(Long frameworkId);

    /**
     * 删除指定视频的所有草稿
     */
    void deleteByVideoId(Long videoId);

    void deleteByVideoIdAndStatusIn(Long videoId, List<DraftStatus> statuses);
}
