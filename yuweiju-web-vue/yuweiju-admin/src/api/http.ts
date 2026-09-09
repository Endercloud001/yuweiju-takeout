import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import Cookies from 'js-cookie'
import type { ApiResponse } from '../types/api'

export const apiBase = import.meta.env.VITE_API_BASE

const http = axios.create({
  baseURL: apiBase,
  timeout: 600000,
})

http.interceptors.request.use((config) => {
  const token = Cookies.get('token')
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.token = token
  }
  return config
})

function isBlobResponse(response: AxiosResponse) {
  return response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer'
}

http.interceptors.response.use((response) => {
  if (isBlobResponse(response)) return response
  return response
})

export async function request<T>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  const response = await http.request<ApiResponse<T>>(config)
  const data = response.data
  if (Number(data.code) !== 1) {
    throw new Error(data.msg || '请求失败')
  }
  return data
}

export async function requestBlob(config: AxiosRequestConfig): Promise<Blob> {
  const response = await http.request<Blob>({ ...config, responseType: 'blob' })
  return response.data
}
