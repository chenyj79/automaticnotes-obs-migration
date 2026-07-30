package com.black.asr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地 Whisper ASR 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.whisper")
public class WhisperAsrProperties {
    
    /**
     * Python 服务端点 URL
     */
    private String url = "http://localhost:8000/transcribe";

    /**
     * Python 服务端点 URL (标点恢复)
     */
    private String restorePuncUrl = "http://localhost:8000/restore_punc";

    /**
     * HTTP 请求读取超时时间(秒)，默认 1800 秒（30分钟）以支持长音频推理
     */
    private int readTimeout = 1800;

    /**
     * HTTP 请求连接超时时间(秒)，默认 5 秒
     */
    private int connectTimeout = 5;
}
