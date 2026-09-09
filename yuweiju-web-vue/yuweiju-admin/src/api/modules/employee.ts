import { request } from '../http'
import type { ApiResponse } from '../../types/api'
import type {
  EmployeeEditPasswordBody,
  EmployeeLoginBody,
  EmployeeLoginData,
  EmployeePageQuery,
  EmployeeSaveBody,
  EmployeeStatusBody,
  EmployeeItem,
} from '../../types/employee'

export function employeeLogin(payload: EmployeeLoginBody): Promise<ApiResponse<EmployeeLoginData>> {
  return request<EmployeeLoginData>({
    url: '/admin/employee/login',
    method: 'POST',
    data: payload,
  })
}

export function employeeLogout(): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/employee/logout',
    method: 'POST',
  })
}

export function employeeEditPassword(payload: EmployeeEditPasswordBody): Promise<ApiResponse<string>> {
  return request<string>({
    url: '/admin/employee/editPassword',
    method: 'PUT',
    data: payload,
  })
}

export function getEmployeePage(payload: EmployeePageQuery): Promise<ApiResponse<{ total: number; records: EmployeeItem[] }>> {
  return request({
    url: '/admin/employee/page',
    method: 'GET',
    params: payload,
  })
}

export function addEmployee(payload: EmployeeSaveBody): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/employee',
    method: 'POST',
    data: payload,
  })
}

export function editEmployee(payload: EmployeeSaveBody): Promise<ApiResponse<null>> {
  return request<null>({
    url: '/admin/employee',
    method: 'PUT',
    data: payload,
  })
}

export function getEmployeeById(id: number): Promise<ApiResponse<EmployeeItem>> {
  return request<EmployeeItem>({
    url: `/admin/employee/${id}`,
    method: 'GET',
  })
}

export function updateEmployeeStatus(payload: EmployeeStatusBody): Promise<ApiResponse<null>> {
  return request<null>({
    url: `/admin/employee/status/${payload.status}`,
    method: 'POST',
    params: { id: payload.id },
  })
}
