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

export interface AnalysisStage {
  stage: string
  status: 'COMPLETE' | 'PARTIAL' | 'SKIPPED' | 'FAILED'
  reason: string | null
  windowStart: string
  windowEnd: string
  outputs: Record<string, unknown>
}

export interface AnalysisRun {
  message: string
  snapshotDate: string
  executed: boolean
  status: AnalysisStage['status']
  executionMode: 'SYNCHRONOUS'
  heatModelTrained?: boolean
  stages: AnalysisStage[]
}

export function runAnalysisTraining(params?: { date?: string }): Promise<ApiResponse<AnalysisRun>> {
  return request<AnalysisRun>({
    url: '/admin/analysis/training/run', method: 'POST', params,
  })
}

export function getOfflineMetrics(date: string): Promise<ApiResponse<Record<string, unknown>>> {
  return request<Record<string, unknown>>({ url: '/admin/analysis/metrics/offline', method: 'GET', params: { date } })
}
