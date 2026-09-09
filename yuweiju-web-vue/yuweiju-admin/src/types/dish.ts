import type { PageQuery } from './api'

export type DishStatus = 0 | 1

export interface DishFlavor {
  id?: number
  dishId?: number
  name: string
  value: string
}

export interface DishItem {
  id: number
  name: string
  categoryId: number
  categoryName?: string
  price: number
  image?: string
  description?: string
  status: DishStatus
  updateTime?: string
  createTime?: string
}

export interface DishPageQuery extends PageQuery {
  name?: string
  categoryId?: number
  status?: DishStatus
}

export interface DishSaveBody {
  id?: number
  name: string
  categoryId: number
  price: number
  image?: string
  description?: string
  flavors?: DishFlavor[]
}

