import { request } from '../http'
import type { ApiResponse } from '../../types/api'
import type { DefaultImageItem, DefaultImageQuery } from '../../types/common'

export function getDefaultImages(params: DefaultImageQuery): Promise<ApiResponse<DefaultImageItem[]>> {
  return request<DefaultImageItem[]>({
    url: '/admin/common/default-images',
    method: 'GET',
    params,
  })
}