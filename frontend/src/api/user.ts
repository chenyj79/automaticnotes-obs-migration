import request from './request';
import type { ProcessResult, UserVo } from '@/types';

export interface UpdateProfileRequest {
    email?: string;
    phone?: string;
}

export interface ChangePasswordRequest {
    oldPassword?: string;
    newPassword?: string;
}

export const getCurrentUserAPI = () => {
    return request.get<any, ProcessResult<UserVo>>('/user/me');
};

export const updateProfileAPI = (data: UpdateProfileRequest) => {
    return request.put<any, ProcessResult<UserVo>>('/user/profile', data);
};

export const changePasswordAPI = (data: ChangePasswordRequest) => {
    return request.put<any, ProcessResult<void>>('/user/password', data);
};

export const uploadAvatarAPI = (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<any, ProcessResult<string>>('/user/avatar', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
};
