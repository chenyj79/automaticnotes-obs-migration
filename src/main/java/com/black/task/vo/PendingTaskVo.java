package com.black.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTaskVo {
    private Long id;
    private Long videoId;
    private String filename;
    private String status;
}
