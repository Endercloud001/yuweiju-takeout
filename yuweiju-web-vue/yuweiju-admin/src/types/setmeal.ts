import type { PageQuery } from './api'

export type SetmealStatus = 0 | 1

export interface SetmealDishItem {
  id?: number
  setmealId?: number
  dishId: number
  name?: string
  price?: number
  copies: number
}

export interface SetmealItem {
  id: number
  name: string
  categoryId: number
  categoryName?: string
  price: number
  image?: string
  description?: string
  status: SetmealStatus
  setmealDishes?: SetmealDishItem[]
  updateTime?: string
  createTime?: string
}

export interface SetmealPageQuery extends PageQuery {
  name?: string
  categoryId?: number
  status?: SetmealStatus
}

export interface SetmealSaveBody {
  id?: number
  name: string
  categoryId: number
  price: number
  image?: string
  description?: string
  setmealDishes?: SetmealDishItem[]
}
