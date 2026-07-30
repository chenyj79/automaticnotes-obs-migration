package com.black.asr.repository;

import com.black.asr.po.AsrBenchmarkResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AsrBenchmarkRepository extends JpaRepository<AsrBenchmarkResult, Long> {

    Optional<AsrBenchmarkResult> findByVideoId(Long videoId);

    List<AsrBenchmarkResult> findAllByOrderByCreatedAtDesc();
}
