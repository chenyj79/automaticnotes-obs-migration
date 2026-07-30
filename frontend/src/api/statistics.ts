import request from './request';
import type { ProcessResult } from '@/types';

export interface DashboardStatisticsDto {
  videoCount: number;
  frameworkCount: number;
  knowledgePointCount: number;
  averageScore: number;
}

export const getDashboardStatisticsAPI = () => {
  return request.get<any, ProcessResult<DashboardStatisticsDto>>('/statistics/user');
};
