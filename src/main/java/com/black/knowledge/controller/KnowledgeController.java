package com.black.knowledge.controller;

import com.black.constant.AppConstants;
import com.black.knowledge.annotation.CheckFrameworkOwner;
import com.black.knowledge.annotation.CheckKnowledgePointOwner;
import com.black.knowledge.dto.AddKnowledgePointRequest;
import com.black.knowledge.dto.AddRelationRequest;
import com.black.knowledge.dto.CreateFrameworkRequest;
import com.black.knowledge.dto.SuggestFrameworkCategoriesRequest;
import com.black.knowledge.dto.UpdateFrameworkRequest;
import com.black.knowledge.dto.UpdateKnowledgePointRequest;
import com.black.knowledge.service.FrameworkCategoryService;
import com.black.knowledge.service.KnowledgeFrameworkService;
import com.black.knowledge.service.KnowledgePointService;
import com.black.knowledge.service.KnowledgeRelationService;
import com.black.knowledge.service.KnowledgePointDraftService;
import com.black.knowledge.dto.KnowledgePointDraftRequest;
import com.black.knowledge.dto.ReviewDraftRequest;
import com.black.knowledge.vo.FrameworkCategorySuggestionVo;
import com.black.knowledge.vo.KnowledgeFrameworkVo;
import com.black.knowledge.vo.KnowledgePointVo;
import com.black.knowledge.vo.KnowledgeRelationVo;
import com.black.knowledge.vo.KnowledgePointDraftVo;
import com.black.model.ProcessResult;
import com.black.user.annotation.CurrentUser;
import com.black.user.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识模块控制器
 * 提供知识框架、知识点和知识关系的管理接口
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeFrameworkService frameworkService;

    @Autowired
    private KnowledgePointService pointService;

    @Autowired
    private KnowledgeRelationService relationService;

    @Autowired
    private FrameworkCategoryService frameworkCategoryService;

    @Autowired
    private KnowledgePointDraftService draftService;

    // ==================== 知识框架接口 ====================

    /**
     * 创建知识框架
     */
    @PostMapping("/framework")
    public ResponseEntity<ProcessResult<KnowledgeFrameworkVo>> createFramework(
            @Valid @RequestBody CreateFrameworkRequest request,
            @CurrentUser LoginUser loginUser) {
        KnowledgeFrameworkVo vo = frameworkService.createFramework(request, loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_CREATE_SUCCESS, vo));
    }

    @PostMapping("/framework/categories/suggest")
    public ResponseEntity<ProcessResult<List<FrameworkCategorySuggestionVo>>> suggestFrameworkCategories(
            @Valid @RequestBody SuggestFrameworkCategoriesRequest request) {
        List<FrameworkCategorySuggestionVo> suggestions = frameworkCategoryService.suggestCategories(
                request.getName(), request.getSubject(), request.getDescription());
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, suggestions));
    }

    /**
     * 获取我的知识框架列表（分页）
     */
    @GetMapping("/framework/my")
    public ResponseEntity<ProcessResult<Page<KnowledgeFrameworkVo>>> getMyFrameworks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoginUser loginUser) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        Page<KnowledgeFrameworkVo> frameworkPage = frameworkService.getMyFrameworks(loginUser.getId(), pageable);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, frameworkPage));
    }

    /**
     * 获取框架详情
     */
    @GetMapping("/framework/{id}")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<KnowledgeFrameworkVo>> getFrameworkDetail(
            @PathVariable Long id) {
        KnowledgeFrameworkVo vo = frameworkService.getFrameworkDetail(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, vo));
    }

    /**
     * 获取框架下的所有知识点（扁平列表，前端通过CONTAINS关系构建树）
     */
    @GetMapping("/framework/{id}/points")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<List<KnowledgePointVo>>> getFrameworkPoints(
            @PathVariable Long id,
            @RequestParam(required = false) String category) {
        List<KnowledgePointVo> points = pointService.getKnowledgePointsByCategory(id, category);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, points));
    }

    /**
     * 获取框架下的所有关系（前端构建知识图谱）
     */
    @GetMapping("/framework/{id}/relations")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<List<KnowledgeRelationVo>>> getFrameworkRelations(
            @PathVariable Long id) {
        List<KnowledgeRelationVo> relations = relationService.getRelationsByFrameworkId(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, relations));
    }

    /**
     * 更新知识框架
     */
    @PutMapping("/framework/{id}")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<KnowledgeFrameworkVo>> updateFramework(
            @PathVariable Long id,
            @RequestBody UpdateFrameworkRequest request) {
        KnowledgeFrameworkVo vo = frameworkService.updateFramework(id, request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS, vo));
    }

    /**
     * 删除知识框架
     */
    @DeleteMapping("/framework/{id}")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<Void>> deleteFramework(@PathVariable Long id) {
        frameworkService.deleteFramework(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_DELETE_SUCCESS));
    }

    // ==================== 知识点接口 ====================

    // ==================== 视频关联知识点接口 ====================

    /**
     * 获取指定视频提取出的所有知识点(笔记)
     */
    @GetMapping("/video/{videoId}/points")
    public ResponseEntity<ProcessResult<List<KnowledgePointVo>>> getVideoKnowledgePoints(
            @PathVariable Long videoId) {
        List<KnowledgePointVo> points = pointService.getKnowledgePointsByVideoId(videoId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, points));
    }

    /**
     * 手动添加知识点
     */
    @PostMapping("/point")
    @CheckFrameworkOwner("#request.frameworkId")
    public ResponseEntity<ProcessResult<KnowledgePointVo>> addKnowledgePoint(
            @Valid @RequestBody AddKnowledgePointRequest request) {
        KnowledgePointVo vo = pointService.addKnowledgePoint(request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_CREATE_SUCCESS, vo));
    }

    /**
     * 更新知识点
     */
    @PutMapping("/point/{id}")
    @CheckKnowledgePointOwner("#id")
    public ResponseEntity<ProcessResult<KnowledgePointVo>> updateKnowledgePoint(
            @PathVariable Long id,
            @RequestBody UpdateKnowledgePointRequest request) {
        KnowledgePointVo vo = pointService.updateKnowledgePoint(id, request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS, vo));
    }

    /**
     * 删除知识点
     */
    @DeleteMapping("/point/{id}")
    @CheckKnowledgePointOwner("#id")
    public ResponseEntity<ProcessResult<Void>> deleteKnowledgePoint(@PathVariable Long id) {
        pointService.deleteKnowledgePoint(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_DELETE_SUCCESS));
    }

    // ==================== 关系接口 ====================

    /**
     * 手动添加知识点关系
     */
    @PostMapping("/relation")
    public ResponseEntity<ProcessResult<KnowledgeRelationVo>> addRelation(
            @Valid @RequestBody AddRelationRequest request) {
        KnowledgeRelationVo vo = relationService.addRelation(request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_CREATE_SUCCESS, vo));
    }

    /**
     * 查询知识点的所有关系
     */
    @GetMapping("/relation/point/{pointId}")
    public ResponseEntity<ProcessResult<List<KnowledgeRelationVo>>> getPointRelations(
            @PathVariable Long pointId) {
        List<KnowledgeRelationVo> relations = relationService.getRelationsByPointId(pointId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, relations));
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/relation/{id}")
    public ResponseEntity<ProcessResult<Void>> deleteRelation(@PathVariable Long id) {
        relationService.deleteRelation(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_DELETE_SUCCESS));
    }

    // ==================== 草稿接口 ====================

    /**
     * 获取所有草稿
     */
    @GetMapping("/drafts/all")
    public ResponseEntity<ProcessResult<List<KnowledgePointDraftVo>>> getAllDrafts() {
        List<KnowledgePointDraftVo> drafts = draftService.getAllDrafts(null);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, drafts));
    }

    /**
     * 获取框架下的所有草稿
     */
    @GetMapping("/framework/{id}/drafts")
    @CheckFrameworkOwner("#id")
    public ResponseEntity<ProcessResult<List<KnowledgePointDraftVo>>> getFrameworkDrafts(
            @PathVariable Long id) {
        List<KnowledgePointDraftVo> drafts = draftService.getAllDrafts(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, drafts));
    }

    /**
     * 获取视频相关的草稿
     */
    @GetMapping("/video/{videoId}/drafts")
    public ResponseEntity<ProcessResult<List<KnowledgePointDraftVo>>> getVideoDrafts(
            @PathVariable Long videoId) {
        List<KnowledgePointDraftVo> drafts = draftService.getVideoDrafts(videoId);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, drafts));
    }

    /**
     * 获取草稿详情
     */
    @GetMapping("/draft/{id}")
    public ResponseEntity<ProcessResult<KnowledgePointDraftVo>> getDraftDetail(
            @PathVariable Long id) {
        KnowledgePointDraftVo draft = draftService.getDraftById(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, draft));
    }

    /**
     * 审核草稿
     */
    @PostMapping("/draft/{id}/review")
    public ResponseEntity<ProcessResult<Void>> reviewDraft(
            @PathVariable Long id,
            @Valid @RequestBody ReviewDraftRequest request) {
        draftService.reviewDraft(id, request);
        return ResponseEntity.ok(ProcessResult.success("审核成功"));
    }

    /**
     * 删除草稿
     */
    @DeleteMapping("/draft/{id}")
    public ResponseEntity<ProcessResult<Void>> deleteDraft(@PathVariable Long id) {
        draftService.deleteDraft(id);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.KNOWLEDGE_DELETE_SUCCESS));
    }
}

