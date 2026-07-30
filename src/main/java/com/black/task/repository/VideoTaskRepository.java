package com.black.task.repository;

import com.black.task.enums.TaskStatus;
import com.black.task.po.VideoTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoTaskRepository extends JpaRepository<VideoTask, Long> {
    Optional<VideoTask> findByVideoId(Long videoId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE VideoTask t SET t.retryCount = t.retryCount + 1 WHERE t.id = :taskId")
    void incrementRetryCount(@Param("taskId") Long taskId);
    
    List<VideoTask> findByFrameworkIdAndUserIdAndStatusIn(Long frameworkId, Long userId, List<TaskStatus> statuses);

    @Query("SELECT t FROM VideoTask t WHERE t.frameworkId = :frameworkId AND t.userId = :userId AND " +
            "(t.status IN :pendingStatuses OR t.status = 'STATUS_RETRYING' OR (t.status = 'STATUS_SUCCESS' AND t.isReportViewed = false))")
    List<VideoTask> findPendingOrUnviewedTasks(Long frameworkId, Long userId, List<TaskStatus> pendingStatuses);
}
