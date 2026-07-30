package com.black.asr.repository;

import com.black.asr.po.TranscriptionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscriptionMetricsRepository extends JpaRepository<TranscriptionMetrics, Long> {

    List<TranscriptionMetrics> findByVideoId(Long videoId);

    List<TranscriptionMetrics> findAllByOrderByCreatedAtDesc();
}
