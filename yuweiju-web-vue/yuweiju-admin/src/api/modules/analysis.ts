import { request } from '../http'
import type { ApiResponse } from '../../types/api'

export interface AnalysisListQuery {
  date: string
  limit?: number
}

export interface HeatPredictionItem {
  dishId: number
  dishName: string | null
  windowStart: string
  windowEnd: string
  predictedSalesQty: number
  heatScore: number
  modelVersion: string
  featureVersion: string
  explainJson: string | null
}

export interface UserClusterItem {
  userId: number
  userName: string | null
  snapshotDate: string
  clusterId: number
  clusterScore: number
  modelVersion: string
  featureVersion: string
  topDishIds: number[]
}

export function getHeatPredictionList(params: AnalysisListQuery): Promise<ApiResponse<HeatPredictionItem[]>> {
  return request<HeatPredictionItem[]>({
    url: '/admin/analysis/heat',
    method: 'GET',
    params,
  })
}

export function getUserClusterList(params: AnalysisListQuery): Promise<ApiResponse<UserClusterItem[]>> {
  return request<UserClusterItem[]>({
    url: '/admin/analysis/clusters',
    method: 'GET',
    params,
  })
}

export function runAnalysisTraining(params?: { date?: string }): Promise<ApiResponse<{ message: string }>> {
  return request<{ message: string }>({
    url: '/admin/analysis/training/run',
    method: 'POST',
    params,
  })
}
