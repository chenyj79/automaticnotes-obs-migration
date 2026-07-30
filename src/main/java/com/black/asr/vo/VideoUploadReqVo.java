package com.black.asr.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoUploadReqVo {
    @NotBlank(message = "Original file name cannot be blank")
    private String originalFileName;
    
    @NotBlank(message = "OBS Object Name cannot be blank")
    private String ossObjectName;

    @NotBlank(message = "OBS URL cannot be blank")
    private String ossUrl;
    
    @NotNull(message = "File size cannot be null")
    private Long fileSize;
    
    @NotNull(message = "Duration cannot be null")
    private Integer duration;
    
    @NotNull(message = "Framework ID cannot be null")
    private Long frameworkId;
}
