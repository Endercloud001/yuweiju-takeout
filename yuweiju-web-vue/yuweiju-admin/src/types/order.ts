export interface OrderStatusStatistics {
  confirmed: number
  deliveryInProgress: number
  toBeConfirmed: number
}

export interface OrderListItem {
  id: number
  number: string
  status: number
  amount: number
  address?: string
  remark?: string
  estimatedDeliveryTime?: string
  deliveryTime?: string
  orderDishes?: string
  riskScore?: number | null
  riskLevel?: string | null
  riskReasons?: string | null
  modelVersion?: string | null
}

export interface OrderConditionQuery {
  page: number
  pageSize: number
  number?: string
  phone?: string
  status?: number | string
  riskLevel?: string
  minRiskScore?: number
  beginTime?: string
  endTime?: string
}

export interface OrderDetail {
  id: number
  number: string
  status: number
  orderTime?: string
  consignee?: string
  phone?: string
  deliveryTime?: string
  estimatedDeliveryTime?: string
  address?: string
  cancelReason?: string
  rejectionReason?: string
  remark?: string
  orderDetailList?: Array<{
    name: string
    number?: number
    amount?: number
  }>
  amount?: number
  riskScore?: number | null
  riskLevel?: string | null
  riskReasons?: string | null
  modelVersion?: string | null
  decisionStatus?: string
}

export interface OrderRiskModelMeta {
  modelVersion: string
  artifactPath: string
  status: 'ACTIVE' | 'REJECTED' | 'ARCHIVED' | string
  trainWindowStart?: string
  trainWindowEnd?: string
  createdAt?: string
  metrics?: Record<string, unknown>
}

export interface OrderRiskFeedbackPayload {
  orderId: number
  decision: 'approve' | 'reject' | 'review'
  reason?: string
}

export interface OrderRiskTrainingReadiness {
  ready: boolean
  blockedReason: string
  featureVersion: string
  minAnomalyLabels: number
  trainWindowStart?: string
  trainWindowEnd?: string
  holdoutWindowStart?: string
  holdoutWindowEnd?: string
  windowOrders: number
  snapshotOrders: number
  trainSamples: number
  holdoutSamples: number
  trainPositives: number
  holdoutPositives: number
}

export interface OrderRiskBackfillSummary {
  triggered: boolean
  reason?: string
  days: number
  limit: number
  onlyMissing: boolean
  candidateOrders: number
  processedOrders: number
  skippedExisting: number
}
