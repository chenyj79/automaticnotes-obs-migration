package com.black.knowledge.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.black.asr.po.Video;
import com.black.asr.po.VideoSegment;
import com.black.asr.repository.VideoRepository;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.knowledge.po.KnowledgePoint;
import com.black.knowledge.po.KnowledgePointVideoRef;
import com.black.knowledge.repository.KnowledgePointRepository;
import com.black.knowledge.repository.KnowledgePointVideoRefRepository;
import com.black.knowledge.vo.VideoIndexVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 视频索引服务
 * 根据关键词或知识点检索相关视频片段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoIndexService {

    private final KnowledgePointRepository pointRepository;
    private final KnowledgePointVideoRefRepository videoRefRepository;
    private final VideoRepository videoRepository;
    private final VideoSegmentRepository segmentRepository;

    /**
     * 根据关键词搜索视频片段
     *
     * @param keyword     搜索关键词
     * @param frameworkId 可选，限定在某个框架内搜索
     * @return 匹配的视频片段列表
     */
    public List<VideoIndexVo> searchByKeyword(String keyword, Long frameworkId) {
        List<VideoIndexVo> results = new ArrayList<>();

        // 1. 在知识点标题中搜索匹配的知识点
        List<KnowledgePoint> matchedPoints;
        if (frameworkId != null) {
            matchedPoints = pointRepository.findByFrameworkIdAndTitleContaining(frameworkId, keyword);
        } else {
            matchedPoints = pointRepository.findByTitleContaining(keyword);
        }

        if (matchedPoints.isEmpty()) {
            return results;
        }

        // 2. 获取匹配知识点的视频引用
        List<Long> pointIds = matchedPoints.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toList());

        List<KnowledgePointVideoRef> refs = videoRefRepository.findByKnowledgePointIdIn(pointIds);

        // 3. 构建索引结果
        Map<Long, KnowledgePoint> pointMap = matchedPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p));

        for (KnowledgePointVideoRef ref : refs) {
            Video video = videoRepository.findById(ref.getVideoId()).orElse(null);
            if (video == null)
                continue;

            KnowledgePoint point = pointMap.get(ref.getKnowledgePointId());

            // 解析时间戳JSON
            JSONArray timestamps = JSON.parseArray(ref.getTimestamps());
            if (timestamps != null) {
                for (int i = 0; i < timestamps.size(); i++) {
                    JSONObject ts = timestamps.getJSONObject(i);
                    VideoIndexVo vo = new VideoIndexVo();
                    vo.setVideoId(video.getId());
                    vo.setVideoTitle(video.getOriginalFileName());
                    vo.setOssUrl(video.getOssUrl());
                    vo.setStartTime(ts.getInteger("start"));
                    vo.setEndTime(ts.getInteger("end"));
                    vo.setKnowledgePointTitle(point != null ? point.getTitle() : "");

                    // 获取知识点时间段内所有重叠的转写文本
                    if (vo.getStartTime() != null && vo.getEndTime() != null) {
                        List<VideoSegment> segments = segmentRepository.findByVideoId(video.getId());
                        StringBuilder matchedText = new StringBuilder();
                        for (VideoSegment seg : segments) {
                            // seg的时间段与vo的时间段有重叠：seg.start < vo.end && seg.end > vo.start
                            if (seg.getStartTime() != null && seg.getEndTime() != null
                                    && seg.getStartTime() < vo.getEndTime()
                                    && seg.getEndTime() > vo.getStartTime()) {
                                if (!matchedText.isEmpty()) {
                                    matchedText.append("\n");
                                }
                                matchedText.append(
                                        seg.getPolishedText() != null ? seg.getPolishedText() : seg.getRawText());
                            }
                        }
                        if (!matchedText.isEmpty()) {
                            vo.setMatchedText(matchedText.toString());
                        }
                    }

                    results.add(vo);
                }
            }
        }

        return results;
    }
}
