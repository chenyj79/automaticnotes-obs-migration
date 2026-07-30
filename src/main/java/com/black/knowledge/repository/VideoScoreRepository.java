package com.black.knowledge.repository;

import com.black.knowledge.po.VideoScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoScoreRepository extends JpaRepository<VideoScore, Long> {
    Optional<VideoScore> findByVideoId(Long videoId);

    void deleteByVideoId(Long videoId);

    List<VideoScore> findByVideoIdIn(List<Long> videoIds);
}
