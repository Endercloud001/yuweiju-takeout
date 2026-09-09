import { request } from '../http'
import type { ApiResponse } from '../../types/api'
import type {
  AiAssistantMessage,
  AiAssistantMessageListQuery,
  AiAssistantSessionSummary,
} from '../../types/ai-assistant'

export function getAiAssistantOpenSessions(): Promise<ApiResponse<AiAssistantSessionSummary[]>> {
  return request<AiAssistantSessionSummary[]>({
    url: '/admin/aiAssistant/session/openList',
    method: 'GET',
  })
}

export function getAiAssistantMessages(
  params: AiAssistantMessageListQuery,
): Promise<ApiResponse<AiAssistantMessage[]>> {
  return request<AiAssistantMessage[]>({
    url: '/admin/aiAssistant/message/list',
    method: 'GET',
    params,
  })
}

