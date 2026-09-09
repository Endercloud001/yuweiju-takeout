export type ElTagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

export const orderStatusLabelMap: Record<number, string> = {
  1: '待付款',
  2: '待接单',
  3: '待派送',
  4: '派送中',
  5: '已完成',
  6: '已取消',
}

export const orderStatusTagTypeMap: Record<number, ElTagType> = {
  1: 'info',
  2: 'warning',
  3: 'primary',
  4: 'primary',
  5: 'success',
  6: 'danger',
}

export function getOrderStatusLabel(status?: number | null) {
  if (typeof status !== 'number') return ''
  return orderStatusLabelMap[status] ?? `状态${status}`
}

export function getOrderStatusTagType(status?: number | null): ElTagType {
  if (typeof status !== 'number') return 'info'
  return orderStatusTagTypeMap[status] ?? 'info'
}

export function noop(): void {}
