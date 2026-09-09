import type { PageQuery } from './api'

export type CategoryType = 1 | 2
export type CategoryStatus = 0 | 1

export interface CategoryItem {
  id: number
  name: string
  type: CategoryType
  sort: number
  status: CategoryStatus
  createTime?: string
  updateTime?: string
}

export interface CategoryPageQuery extends PageQuery {
  name?: string
  type?: CategoryType
}

export interface CategorySaveBody {
  id?: number
  name: string
  type: CategoryType
  sort: number
}

