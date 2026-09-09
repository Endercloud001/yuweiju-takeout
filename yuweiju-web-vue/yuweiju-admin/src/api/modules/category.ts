import { request } from '../http'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { CategoryItem, CategoryPageQuery, CategorySaveBody, CategoryStatus, CategoryType } from '../../types/category'

export function getCategoryPage(params: CategoryPageQuery): Promise<ApiResponse<PageResponse<CategoryItem>>> {
  return request<PageResponse<CategoryItem>>({
    url: '/admin/category/page',
    method: 'GET',
    params,
  })
}

export function addCategory(payload: CategorySaveBody): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/category',
    method: 'POST',
    data: payload,
  })
}

export function editCategory(payload: CategorySaveBody & { id: number }): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/category',
    method: 'PUT',
    data: payload,
  })
}

export function deleteCategory(id: number): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/category',
    method: 'DELETE',
    params: { id },
  })
}

export function setCategoryStatus(id: number, status: CategoryStatus): Promise<ApiResponse<string>> {
  return request<string>({
    url: `/admin/category/status/${status}`,
    method: 'POST',
    params: { id },
  })
}

export function getCategoryList(type?: CategoryType): Promise<ApiResponse<CategoryItem[]>> {
  return request<CategoryItem[]>({
    url: '/admin/category/list',
    method: 'GET',
    params: { type },
  })
}
