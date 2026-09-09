import { request } from '../http'
import type { ApiResponse } from '../../types/api'
import type {
  CustomerServiceSessionSummary,
  CustomerServiceMessage,
  CustomerServiceMessageListQuery,
  CustomerServiceCloseBody,
} from '../../types/customer-service'

export function getCustomerServiceOpenSessions(): Promise<ApiResponse<CustomerServiceSessionSummary[]>> {
  return request<CustomerServiceSessionSummary[]>({
    url: '/admin/customerService/session/openList',
    method: 'GET',
  })
}

export function getCustomerServiceMessages(
  params: CustomerServiceMessageListQuery,
): Promise<ApiResponse<CustomerServiceMessage[]>> {
  return request<CustomerServiceMessage[]>({
    url: '/admin/customerService/message/list',
    method: 'GET',
    params,
  })
}

export function closeCustomerServiceSession(
  body: CustomerServiceCloseBody,
): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/customerService/session/close',
    method: 'POST',
    data: body,
  })
}
