package com.black.asr.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.exceptions.ClientException;
import com.black.asr.config.AliyunTingwuAsrProperties;
import com.black.asr.vo.VideoVo;
import com.black.constant.AppConstants;
import com.black.exception.BusinessException;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.asr.service.AsrService;
import com.black.asr.vo.VideoSegmentVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云通义听悟ASR服务实现
 * 参考文档：https://help.aliyun.com/zh/tingwu/offline-transcribe-of-audio-and-video-files
 */
@Slf4j
@Service("aliyunTingwuAsrServiceImpl")
@RequiredArgsConstructor
public class AliyunTingwuAsrServiceImpl implements AsrService {
    private final AliyunTingwuAsrProperties properties;

    private final VideoSegmentRepository videoSegmentRepository;

    /**
     * 通过OBS URL转写视频/音频
     *
     * @param video 视频信息
     * @return 转写文本
     * @throws BusinessException 转写失败时抛出异常
     */
    @Override
    public List<VideoSegmentVo> transcribe(VideoVo video) {
        // 验证配置
        if (!isAvailable()) {
            throw BusinessException.configIncomplete();
        }

        // 步骤1: 创建转写任务
        log.info("开始创建转写任务 - videoId: {}, ossUrl: {}", video.getId(), video.getOssUrl());
        String taskId = summitTask(video.getOssUrl());
        if (taskId == null) {
            log.error("创建转写任务失败 - videoId: {}", video.getId());
            throw BusinessException.createTaskFailed();
        }
        log.info("转写任务创建成功 - videoId: {}, taskId: {}", video.getId(), taskId);

        // 步骤2: 轮询查询任务结果
        List<VideoSegmentVo> videoSegmentList = pollTaskResult(taskId);
        if (videoSegmentList == null) {
            throw BusinessException.transcribeTimeout();
        }

        // 步骤3: 分段内容绑定视频ID
        for (VideoSegmentVo videoSegment : videoSegmentList) {
            videoSegment.setVideoId(video.getId());
            videoSegmentRepository.save(videoSegment.toPo());
        }

        return videoSegmentList;
    }

    /**
     * 提交转写任务
     *
     * @param fileUrl 音频文件URL
     * @return 任务ID
     */
    private String summitTask(String fileUrl) {
        try {
            CommonRequest request = createCommonRequest(
                    properties.getEndpoint(),
                    AppConstants.AliyunTingwu.API_VERSION,
                    ProtocolType.HTTPS,
                    MethodType.PUT,
                    AppConstants.AliyunTingwu.TASKS_URI);

            request.putQueryParameter(AppConstants.AliyunTingwu.QUERY_TYPE,
                    AppConstants.AliyunTingwu.QUERY_TYPE_OFFLINE);

            JSONObject root = new JSONObject();
            root.put(AppConstants.AliyunTingwu.FIELD_APP_KEY, properties.getAppKey());

            JSONObject input = new JSONObject();
            input.fluentPut(AppConstants.AliyunTingwu.FIELD_FILE_URL, fileUrl)
                    .fluentPut(AppConstants.AliyunTingwu.FIELD_SOURCE_LANGUAGE, AppConstants.AliyunTingwu.LANGUAGE_AUTO)
                    .fluentPut(AppConstants.AliyunTingwu.FIELD_TASK_KEY,
                            AppConstants.AliyunTingwu.TASK_KEY_PREFIX + System.currentTimeMillis());
            root.put(AppConstants.AliyunTingwu.FIELD_INPUT, input);

            JSONObject parameters = new JSONObject();
            parameters.put(AppConstants.AliyunTingwu.FIELD_TEXT_POLISH_ENABLED, true);
            JSONObject transcription = new JSONObject();
            transcription.put(AppConstants.AliyunTingwu.FIELD_MODEL, AppConstants.AliyunTingwu.MODEL_DOMAIN_EDUCATION);
            parameters.put(AppConstants.AliyunTingwu.FIELD_TRANSCRIPTION, transcription);
            root.put(AppConstants.AliyunTingwu.FIELD_PARAMETERS, parameters);

            request.setHttpContent(root.toJSONString().getBytes(StandardCharsets.UTF_8),
                    AppConstants.AliyunTingwu.ENCODING_UTF8, FormatType.JSON);

            DefaultProfile profile = DefaultProfile.getProfile(
                    properties.getRegionId(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);
            CommonResponse response = client.getCommonResponse(request);

            String responseData = response.getData();
            JSONObject body = JSONObject.parseObject(responseData);

            // 检查返回码
            Integer code = body.getInteger(AppConstants.AliyunTingwu.FIELD_CODE);
            if (code != null && code == AppConstants.AliyunTingwu.API_SUCCESS_CODE) {
                JSONObject data = body.getJSONObject(AppConstants.AliyunTingwu.FIELD_DATA);
                if (data != null) {
                    return data.getString(AppConstants.AliyunTingwu.FIELD_TASK_ID);
                }
            } else {
                String message = body.getString(AppConstants.AliyunTingwu.FIELD_MESSAGE);
                throw BusinessException.createTaskFailedWithMessage(message);
            }

            return null;
        } catch (ClientException e) {
            throw BusinessException.createTaskFailedWithMessage(e.getMessage());
        }
    }

    /**
     * 获取任务信息
     * 参考：https://help.aliyun.com/zh/tingwu/api-tingwu-2023-09-30-gettaskinfo
     *
     * @param taskId 任务ID
     * @return 任务信息JSON对象，包含TaskStatus和Result等字段
     */
    private JSONObject getTaskInfo(String taskId) {
        try {
            String queryUrl = String.format(AppConstants.AliyunTingwu.TASK_INFO_URI_FORMAT, taskId);

            CommonRequest request = createCommonRequest(
                    properties.getEndpoint(),
                    AppConstants.AliyunTingwu.API_VERSION,
                    ProtocolType.HTTPS,
                    MethodType.GET,
                    queryUrl);

            DefaultProfile profile = DefaultProfile.getProfile(
                    properties.getRegionId(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);
            CommonResponse response = client.getCommonResponse(request);

            String responseData = response.getData();
            JSONObject responseJson = JSONObject.parseObject(responseData);

            // 检查返回码
            Integer code = responseJson.getInteger(AppConstants.AliyunTingwu.FIELD_CODE);
            if (code != null && code == AppConstants.AliyunTingwu.API_SUCCESS_CODE) {
                // 成功，返回Data对象
                return responseJson.getJSONObject(AppConstants.AliyunTingwu.FIELD_DATA);
            } else {
                // 失败，记录错误信息
                String message = responseJson.getString(AppConstants.AliyunTingwu.FIELD_MESSAGE);
                throw BusinessException.getTranscribeContentFailedWithMessage(message);
            }
        } catch (ClientException e) {
            throw BusinessException.getTranscribeContentFailedWithMessage(e.getMessage());
        }
    }

    /**
     * 轮询查询任务结果
     * 参考：https://help.aliyun.com/zh/tingwu/api-tingwu-2023-09-30-gettaskinfo
     */
    private List<VideoSegmentVo> pollTaskResult(String taskId) {
        try {
            long startTime = System.currentTimeMillis();

            while (true) {
                // 检查是否超时
                if (System.currentTimeMillis() - startTime > properties.getMaxWaitTime() * 1000L) {
                    return null;
                }

                // 调用GetTaskInfo API查询任务状态
                JSONObject taskData = getTaskInfo(taskId);

                if (taskData != null) {
                    String taskStatus = taskData.getString(AppConstants.AliyunTingwu.FIELD_TASK_STATUS);

                    // 任务状态：ONGOING(进行中), COMPLETED(完成), FAILED(失败), INVALID(无效)
                    if (AppConstants.AliyunTingwu.STATUS_COMPLETED.equals(taskStatus)) {
                        log.info("转写任务完成 - taskId: {}", taskId);
                        // 任务完成，提取转写文本
                        JSONObject result = taskData.getJSONObject(AppConstants.AliyunTingwu.FIELD_RESULT);
                        if (result == null) {
                            return null;
                        }
                        return extractVideoSegment(result);
                    } else if (AppConstants.AliyunTingwu.STATUS_FAILED.equals(taskStatus)
                            || AppConstants.AliyunTingwu.STATUS_INVALID.equals(taskStatus)) {
                        log.error("转写任务异常 - taskId: {}, status: {}, error: {}", taskId, taskStatus, taskData.getString(AppConstants.AliyunTingwu.FIELD_ERROR_MESSAGE));
                        // 任务失败
                        String errorMessage = taskData.getString(AppConstants.AliyunTingwu.FIELD_ERROR_MESSAGE);
                        throw BusinessException.transcribeFailedWithMessage(errorMessage);
                    }
                    // ONGOING 状态，继续轮询
                }

                // 等待后继续轮询
                Thread.sleep(properties.getPollInterval() * 1000L);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从任务结果中提取转写文本
     * 根据API文档：Result.Transcription 是一个JSON文件的URL，需要下载并解析
     * 参考：https://help.aliyun.com/zh/tingwu/api-tingwu-2023-09-30-gettaskinfo
     */
    private List<VideoSegmentVo> extractVideoSegment(JSONObject result) {
        try {
            // 获取Transcription URL（语音转写结果的JSON文件URL）和 TextPolish URL（语音转写润色结果的JSON文件URL）
            String transcriptionUrl = result.getString(AppConstants.AliyunTingwu.FIELD_TRANSCRIPTION);
            String textPolishUrl = result.getString(AppConstants.AliyunTingwu.FIELD_TEXT_POLISH);
            if (StrUtil.hasEmpty(transcriptionUrl, textPolishUrl)) {
                return null;
            }

            // 下载并解析Transcription JSON文件 和 TextPolish JSON文件
            String transcriptionJson = downloadJsonFromUrl(transcriptionUrl);
            String textPolishJson = downloadJsonFromUrl(textPolishUrl);
            if (transcriptionJson == null || textPolishJson == null) {
                log.error("下载转写结果JSON失败 - taskId: {}", transcriptionUrl);
                return null;
            }

            // 解析JSON获取转写文本 和 润色文本
            JSONObject transcriptionData = JSONObject.parseObject(transcriptionJson);
            JSONObject textPolishData = JSONObject.parseObject(textPolishJson);

            List<VideoSegmentVo> videoSegmentList = new ArrayList<>();

            // 根据阿里云通义听悟返回的JSON结构提取文本
            JSONObject transcription = transcriptionData.getJSONObject(AppConstants.AliyunTingwu.FIELD_TRANSCRIPTION);
            if (transcription != null) {
                JSONArray paragraphs = transcription.getJSONArray(AppConstants.AliyunTingwu.FIELD_PARAGRAPHS);
                if (paragraphs != null && !paragraphs.isEmpty()) {
                    // 遍历所有段落
                    for (int i = 0; i < paragraphs.size(); i++) {
                        JSONObject paragraph = paragraphs.getJSONObject(i);
                        if (paragraph != null) {
                            StringBuilder paragraphText = new StringBuilder();
                            VideoSegmentVo videoSegment = new VideoSegmentVo();
                            JSONArray words = paragraph.getJSONArray(AppConstants.AliyunTingwu.FIELD_WORDS);
                            if (words != null && !words.isEmpty()) {
                                // 遍历段落中的所有单词
                                for (int j = 0; j < words.size(); j++) {
                                    JSONObject word = words.getJSONObject(j);
                                    if (word != null) {
                                        String wordText = word.getString(AppConstants.AliyunTingwu.FIELD_TEXT);
                                        if (wordText != null && !wordText.trim().isEmpty()) {
                                            paragraphText.append(wordText);
                                        }
                                    }
                                }
                            }
                            videoSegment.setRawText(paragraphText.toString());
                            videoSegmentList.add(videoSegment);
                        }
                    }
                }
            }

            JSONArray textPolish = textPolishData.getJSONArray(AppConstants.AliyunTingwu.FIELD_TEXT_POLISH);
            if (textPolish != null && !textPolish.isEmpty()) {
                for (int i = 0; i < textPolish.size(); i++) {
                    JSONObject textPolishItem = textPolish.getJSONObject(i);
                    if (textPolishItem != null) {
                        String textPolishText = textPolishItem
                                .getString(AppConstants.AliyunTingwu.FIELD_FORMAL_PARAGRAPH_TEXT);
                        if (textPolishText != null && !textPolishText.trim().isEmpty()) {
                            videoSegmentList.get(i).setPolishedText(textPolishText);
                        }
                        videoSegmentList.get(i)
                                .setId(textPolishItem.getLong(AppConstants.AliyunTingwu.FIELD_PARAGRAPH_ID));
                        videoSegmentList.get(i)
                                .setStartTime(textPolishItem.getInteger(AppConstants.AliyunTingwu.FIELD_START));
                        videoSegmentList.get(i)
                                .setEndTime(textPolishItem.getInteger(AppConstants.AliyunTingwu.FIELD_END));
                    }
                }
            }

            return videoSegmentList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从URL下载JSON内容
     */
    private String downloadJsonFromUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(AppConstants.AliyunTingwu.HTTP_METHOD_GET);
            connection.setConnectTimeout(AppConstants.AliyunTingwu.HTTP_CONNECT_TIMEOUT);
            connection.setReadTimeout(AppConstants.AliyunTingwu.HTTP_READ_TIMEOUT);

            int responseCode = connection.getResponseCode();
            if (responseCode == AppConstants.AliyunTingwu.HTTP_SUCCESS_CODE) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            } else {
                // 读取错误信息
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    System.err.println(String.format(AppConstants.Message.DOWNLOAD_JSON_FAILED,
                            responseCode, errorResponse));
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static CommonRequest createCommonRequest(String domain, String version, ProtocolType protocolType,
            MethodType method, String uri) {
        // 创建API请求并设置参数
        CommonRequest request = new CommonRequest();
        request.setSysDomain(domain);
        request.setSysVersion(version);
        request.setSysProtocol(protocolType);
        request.setSysMethod(method);
        request.setSysUriPattern(uri);
        request.setHttpContentType(FormatType.JSON);
        return request;
    }

    @Override
    public boolean isAvailable() {
        return !StrUtil.hasEmpty(properties.getAccessKeyId(), properties.getAccessKeySecret(), properties.getAppKey());
    }
}