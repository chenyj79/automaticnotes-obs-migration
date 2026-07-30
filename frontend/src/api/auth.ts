import request from './request';
import type { ProcessResult, LoginResponse, UserVo } from '@/types';

// Depending on the exact payload of DTOs, we define request types
export interface LoginRequestData {
    username?: string;
    password?: string;
}

export interface RegisterRequestData {
    username?: string;
    password?: string;
    email?: string; // Only if registration needs it, based on AuthController it's just user/pass
}

export const loginAPI = (data: LoginRequestData) => {
    return request.post<any, ProcessResult<LoginResponse>>('/auth/login', data);
};

export const registerAPI = (data: RegisterRequestData) => {
    return request.post<any, ProcessResult<UserVo>>('/auth/register', data);
};
