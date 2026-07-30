package com.black.asr.repository;

import com.black.asr.po.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 视频数据访问层
 */
public interface VideoRepository extends JpaRepository<Video, Long> {

    /**
     * 根据用户ID分页查询视频列表
     */
    Page<Video> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据视频ID和用户ID查询（防止越权访问）
     */
    Optional<Video> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    List<Video> findByUserId(Long userId);

    /**
     * 根据框架ID查询视频列表
     */
    List<Video> findByFrameworkId(Long frameworkId);
}
