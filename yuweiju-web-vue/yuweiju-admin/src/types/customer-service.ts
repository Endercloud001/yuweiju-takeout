export interface CustomerServiceSessionSummary {
  id: number
  userId: number
  userName: string
  userAvatar: string
  status: number
  updateTime: string
  lastMessagePreview?: string
}

export interface CustomerServiceMessage {
  id: number
  sessionId: number
  senderType: number
  senderId?: number
  content: string
  createTime: string
}

export interface CustomerServiceMessageListQuery {
  sessionId: number
  beforeId?: number
  limit?: number
}

export interface CustomerServiceCloseBody {
  sessionId: number
  closeReason?: string
}
