package com.black.asr.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.black.asr.config.WhisperAsrProperties;
import com.black.asr.po.VideoSegment;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.asr.service.AsrService;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.exception.BusinessException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于本地 Whisper 开源模型的 ASR 服务实现
 * 调用独立的 Python FastAPI 服务进行转写，由于该服务本身针对长音频使用 vad_filter 做了优化，
 * 此处使用 HTTP 长连接进行同步调用。
 */
@Slf4j
@Service("whisperAsrServiceImpl")
@RequiredArgsConstructor
public class WhisperAsrServiceImpl implements AsrService {

    private final WhisperAsrProperties properties;
    private final VideoSegmentRepository videoSegmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public List<VideoSegmentVo> transcribe(VideoVo video) {
        if (!isAvailable()) {
            throw BusinessException.configIncomplete();
        }

        log.info("开始调用本地 Whisper ASR 服务转写 - videoId: {}, url: {}", video.getId(), video.getOssUrl());
        
        List<VideoSegmentVo> segmentList = new ArrayList<>();
        try {
            // 构造请求体 JSON
            String requestBody = String.format("{\"id\": %d, \"url\": \"%s\"}", video.getId(), video.getOssUrl());
            
            URL url = new URL(properties.getUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(properties.getConnectTimeout() * 1000);
            connection.setReadTimeout(properties.getReadTimeout() * 1000);
            connection.setDoOutput(true);

            // 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 获取响应
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        responseBuilder.append(responseLine.trim());
                    }
                }
                
                String jsonResponse = responseBuilder.toString();
                WhisperResponse response = objectMapper.readValue(jsonResponse, WhisperResponse.class);
                
                if (response.getCode() == 200 && response.getData() != null) {
                    for (WhisperSegment seg : response.getData()) {
                        VideoSegmentVo vo = new VideoSegmentVo();
                        vo.setId(IdUtil.getSnowflakeNextId());
                        vo.setVideoId(video.getId());
                        vo.setStartTime(seg.getStart());
                        vo.setEndTime(seg.getEnd());
                        vo.setRawText(seg.getText());
                        vo.setPolishedText(seg.getText()); // Whisper 直接给出的就是可读文本，暂不区分 polished
                        segmentList.add(vo);
                        
                        // 保存到数据库
                        VideoSegment po = vo.toPo();
                        if (po != null) {
                            videoSegmentRepository.save(po);
                        }
                    }
                    log.info("Whisper 转写完成 - videoId: {}, 提取了 {} 个片段", video.getId(), segmentList.size());
                } else {
                    log.error("Whisper 服务返回异常: {}", response.getMessage());
                    throw BusinessException.transcribeFailedWithMessage(response.getMessage());
                }
            } else {
                StringBuilder errorBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorBuilder.append(responseLine.trim());
                    }
                }
                log.error("调用 Whisper 服务 HTTP 错误: {}, {}", responseCode, errorBuilder);
                throw BusinessException.transcribeFailedWithMessage("HTTP " + responseCode + ": " + errorBuilder.toString());
            }

        } catch (Exception e) {
            log.error("转写过程中发生未捕获异常: ", e);
            throw BusinessException.transcribeFailedWithMessage(e.getMessage());
        }

        return segmentList;
    }

    @Override
    public boolean isAvailable() {
        return StrUtil.isNotBlank(properties.getUrl());
    }

    // 内部类用于反序列化
    @Data
    public static class WhisperResponse {
        private int code;
        private String message;
        private List<WhisperSegment> data;
    }

    @Data
    public static class WhisperSegment {
        private int start;
        private int end;
        private String text;
    }
}
