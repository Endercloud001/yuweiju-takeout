import { request } from '../http'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { DishItem, DishPageQuery, DishSaveBody, DishStatus } from '../../types/dish'

export function getDishPage(params: DishPageQuery): Promise<ApiResponse<PageResponse<DishItem>>> {
  return request<PageResponse<DishItem>>({
    url: '/admin/dish/page',
    method: 'GET',
    params,
  })
}

export function deleteDish(ids: string): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/dish',
    method: 'DELETE',
    params: { ids },
  })
}

export function addDish(payload: DishSaveBody): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/dish',
    method: 'POST',
    data: payload,
  })
}

export function editDish(payload: DishSaveBody & { id: number }): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/dish',
    method: 'PUT',
    data: payload,
  })
}

export function getDishById(id: number): Promise<ApiResponse<DishItem>> {
  return request<DishItem>({
    url: `/admin/dish/${id}`,
    method: 'GET',
  })
}

export function setDishStatus(id: number, status: DishStatus): Promise<ApiResponse<string>> {
  return request<string>({
    url: `/admin/dish/status/${status}`,
    method: 'POST',
    params: { id },
  })
}

