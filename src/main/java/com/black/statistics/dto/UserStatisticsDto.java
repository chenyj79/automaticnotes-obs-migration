package com.black.statistics.dto;

import lombok.Data;

@Data
public class UserStatisticsDto {
    private long videoCount;
    private long frameworkCount;
    private long knowledgePointCount;
    private double averageScore;
}
