package com.black.asr.repository;

import com.black.asr.po.VideoSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoSegmentRepository extends JpaRepository<VideoSegment, Long> {
       List<VideoSegment> findByVideoId(Long videoId);

       void deleteByVideoId(Long videoId);

       boolean existsByVideoId(Long videoId);

       @Query("SELECT s FROM VideoSegment s JOIN Video v ON s.videoId = v.id WHERE v.userId = :userId " +
                     "AND v.uploadTime >= :startDate AND v.uploadTime <= :endDate " +
                     "AND (s.rawText LIKE CONCAT('%', :keyword, '%') OR s.polishedText LIKE CONCAT('%', :keyword, '%')) "
                     +
                     "ORDER BY v.uploadTime DESC, s.startTime ASC")
       List<VideoSegment> searchSegmentsByKeywordAndDate(@Param("userId") Long userId,
                     @Param("keyword") String keyword,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);
}
