package com.black.asr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云通义听悟ASR配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.aliyun")
public class AliyunTingwuAsrProperties {
    
    /**
     * 访问密钥ID
     */
    private String accessKeyId = "";
    
    /**
     * 访问密钥Secret
     */
    private String accessKeySecret = "";
    
    /**
     * 应用Key
     */
    private String appKey = "";
    
    /**
     * API端点
     */
    private String endpoint = "tingwu.cn-beijing.aliyuncs.com";
    
    /**
     * 区域ID
     */
    private String regionId = "cn-shanghai";
    
    /**
     * 轮询间隔（秒），默认60秒
     */
    private int pollInterval = 60;
    
    /**
     * 最大等待时间（秒），默认3小时
     */
    private int maxWaitTime = 10800;
}
