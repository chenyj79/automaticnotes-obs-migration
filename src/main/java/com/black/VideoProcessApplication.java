package com.black;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 视频处理应用主类
 */
@EnableScheduling
@SpringBootApplication
public class VideoProcessApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoProcessApplication.class, args);
    }
}

