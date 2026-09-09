export interface AiAssistantSessionSummary {
  id: number
  userId: number
  userName: string
  userAvatar: string
  status: number
  updateTime: string
  lastMessagePreview?: string
}

export interface AiAssistantMessage {
  id: number
  sessionId: number
  senderType: number
  senderId?: number
  content: string
  intent?: string
  metadata?: string
  createTime: string
}

export interface AiAssistantMessageListQuery {
  sessionId: number
  beforeId?: number
  limit?: number
}

