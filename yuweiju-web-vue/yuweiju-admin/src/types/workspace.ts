export interface BusinessData {
  turnover: number
  validOrderCount: number
  orderCompletionRate: number
  unitPrice: number
  newUsers: number
}

export interface OverviewOrdersData {
  allOrders: number
  cancelledOrders: number
  completedOrders: number
  deliveredOrders: number
  waitingOrders: number
}

export interface OverviewSimpleData {
  discontinued: number
  sold: number
}
