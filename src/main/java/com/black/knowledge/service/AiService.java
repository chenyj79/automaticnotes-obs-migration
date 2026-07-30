package com.black.knowledge.service;

import com.black.knowledge.dto.ai.IndependentKnowledgePoint;
import com.black.exception.BusinessException;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.dto.ai.VideoScoreResult;
import com.black.knowledge.vo.FrameworkCategorySuggestionVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI服务
 * 使用Spring AI Alibaba（通义千问）实现知识提取和视频评分
 * 提示词从外部文件加载，输出使用框架的entity()结构化解析
 */
@Slf4j
@Service
public class AiService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/pureExtractionPrompt.st")
    private Resource pureExtractionPromptResource;

    @Value("classpath:prompts/reconcileKnowledgePrompt.st")
    private Resource reconcileKnowledgePromptResource;

    @Value("classpath:prompts/scoreVideoPrompt.st")
    private Resource scoreVideoPromptResource;

    @Value("classpath:prompts/frameworkCategorySuggestionPrompt.st")
    private Resource frameworkCategorySuggestionPromptResource;

    @Value("classpath:prompts/videoSummaryPrompt.st")
    private Resource videoSummaryPromptResource;

    @Value("classpath:prompts/segmentSummaryPrompt.st")
    private Resource segmentSummaryPromptResource;

    public AiService(@Qualifier("qwenChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 修复大模型输出 JSON 中，因 LaTeX 公式带来的未转义反斜杠（如 \varphi 会报错 Unrecognized character
     * escape 'v'）
     */
    private String fixUnescapedBackslashes(String jsonContent) {
        if (jsonContent == null)
            return null;
        // 查找单反斜杠（且前面没有反斜杠）后跟着不是合法 JSON 转义字符的（" \ / b f n r t u）
        // 并将其替换为双反斜杠。使用负向先行断言 (?<!\\\\) 防止将正确的 \\ 变成 \\\
        return jsonContent.replaceAll("(?<!\\\\)\\\\([^\"\\\\/bfnrtu])", "\\\\\\\\$1");
    }

    /**
     * 第一阶段：从转写文本中独立提取知识点
     */
    public List<IndependentKnowledgePoint> extractIndependentKnowledge(String transcriptText, String subject,
            String categoryDefinitions, String existingAnchorTitles) {
        PromptTemplate promptTemplate = new PromptTemplate(pureExtractionPromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "subject", subject != null ? subject : "",
                "categoryDefinitions", categoryDefinitions != null ? categoryDefinitions : "",
                "existingAnchorTitles", existingAnchorTitles != null ? existingAnchorTitles : "",
                "transcript", transcriptText));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw BusinessException.aiResponseIsEmpty();
            }
            content = fixUnescapedBackslashes(content);
            BeanOutputConverter<List<IndependentKnowledgePoint>> converter = new BeanOutputConverter<>(
                    new ParameterizedTypeReference<>() {
                    });
            return converter.convert(content);
        } catch (Exception e) {
            log.error("AI知识独立提取失败", e);
            throw BusinessException.aiExtractionFailed(e.getMessage());
        }
    }

    /**
     * 第二阶段：比对提取出的知识点与现有知识点进行重构
     */
    public List<ExtractedKnowledgePoint> reconcileKnowledge(String extractedPointsJson, String similarContextJson,
            String categoryDefinitions) {
        PromptTemplate promptTemplate = new PromptTemplate(reconcileKnowledgePromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "extractedPoints", extractedPointsJson,
                "similarContext", similarContextJson,
                "categoryDefinitions", categoryDefinitions != null ? categoryDefinitions : ""));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw BusinessException.aiResponseIsEmpty();
            }
            content = fixUnescapedBackslashes(content);
            BeanOutputConverter<List<ExtractedKnowledgePoint>> converter = new BeanOutputConverter<>(
                    new ParameterizedTypeReference<>() {
                    });
            return converter.convert(content);
        } catch (Exception e) {
            log.error("AI知识对齐比对失败", e);
            throw BusinessException.aiExtractionFailed(e.getMessage());
        }
    }

    /**
     * 根据提取的知识点评估视频质量（使用entity()结构化输出）
     *
     * @param knowledgePointsJson 提取的知识点JSON
     * @param videoDuration       视频时长（秒）
     * @param totalCount          知识点总数
     * @param newCount            新增知识点数
     * @param updateCount         补充知识点数
     * @param subject             学科/主题
     */
    public VideoScoreResult scoreVideo(String knowledgePointsJson, int videoDuration,
            int totalCount, int newCount, int updateCount, int redundantCount,
            String subject) {
        PromptTemplate promptTemplate = new PromptTemplate(scoreVideoPromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "subject", subject != null ? subject : "",
                "duration", String.valueOf(videoDuration),
                "knowledgePoints", knowledgePointsJson,
                "totalCount", String.valueOf(totalCount),
                "newCount", String.valueOf(newCount),
                "updateCount", String.valueOf(updateCount),
                "redundantCount", String.valueOf(redundantCount)));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw BusinessException.aiResponseIsEmpty();
            }
            content = fixUnescapedBackslashes(content);
            BeanOutputConverter<VideoScoreResult> converter = new BeanOutputConverter<>(VideoScoreResult.class);
            return converter.convert(content);
        } catch (Exception e) {
            log.error("AI视频评分失败", e);
            throw BusinessException.aiScoringFailed(e.getMessage());
        }
    }

    public List<FrameworkCategorySuggestionVo> suggestFrameworkCategories(String name, String subject,
            String description) {
        PromptTemplate promptTemplate = new PromptTemplate(frameworkCategorySuggestionPromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "name", name != null ? name : "",
                "subject", subject != null ? subject : "",
                "description", description != null ? description : ""));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw BusinessException.aiResponseIsEmpty();
            }
            content = fixUnescapedBackslashes(content);
            BeanOutputConverter<List<FrameworkCategorySuggestionVo>> converter = new BeanOutputConverter<>(
                    new ParameterizedTypeReference<>() {
                    });
            return converter.convert(content);
        } catch (Exception e) {
            log.error("AI分类建议失败", e);
            throw BusinessException.aiExtractionFailed(e.getMessage());
        }
    }

    /**
     * 生成视频内容简介
     *
     * @param transcriptText 视频转写文本
     * @return 生成的视频简介
     */
    public String generateVideoSummary(String transcriptText) {
        PromptTemplate promptTemplate = new PromptTemplate(videoSummaryPromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "transcript", transcriptText != null ? transcriptText : ""));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                log.warn("AI生成的视频简介为空");
                return "暂无简介";
            }
            return content.trim();
        } catch (Exception e) {
            log.error("AI生成视频简介失败", e);
            return "AI生成简介失败";
        }
    }

    /**
     * 生成知识点关联视频片段的专门概述
     *
     * @param knowledgeTitle 知识点标题
     * @param segmentText    片段转写文本
     * @return 生成的片段概述
     */
    public String generateSegmentSummary(String knowledgeTitle, String segmentText) {
        PromptTemplate promptTemplate = new PromptTemplate(segmentSummaryPromptResource);
        Prompt prompt = promptTemplate.create(Map.of(
                "knowledgeTitle", knowledgeTitle != null ? knowledgeTitle : "",
                "transcript", segmentText != null ? segmentText : ""));

        try {
            String content = chatClient.prompt(prompt).call().content();
            if (content == null || content.isBlank()) {
                log.warn("AI生成的片段概述为空");
                return "暂无概述";
            }
            return content.trim();
        } catch (Exception e) {
            log.error("AI生成片段概述失败", e);
            return "AI生成概述失败";
        }
    }
}
