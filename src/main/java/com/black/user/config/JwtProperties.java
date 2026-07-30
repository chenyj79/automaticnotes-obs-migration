package com.black.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /**
     * JWT签名密钥
     */
    private String secret;

    /**
     * JWT过期时间（毫秒）
     */
    private long expiration;

    /**
     * JWT请求头名称
     */
    private String header;

    /**
     * JWT Token前缀
     */
    private String prefix;
}
