package com.black.asr.service;

import com.black.asr.po.VideoSegment;
import com.black.asr.repository.VideoSegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoSegmentService {

    private final VideoSegmentRepository videoSegmentRepository;

    public List<VideoSegment> findByVideoId(Long videoId) {
        return videoSegmentRepository.findByVideoId(videoId);
    }
}
