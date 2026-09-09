import { request, requestBlob } from '../http'
import type { ApiResponse } from '../../types/api'

export interface ReportQuery {
  begin: string
  end: string
}

export interface TurnoverStatistics {
  dateList: string[]
  turnoverList: number[]
}

export interface UserStatistics {
  dateList: string[]
  newUserList: number[]
  totalUserList: number[]
}

export interface OrdersStatistics {
  dateList: string[]
  orderCountList: number[]
  validOrderCountList: number[]
  orderCompletionRate: number
  totalOrderCount: number
  validOrderCount: number
}

export interface Top10 {
  nameList: string[]
  numberList: number[]
}

export function getTurnoverStatistics(payload: ReportQuery): Promise<ApiResponse<TurnoverStatistics>> {
  return request<TurnoverStatistics>({
    url: '/admin/report/turnoverStatistics',
    method: 'GET',
    params: payload,
  })
}

export function getUserStatistics(payload: ReportQuery): Promise<ApiResponse<UserStatistics>> {
  return request<UserStatistics>({
    url: '/admin/report/userStatistics',
    method: 'GET',
    params: payload,
  })
}

export function getOrdersStatistics(payload: ReportQuery): Promise<ApiResponse<OrdersStatistics>> {
  return request<OrdersStatistics>({
    url: '/admin/report/ordersStatistics',
    method: 'GET',
    params: payload,
  })
}

export function getTop10(payload: ReportQuery): Promise<ApiResponse<Top10>> {
  return request<Top10>({
    url: '/admin/report/top10',
    method: 'GET',
    params: payload,
  })
}

export function exportReport(payload: ReportQuery): Promise<Blob> {
  return requestBlob({
    url: '/admin/report/export',
    method: 'GET',
    params: payload,
  })
}
