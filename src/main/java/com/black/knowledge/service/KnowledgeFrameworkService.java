package com.black.knowledge.service;

import cn.hutool.core.util.StrUtil;
import com.black.exception.BusinessException;
import com.black.knowledge.dto.CreateFrameworkRequest;
import com.black.knowledge.dto.UpdateFrameworkRequest;
import com.black.knowledge.po.KnowledgeFramework;
import com.black.knowledge.repository.KnowledgeFrameworkRepository;
import com.black.knowledge.vo.KnowledgeFrameworkVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识框架服务
 * 所有权校验已由 @CheckFrameworkOwner AOP 切面统一处理
 */
@Service
@RequiredArgsConstructor
public class KnowledgeFrameworkService {

    private final KnowledgeFrameworkRepository frameworkRepository;
    private final KnowledgePointService knowledgePointService;
    private final FrameworkCategoryService frameworkCategoryService;

    /**
     * 创建知识框架
     */
    public KnowledgeFrameworkVo createFramework(CreateFrameworkRequest request, Long userId) {
        KnowledgeFramework framework = new KnowledgeFramework();
        framework.setUserId(userId);
        framework.setName(request.getName());
        framework.setSubject(request.getSubject());
        framework.setDescription(request.getDescription());
        frameworkRepository.save(framework);
        frameworkCategoryService.saveFrameworkCategories(framework.getId(), request.getCategories());
        return framework.toVo(frameworkCategoryService.getFrameworkCategories(framework.getId()));
    }

    /**
     * 获取当前用户的知识框架列表（分页）
     */
    public Page<KnowledgeFrameworkVo> getMyFrameworks(Long userId, Pageable pageable) {
        return frameworkRepository.findByUserId(userId, pageable)
                .map(framework -> framework.toVo(frameworkCategoryService.getFrameworkCategories(framework.getId())));
    }

    /**
     * 获取框架详情（AOP已校验所有权）
     */
    public KnowledgeFrameworkVo getFrameworkDetail(Long frameworkId) {
        KnowledgeFramework framework = getFrameworkById(frameworkId);
        frameworkCategoryService.ensureDefaultCategory(frameworkId);
        return framework.toVo(frameworkCategoryService.getFrameworkCategories(frameworkId));
    }

    /**
     * 更新知识框架（AOP已校验所有权）
     */
    public KnowledgeFrameworkVo updateFramework(Long frameworkId, UpdateFrameworkRequest request) {
        KnowledgeFramework framework = getFrameworkById(frameworkId);

        if (StrUtil.isNotBlank(request.getName())) {
            framework.setName(request.getName());
        }
        if (request.getSubject() != null) {
            framework.setSubject(request.getSubject());
        }
        if (request.getDescription() != null) {
            framework.setDescription(request.getDescription());
        }

        frameworkRepository.save(framework);
        return framework.toVo(frameworkCategoryService.getFrameworkCategories(frameworkId));
    }

    /**
     * 删除知识框架（AOP已校验所有权，级联删除知识点、关联关系）
     */
    @Transactional
    public void deleteFramework(Long frameworkId) {
        knowledgePointService.deleteAllByFrameworkId(frameworkId);
        frameworkCategoryService.deleteByFrameworkId(frameworkId);
        frameworkRepository.deleteById(frameworkId);
    }

    /**
     * 获取框架实体并校验所有权（供AOP切面调用）
     */
    public void getFrameworkAndCheckOwner(Long frameworkId, Long userId) {
        KnowledgeFramework framework = frameworkRepository.findById(frameworkId)
                .orElseThrow(() -> BusinessException.frameworkNotFound(frameworkId));

        if (!framework.getUserId().equals(userId)) {
            throw BusinessException.knowledgeAccessDenied();
        }
    }

    /**
     * 根据ID获取框架
     */
    public KnowledgeFramework getFrameworkById(Long frameworkId) {
        return frameworkRepository.findById(frameworkId)
                .orElseThrow(() -> BusinessException.frameworkNotFound(frameworkId));
    }
}
