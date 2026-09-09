import { request } from '../http'
import type { ApiResponse } from '../../types/api'

export type ShopStatus = 0 | 1

export function getShopStatus(): Promise<ApiResponse<ShopStatus>> {
  return request<ShopStatus>({
    url: '/admin/shop/status',
    method: 'GET',
  })
}

export function setShopStatus(status: ShopStatus): Promise<ApiResponse<string>> {
  return request<string>({
    url: `/admin/shop/${status}`,
    method: 'PUT',
  })
}
