// Global TypeScript Definitions matching backend DTOs/VOs

export interface ProcessResult<T = any> {
    success: boolean;
    message: string;
    code: number;
    data: T;
}

export interface UserVo {
    id: number;
    username: string;
    email: string | null;
    phone: string | null;
    avatarUrl: string | null;
    role: string | null;
    status: string | null;
    createTime: string | null;
}

export interface LoginResponse {
    token: string;
    tokenType: string;
    username: string;
    role: string;
}

export interface VideoVo {
    id: number;
    userId: number;
    originalFileName: string;
    duration: number | null;
    ossUrl: string | null;
    ossObjectName: string | null;
    fileSize: number | null;
    summary?: string;
    status: string; // 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
    uploadTime: string;
    frameworkId: number;
    // Extrapolated fields from Backend PO
    title?: string;
    fileName?: string;
    fileUrl?: string; // mapping ossUrl
    processStatus?: string; // mapping status
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

export interface VideoSegmentVo {
    id: number;
    videoId: number;
    startTime: number;
    endTime: number;
    rawText: string;
    polishedText: string;
}

export interface VideoScoreVo {
    id: number;
    videoId: number;
    densityScore: number;
    effectivenessScore: number;
    totalScore: number;
    knowledgeCount: number;
    explanation: string;
    createTime: string;
}

// Extrapolated mock type for Notes
export interface NoteVo {
    timestamp: string;
    entity: string;
    category: string;
    description: string;
    tagType: 'primary' | 'success' | 'warning' | 'danger' | 'info';
    type: 'primary' | 'success' | 'warning' | 'danger' | 'info';
}

// ----------------- Knowledge Module -----------------

export enum RelationType {
    PREREQUISITE = 'PREREQUISITE',
    RELATED = 'RELATED',
    CONTAINS = 'CONTAINS',
    EXTENSION = 'EXTENSION'
}

export interface KnowledgeFrameworkVo {
    id: number;
    userId: number;
    name: string;
    subject: string;
    description: string;
    categories?: FrameworkCategoryVo[];
    createTime: string;
    updateTime: string;
}

export interface FrameworkCategoryVo {
    id?: number;
    frameworkId?: number;
    name: string;
    definition: string;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
}

export interface PointVideoIndexingVo {
    videoId: number;
    videoTitle: string;
    timestamps: string;
    videoSummary?: string;
}

export interface KnowledgePointVo {
    id: number;
    frameworkId: number;
    title: string;
    content: string;
    category?: string;
    videoIndexings?: PointVideoIndexingVo[];
    createTime: string;
    updateTime: string;

    // Frontend-only property to render the constructed tree structure
    children?: KnowledgePointVo[];
}

// DTOs
export interface CreateFrameworkRequest {
    name: string;
    subject: string;
    description: string;
    categories?: FrameworkCategoryVo[];
}

export interface SuggestFrameworkCategoriesRequest {
    name: string;
    subject: string;
    description: string;
}

export interface AddKnowledgePointRequest {
    frameworkId: number;
    title: string;
    content: string;
    category?: string;
    videoRefs?: {
        videoId: number;
        segments: { start: number; end: number }[];
    }[];
}

export interface UpdateKnowledgePointRequest {
    title?: string;
    content?: string;
    category?: string;
    relationUpdates?: {
        action: 'ADD' | 'DELETE';
        sourcePointId: number;
        targetPointId: number;
        relationType: RelationType;
    }[];
    videoUpdates?: {
        action: 'SET' | 'DELETE';
        videoId: number;
        segments?: { start: number; end: number }[];
    }[];
}

export interface KnowledgeRelationVo {
    id: number;
    sourcePointId: number;
    sourcePointTitle: string;
    targetPointId: number;
    targetPointTitle: string;
    relationType: RelationType;
}

export interface AddRelationRequest {
    sourcePointId: number;
    targetPointId: number;
    relationType: RelationType;
}
export enum KnowledgeExtractionAction {
    NEW = 'NEW',
    UPDATE = 'UPDATE',
    REDUNDANT = 'REDUNDANT'
}

export interface ExtractedKnowledgePoint {
    title: string;
    content: string;
    category?: string;
    action: KnowledgeExtractionAction;
    existingPointId?: number;
    timestamps: { start: number; end: number }[];
}

export interface VideoProcessResultVo {
    video: VideoVo;
    extractedPoints: ExtractedKnowledgePoint[];
    score: VideoScoreVo;
}

// ----------------- Draft Module -----------------
export enum DraftStatus {
    PENDING = 'PENDING',
    APPROVED = 'APPROVED',
    REJECTED = 'REJECTED'
}

export interface KnowledgePointDraftVo {
    id: number;
    videoId: number;
    frameworkId: number;
    title: string;
    content: string;
    category?: string;
    action?: KnowledgeExtractionAction;
    existingPointId?: number;
    timestamps?: string;
    aiSuggestion?: string;
    status: DraftStatus;
    reviewComment?: string;
    createTime: string;
    updateTime: string;
}

export interface KnowledgePointDraftRequest {
    videoId: number;
    frameworkId: number;
    title: string;
    content: string;
    category?: string;
    aiSuggestion?: string;
}

export interface ReviewDraftRequest {
    approved: boolean;
    comment?: string;
    title?: string;
    content?: string;
    category?: string;
}

export interface VideoUploadReqVo {
    originalFileName: string;
    ossObjectName: string;
    ossUrl: string;
    fileSize: number;
    duration: number;
    frameworkId: number;
}

export interface PostUploadResponse {
    postUrl: string;      // POST 目标地址
    policy: string;       // Base64 Policy
    signature: string;    // 签名
    accessKeyId: string;  // AK
    objectName: string;   // OBS 对象键
    obsUrl: string;       // 公开访问 URL
    bucket: string;
    endpoint: string;
}
