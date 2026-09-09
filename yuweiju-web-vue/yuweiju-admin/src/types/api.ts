export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface PageResponse<T> {
  total: number
  records: T[]
}

export interface PageQuery {
  page: number
  pageSize: number
}
