import { request } from '../http'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { SetmealItem, SetmealPageQuery, SetmealSaveBody, SetmealStatus } from '../../types/setmeal'

export function getSetmealPage(params: SetmealPageQuery): Promise<ApiResponse<PageResponse<SetmealItem>>> {
  return request<PageResponse<SetmealItem>>({
    url: '/admin/setmeal/page',
    method: 'GET',
    params,
  })
}

export function deleteSetmeal(ids: string): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/setmeal',
    method: 'DELETE',
    params: { ids },
  })
}

export function addSetmeal(payload: SetmealSaveBody): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/setmeal',
    method: 'POST',
    data: payload,
  })
}

export function editSetmeal(payload: SetmealSaveBody & { id: number }): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/setmeal',
    method: 'PUT',
    data: payload,
  })
}

export function getSetmealById(id: number): Promise<ApiResponse<SetmealItem>> {
  return request<SetmealItem>({
    url: `/admin/setmeal/${id}`,
    method: 'GET',
  })
}

export function setSetmealStatus(id: number, status: SetmealStatus): Promise<ApiResponse<string>> {
  return request<string>({
    url: `/admin/setmeal/status/${status}`,
    method: 'POST',
    params: { id },
  })
}

