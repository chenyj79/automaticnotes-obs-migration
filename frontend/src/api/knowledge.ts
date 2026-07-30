
import request from './request';
import type {
    ProcessResult,
    Page,
    KnowledgeFrameworkVo,
    CreateFrameworkRequest,
    SuggestFrameworkCategoriesRequest,
    FrameworkCategoryVo,
    AddKnowledgePointRequest,
    KnowledgePointVo,
    KnowledgeRelationVo,
    KnowledgePointDraftVo,
    ReviewDraftRequest
} from '@/types';

export const createFrameworkAPI = (data: CreateFrameworkRequest) => {
    return request.post<any, ProcessResult<KnowledgeFrameworkVo>>('/knowledge/framework', data);
};

export const suggestFrameworkCategoriesAPI = (data: SuggestFrameworkCategoriesRequest) => {
    return request.post<any, ProcessResult<FrameworkCategoryVo[]>>('/knowledge/framework/categories/suggest', data);
};

export const getMyFrameworksAPI = (params: { page?: number; size?: number }) => {
    return request.get<any, ProcessResult<Page<KnowledgeFrameworkVo>>>('/knowledge/framework/my', { params });
};

export const getFrameworkDetailAPI = (id: number | string) => {
    return request.get<any, ProcessResult<KnowledgeFrameworkVo>>(`/knowledge/framework/${id}`);
};

export const getFrameworkPointsAPI = (id: number | string, params?: { category?: string }) => {
    return request.get<any, ProcessResult<KnowledgePointVo[]>>(`/knowledge/framework/${id}/points`, { params });
};

export const deleteFrameworkAPI = (id: number | string) => {
    return request.delete<any, ProcessResult<void>>(`/knowledge/framework/${id}`);
};

// ==================== Knowledge Point API ====================

export const addKnowledgePointAPI = (data: AddKnowledgePointRequest) => {
    return request.post<any, ProcessResult<KnowledgePointVo>>('/knowledge/point', data);
};

// Update knowledge point signature
export const updateKnowledgePointAPI = (id: number | string, data: Partial<AddKnowledgePointRequest>) => {
    return request.put<any, ProcessResult<KnowledgePointVo>>(`/knowledge/point/${id}`, data);
};

export const deleteKnowledgePointAPI = (id: number | string) => {
    return request.delete<any, ProcessResult<void>>(`/knowledge/point/${id}`);
};

// ==================== Knowledge Relation API ====================

export const addRelationAPI = (data: any) => {
    return request.post<any, ProcessResult<any>>('/knowledge/relation', data);
};

export const getPointRelationsAPI = (pointId: number | string) => {
    return request.get<any, ProcessResult<any[]>>(`/knowledge/relation/point/${pointId}`);
};

export const getFrameworkRelationsAPI = (frameworkId: string | number) => {
    return request.get<any, ProcessResult<KnowledgeRelationVo[]>>(`/knowledge/framework/${frameworkId}/relations`);
};

export const deleteRelationAPI = (id: number | string) => {
    return request.delete<any, ProcessResult<void>>(`/knowledge/relation/${id}`);
};

// ==================== Knowledge Video API ====================

export const getVideoPointsAPI = (videoId: string | number) => {
    return request.get<any, ProcessResult<KnowledgePointVo[]>>(`/knowledge/video/${videoId}/points`);
};

// ==================== Knowledge Draft API ====================

export const getAllDraftsAPI = () => {
    return request.get<any, ProcessResult<KnowledgePointDraftVo[]>>(`/knowledge/drafts/all`);
};

export const getFrameworkDraftsAPI = (frameworkId: string | number) => {
    return request.get<any, ProcessResult<KnowledgePointDraftVo[]>>(`/knowledge/framework/${frameworkId}/drafts`);
};

export const getVideoDraftsAPI = (videoId: string | number) => {
    return request.get<any, ProcessResult<KnowledgePointDraftVo[]>>(`/knowledge/video/${videoId}/drafts`);
};

export const getDraftDetailAPI = (draftId: string | number) => {
    return request.get<any, ProcessResult<KnowledgePointDraftVo>>(`/knowledge/draft/${draftId}`);
};

export const reviewDraftAPI = (draftId: string | number, data: ReviewDraftRequest) => {
    return request.post<any, ProcessResult<void>>(`/knowledge/draft/${draftId}/review`, data);
};

export const deleteDraftAPI = (draftId: string | number) => {
    return request.delete<any, ProcessResult<void>>(`/knowledge/draft/${draftId}`);
};
