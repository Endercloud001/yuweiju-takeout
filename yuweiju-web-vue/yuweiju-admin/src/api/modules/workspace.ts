import { request } from '../http'
import type { ApiResponse } from '../../types/api'
import type { BusinessData, OverviewOrdersData, OverviewSimpleData } from '../../types/workspace'

export function getBusinessData(): Promise<ApiResponse<BusinessData>> {
  return request<BusinessData>({
    url: '/admin/workspace/businessData',
    method: 'GET',
  })
}

export function getOverviewOrders(): Promise<ApiResponse<OverviewOrdersData>> {
  return request<OverviewOrdersData>({
    url: '/admin/workspace/overviewOrders',
    method: 'GET',
  })
}

export function getOverviewDishes(): Promise<ApiResponse<OverviewSimpleData>> {
  return request<OverviewSimpleData>({
    url: '/admin/workspace/overviewDishes',
    method: 'GET',
  })
}

export function getOverviewSetmeals(): Promise<ApiResponse<OverviewSimpleData>> {
  return request<OverviewSimpleData>({
    url: '/admin/workspace/overviewSetmeals',
    method: 'GET',
  })
}
