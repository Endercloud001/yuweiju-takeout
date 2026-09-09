import { request } from '../http'
import type { ApiResponse, PageResponse } from '../../types/api'
import type {
  OrderStatusStatistics,
  OrderListItem,
  OrderConditionQuery,
  OrderDetail,
  OrderRiskFeedbackPayload,
  OrderRiskModelMeta,
  OrderRiskTrainingReadiness,
  OrderRiskBackfillSummary,
} from '../../types/order'

export function getOrderStatusStatistics(): Promise<ApiResponse<OrderStatusStatistics>> {
  return request<OrderStatusStatistics>({
    url: '/admin/order/statistics',
    method: 'GET',
  })
}

export function getOrderPage(params: OrderConditionQuery): Promise<ApiResponse<PageResponse<OrderListItem>>> {
  return request<PageResponse<OrderListItem>>({
    url: '/admin/order/conditionSearch',
    method: 'GET',
    params,
  })
}

export function getOrderDetails(id: number): Promise<ApiResponse<OrderDetail>> {
  return request<OrderDetail>({
    url: `/admin/order/details/${id}`,
    method: 'GET',
  })
}

export function confirmOrder(id: number): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/order/confirm',
    method: 'PUT',
    data: { id },
  })
}

export function rejectOrder(id: number, rejectionReason: string): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/order/rejection',
    method: 'PUT',
    data: { id, rejectionReason },
  })
}

export function cancelOrder(id: number, cancelReason: string): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/order/cancel',
    method: 'PUT',
    data: { id, cancelReason },
  })
}

export function deliveryOrder(id: number): Promise<ApiResponse<null>> {
  return request<null>({
    url: `/admin/order/delivery/${id}`,
    method: 'PUT',
  })
}

export function completeOrder(id: number): Promise<ApiResponse<null>> {
  return request<null>({
    url: `/admin/order/complete/${id}`,
    method: 'PUT',
  })
}

export function runOrderRiskTraining(): Promise<ApiResponse<{ message: string }>> {
  return request<{ message: string }>({
    url: '/admin/order-risk/training/run',
    method: 'POST',
  })
}

export function getOrderRiskModels(limit = 20): Promise<ApiResponse<OrderRiskModelMeta[]>> {
  return request<OrderRiskModelMeta[]>({
    url: '/admin/order-risk/models',
    method: 'GET',
    params: { limit },
  })
}

export function getOrderRiskReplay(modelVersion?: string): Promise<ApiResponse<Record<string, unknown>>> {
  return request<Record<string, unknown>>({
    url: '/admin/order-risk/replay',
    method: 'GET',
    params: modelVersion ? { modelVersion } : undefined,
  })
}

export function submitOrderRiskFeedback(payload: OrderRiskFeedbackPayload): Promise<ApiResponse<Record<string, unknown>>> {
  return request<Record<string, unknown>>({
    url: '/admin/order-risk/feedback',
    method: 'POST',
    data: payload,
  })
}

export function getOrderRiskTrainingReadiness(): Promise<ApiResponse<OrderRiskTrainingReadiness>> {
  return request<OrderRiskTrainingReadiness>({
    url: '/admin/order-risk/training/readiness',
    method: 'GET',
  })
}

export function runOrderRiskBackfill(params?: {
  days?: number
  limit?: number
  onlyMissing?: boolean
}): Promise<ApiResponse<OrderRiskBackfillSummary>> {
  return request<OrderRiskBackfillSummary>({
    url: '/admin/order-risk/backfill/run',
    method: 'POST',
    params,
  })
}
