import request from './request';
import type { ProcessResult, VideoVo, Page, VideoSegmentVo, VideoScoreVo, VideoProcessResultVo, VideoUploadReqVo, PostUploadResponse } from '@/types';

export const uploadVideoAPI = (dto: VideoUploadReqVo) => {
    return request.post<any, ProcessResult<VideoVo>>('/video/upload', dto);
};

export const processVideoAPI = (dto: VideoUploadReqVo) => {
    return request.post<any, ProcessResult<number>>('/video/process', dto);
};

export const getObsUploadSignatureAPI = (fileExtension: string, folder: string = 'video') => {
    return request.get<any, ProcessResult<PostUploadResponse>>('/obs/upload-signature', {
        params: { fileExtension, folder }
    });
};

export const getTaskStatusAPI = (taskId: string | number) => {
    return request.get<any, ProcessResult<string>>(`/task/${taskId}/status`);
};

export const getSseTicketAPI = (taskId: string | number) => {
    return request.post<any, ProcessResult<string>>(`/task/${taskId}/sse-ticket`);
};

export const getTaskResultAPI = (taskId: string | number) => {
    return request.get<any, ProcessResult<VideoProcessResultVo>>(`/task/${taskId}/result`);
};

export const markTaskReportViewedAPI = (taskId: string | number) => {
    return request.put<any, ProcessResult<void>>(`/task/${taskId}/viewed`);
};

export const getVideoTaskStatusAPI = (videoId: string | number) => {
    return request.get<any, ProcessResult<string>>(`/task/video/${videoId}/status`);
};

export const getPendingTasksAPI = (frameworkId: string | number) => {
    return request.get<any, ProcessResult<any[]>>(`/task/framework/${frameworkId}/pending`);
};

export const getVideoProcessResultAPI = (id: string | number) => {
    return request.get<any, ProcessResult<VideoProcessResultVo>>(`/video/${id}/result`);
};

export const getMyVideosAPI = (params: { page?: number; size?: number }) => {
    return request.get<any, ProcessResult<Page<VideoVo>>>('/video/my', { params });
};

export const getVideoDetailAPI = (id: string | number) => {
    return request.get<any, ProcessResult<VideoVo>>(`/video/${id}`);
};

export const getVideoSegmentsAPI = (id: string | number) => {
    return request.get<any, ProcessResult<VideoSegmentVo[]>>(`/video/${id}/segments`);
};

export const getVideoScoreAPI = (id: string | number) => {
    return request.get<any, ProcessResult<VideoScoreVo>>(`/video-score/video/${id}`);
};

export const getFrameworkVideosAPI = (frameworkId: string | number) => {
    return request.get<any, ProcessResult<VideoVo[]>>(`/video/framework/${frameworkId}`);
};

export const cancelTaskAPI = (taskId: string | number) => {
    return request.put<any, ProcessResult<void>>(`/task/${taskId}/cancel`);
};

export const deleteVideoAPI = (id: string | number) => {
    return request.delete<any, ProcessResult<void>>(`/video/${id}`);
};
