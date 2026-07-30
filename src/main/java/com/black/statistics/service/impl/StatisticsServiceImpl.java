package com.black.statistics.service.impl;

import com.black.asr.po.Video;
import com.black.asr.repository.VideoRepository;
import com.black.knowledge.po.KnowledgeFramework;
import com.black.knowledge.po.VideoScore;
import com.black.knowledge.repository.KnowledgeFrameworkRepository;
import com.black.knowledge.repository.KnowledgePointRepository;
import com.black.knowledge.repository.VideoScoreRepository;
import com.black.statistics.dto.UserStatisticsDto;
import com.black.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统计数据服务实现类
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final VideoRepository videoRepository;
    private final KnowledgeFrameworkRepository frameworkRepository;
    private final KnowledgePointRepository pointRepository;
    private final VideoScoreRepository scoreRepository;

    @Override
    public UserStatisticsDto getDashboardStatistics(Long userId) {
        UserStatisticsDto stats = new UserStatisticsDto();

        // 1. 视频数量
        stats.setVideoCount(videoRepository.countByUserId(userId));

        // 2. 知识框架数量
        List<KnowledgeFramework> frameworks = frameworkRepository.findByUserId(userId);
        stats.setFrameworkCount(frameworks.size());

        // 3. 知识点数量
        if (!frameworks.isEmpty()) {
            List<Long> frameworkIds = frameworks.stream().map(KnowledgeFramework::getId).collect(Collectors.toList());
            stats.setKnowledgePointCount(pointRepository.countByFrameworkIdIn(frameworkIds));
        } else {
            stats.setKnowledgePointCount(0L);
        }

        // 4. 视频知识密度/效果平均分
        List<Video> videos = videoRepository.findByUserId(userId);
        if (!videos.isEmpty()) {
            List<Long> videoIds = videos.stream().map(Video::getId).collect(Collectors.toList());
            List<VideoScore> scores = scoreRepository.findByVideoIdIn(videoIds);
            if (!scores.isEmpty()) {
                double avgScore = scores.stream().mapToDouble(VideoScore::getTotalScore).average().orElse(0.0);
                stats.setAverageScore(avgScore);
            } else {
                stats.setAverageScore(0.0);
            }
        } else {
            stats.setAverageScore(0.0);
        }

        return stats;
    }
}
