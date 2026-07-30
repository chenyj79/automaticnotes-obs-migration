package com.black.knowledge.dto;

import com.black.knowledge.enums.RelationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加知识点关联关系请求
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddRelationRequest {
    @NotNull(message = "源知识点ID不能为空")
    private Long sourcePointId;

    @NotNull(message = "目标知识点ID不能为空")
    private Long targetPointId;

    @NotNull(message = "关系类型不能为空")
    private RelationType relationType;
}
