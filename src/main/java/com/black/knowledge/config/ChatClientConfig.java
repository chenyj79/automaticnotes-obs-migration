package com.black.knowledge.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * ChatClient配置类
 * 集中管理ChatClient的创建，支持按需注入不同配置的client
 */
@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {
        private final String DEEPSEEK_MODEL = "deepseek-v3";
        private final String QWEN_MODEL = "qwen-plus";

        @Value("${spring.ai.dashscope.api-key}")
        private String apiKey;

        private final RestClient.Builder restClientBuilder;

        @Bean(name = "deepseek")
        public ChatModel deepSeek() {
                return DashScopeChatModel.builder()
                                .dashScopeApi(DashScopeApi.builder()
                                                .restClientBuilder(restClientBuilder)
                                                .apiKey(apiKey)
                                                .build())
                                .defaultOptions(
                                                DashScopeChatOptions.builder().withModel(DEEPSEEK_MODEL).build())
                                .build();
        }

        @Bean(name = "qwen")
        public ChatModel qwen() {
                return DashScopeChatModel.builder().dashScopeApi(DashScopeApi.builder()
                                .restClientBuilder(restClientBuilder)
                                .apiKey(apiKey)
                                .build())
                                .defaultOptions(
                                                DashScopeChatOptions.builder()
                                                                .withModel(QWEN_MODEL)
                                                                .build())
                                .build();
        }

        @Bean(name = "qwenChatClient")
        public ChatClient qwenChatClient(@Qualifier("qwen") ChatModel qwen) {
                return ChatClient.builder(qwen)
                                .defaultOptions(ChatOptions.builder()
                                                .model(QWEN_MODEL)
                                                .build())
                                .build();
        }

        @Bean(name = "deepseekChatClient")
        public ChatClient deepseekChatClient(@Qualifier("deepseek") ChatModel deepSeek) {
                return ChatClient.builder(deepSeek)
                                .defaultOptions(ChatOptions.builder()
                                                .model(DEEPSEEK_MODEL)
                                                .build())
                                .build();
        }
}
