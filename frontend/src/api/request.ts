import axios, { type InternalAxiosRequestConfig, type AxiosResponse, AxiosError } from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
    baseURL: '/api',
    timeout: 120000, // 增加到 2 分钟，支持大文件上传
});

// Request Interceptor
request.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        // Inject Token
        const token = localStorage.getItem('token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response Interceptor
request.interceptors.response.use(
    (response: AxiosResponse) => {
        const res = response.data;

        // ProcessResult structure: { success: boolean, message: string, data: any, code: number }
        // If backend returns a non-success via custom response object
        if (res && res.code && res.code !== 200 && res.success === false) {
            ElMessage.error(res.message || 'Error occurred');

            if (res.code === 401 || res.code === 403) {
                localStorage.removeItem('token');
                localStorage.removeItem('user');
                window.location.href = '/login';
            }
            return Promise.reject(new Error(res.message || 'Error'));
        }

        return res; // usually ProcessResult object
    },
    (error: AxiosError) => {
        let msg = 'Network Error';
        if (error.response) {
            const errorData = error.response.data as any;
            if (errorData && errorData.message) {
                msg = errorData.message;
            } else if (error.response.status === 401 || error.response.status === 403) {
                msg = error.response.status === 401 ? 'Unauthorized, please login again.' : 'Session expired, please login again.';
                localStorage.removeItem('token');
                localStorage.removeItem('user');
                window.location.href = '/login';
            } else if (error.response.status >= 500) {
                msg = 'Server Error. Please try again later.';
            }
        }

        ElMessage.error(msg);
        return Promise.reject(error);
    }
);

export default request;
