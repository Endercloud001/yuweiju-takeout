<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import { formatDateTime } from '../../utils/format'
import avatarMedalUrl from '../../assets/reference_images/medal_activity_40side_105.png?url'
import {
  closeCustomerServiceSession,
  getCustomerServiceMessages,
  getCustomerServiceOpenSessions,
} from '../../api/modules/customer-service'
import {
  getAiAssistantMessages,
  getAiAssistantOpenSessions,
} from '../../api/modules/ai-assistant'
import type {
  CustomerServiceMessage,
  CustomerServiceSessionSummary,
  CustomerServiceMessageListQuery,
} from '../../types/customer-service'
import type {
  AiAssistantMessage,
  AiAssistantMessageListQuery,
  AiAssistantSessionSummary,
} from '../../types/ai-assistant'

type ServiceMode = 'manual' | 'ai'
type SessionSummary = CustomerServiceSessionSummary | AiAssistantSessionSummary
type ChatMessage = CustomerServiceMessage | AiAssistantMessage

type WsStatus = 'connecting' | 'open' | 'closed'

interface WsEnvelope<T = unknown> {
  type: string
  data?: T
  message?: string
}

interface ReplyLock {
  sessionId: number
  adminId: number
  adminName: string
  expiresAt: number
}

const userStore = useUserStore()
const currentMode = ref<ServiceMode>('manual')
const sessionList = ref<SessionSummary[]>([])
const activeSessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const hasMore = ref(false)
const draft = ref('')
const wsStatus = ref<WsStatus>('closed')
const wsError = ref('')
const lockMap = reactive<Record<number, ReplyLock>>({})
const lockHeartbeatAt = ref(0)
const refreshTimer = ref<number | null>(null)
const reconnectTimer = ref<number | null>(null)
const reconnectAttempts = ref(0)
const maxReconnectAttempts = 6
const messageListRef = ref<HTMLDivElement | null>(null)

let ws: WebSocket | null = null

const activeSession = computed(() =>
  sessionList.value.find((item) => item.id === activeSessionId.value) || null,
)
const activeLock = computed(() =>
  activeSessionId.value ? lockMap[activeSessionId.value] : undefined,
)
const currentAdminId = computed(() => userStore.userInfo?.id ?? 0)
const isActiveLockOwner = computed(() => {
  if (!activeLock.value) return true
  return activeLock.value.adminId === currentAdminId.value
})
const canReply = computed(() => {
  if (currentMode.value !== 'manual') return false
  if (!activeSession.value) return false
  if (wsStatus.value !== 'open') return false
  if (!activeLock.value) return true
  return activeLock.value.adminId === currentAdminId.value
})
const lockHint = computed(() => {
  if (currentMode.value !== 'manual') return 'AI 会话仅支持只读查看'
  if (!activeSession.value) return '请选择会话后开始回复'
  if (wsStatus.value !== 'open') return '连接中，稍后可回复'
  if (!activeLock.value) return '点击输入框开始回复'
  if (activeLock.value.adminId === currentAdminId.value) return '你正在回复'
  return `${activeLock.value.adminName || '其他客服'} 正在回复`
})

function normalizePreview(text?: string) {
  if (!text) return '暂无消息'
  return text.length > 40 ? `${text.slice(0, 40)}...` : text
}


function renderMessageContent(message: ChatMessage) {
  const content = message.content || ''
  if (currentMode.value !== 'ai' || message.senderType !== 2) return content
  const metadata = (message as AiAssistantMessage).metadata
  if (!metadata) return content
  try {
    const parsed = JSON.parse(metadata) as { dishNames?: string[] }
    const dishNames = Array.isArray(parsed.dishNames)
      ? parsed.dishNames.filter((item) => typeof item === 'string' && item.trim().length > 0)
      : []
    if (!dishNames.length) return content
    const missing = dishNames.filter((name) => !content.includes(name))
    if (!missing.length) return content
    return `${content}\n${missing.map((name) => `- ${name}`).join('\n')}`
  } catch {
    return content
  }
}

function resolveWsBase() {
  const explicit = (import.meta.env.VITE_WS_BASE as string | undefined) || ''
  if (explicit) return explicit
  const proxyTarget = (import.meta.env.VITE_PROXY_TARGET as string | undefined) || ''
  if (proxyTarget) return proxyTarget
  const apiBase = (import.meta.env.VITE_API_BASE as string | undefined) || ''
  if (apiBase.startsWith('http://') || apiBase.startsWith('https://')) {
    return apiBase
  }
  return window.location.origin
}

function normalizeWsBase(base: string) {
  const clean = base.replace(/\/api\/?$/, '')
  if (clean.startsWith('http://')) return clean.replace('http://', 'ws://')
  if (clean.startsWith('https://')) return clean.replace('https://', 'wss://')
  return clean
}

function buildWsUrl() {
  const token = Cookies.get('token') || userStore.token
  const base = normalizeWsBase(resolveWsBase())
  const query = new URLSearchParams()
  if (token) query.set('token', token)
  return `${base}/ws/customer-service/admin?${query.toString()}`
}

function connectWs() {
  if (currentMode.value !== 'manual') return
  if (wsStatus.value === 'connecting' || wsStatus.value === 'open') return
  wsStatus.value = 'connecting'
  wsError.value = ''
  const url = buildWsUrl()
  ws = new WebSocket(url)
  ws.onopen = () => {
    wsStatus.value = 'open'
    reconnectAttempts.value = 0
  }
  ws.onmessage = (event) => {
    handleWsMessage(event.data)
  }
  ws.onerror = () => {
    wsError.value = '连接异常'
  }
  ws.onclose = () => {
    wsStatus.value = 'closed'
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (currentMode.value !== 'manual') return
  if (reconnectAttempts.value >= maxReconnectAttempts) {
    wsError.value = '连接已断开，请刷新页面'
    return
  }
  if (reconnectTimer.value) window.clearTimeout(reconnectTimer.value)
  reconnectAttempts.value += 1
  const delay = Math.min(2000 + reconnectAttempts.value * 1500, 12000)
  reconnectTimer.value = window.setTimeout(() => {
    connectWs()
  }, delay)
}

function handleWsMessage(raw: string) {
  let payload: WsEnvelope
  try {
    payload = JSON.parse(raw) as WsEnvelope
  } catch {
    return
  }
  if (!payload?.type) return

  if (payload.type === 'CHAT_MESSAGE' && payload.data) {
    if (wsStatus.value !== 'open') wsStatus.value = 'open'
    const message = payload.data as CustomerServiceMessage
    handleIncomingMessage(message)
    return
  }
  if (payload.type === 'TYPING_LOCKED' && payload.data) {
    const data = payload.data as ReplyLock
    if (data.sessionId) {
      lockMap[data.sessionId] = data
    }
    return
  }
  if (payload.type === 'TYPING_UNLOCKED' && payload.data) {
    const data = payload.data as { sessionId: number }
    if (data.sessionId) {
      delete lockMap[data.sessionId]
    }
    return
  }
  if (payload.type === 'ERROR') {
    ElMessage.warning(payload.message || '操作失败')
  }
}

function handleIncomingMessage(message: ChatMessage) {
  const session = sessionList.value.find((item) => item.id === message.sessionId)
  if (session) {
    session.lastMessagePreview = normalizePreview(renderMessageContent(message))
    session.updateTime = message.createTime
    sessionList.value = [session, ...sessionList.value.filter((item) => item.id !== session.id)]
  }
  if (activeSessionId.value === message.sessionId) {
    messages.value.push(message)
    scrollToBottom()
  }
}

function sendWs(type: string, payload: Record<string, unknown>) {
  if (!ws || wsStatus.value !== 'open') {
    ElMessage.warning('连接尚未建立')
    return
  }
  ws.send(JSON.stringify({ type, ...payload }))
}

async function refreshSessions() {
  loadingSessions.value = true
  try {
    const res =
      currentMode.value === 'manual'
        ? await getCustomerServiceOpenSessions()
        : await getAiAssistantOpenSessions()
    sessionList.value = res.data || []
    if (!activeSessionId.value && sessionList.value.length) {
      selectSession(sessionList.value[0])
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取会话列表失败'
    ElMessage.error(message)
  } finally {
    loadingSessions.value = false
  }
}

async function selectSession(session: SessionSummary) {
  if (activeSessionId.value === session.id) return
  if (currentMode.value === 'manual') {
    releaseLock()
  }
  activeSessionId.value = session.id
  messages.value = []
  hasMore.value = false
  await fetchMessages()
  scrollToBottom()
}

async function fetchMessages(beforeId?: number) {
  if (!activeSessionId.value) return
  loadingMessages.value = true
  try {
    const params = {
      sessionId: activeSessionId.value,
      beforeId,
      limit: 50,
    } as CustomerServiceMessageListQuery | AiAssistantMessageListQuery
    const res =
      currentMode.value === 'manual'
        ? await getCustomerServiceMessages(params as CustomerServiceMessageListQuery)
        : await getAiAssistantMessages(params as AiAssistantMessageListQuery)
    const list = res.data || []
    if (beforeId) {
      messages.value = [...list, ...messages.value]
    } else {
      messages.value = list
    }
    hasMore.value = list.length >= 50
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取消息失败'
    ElMessage.error(message)
  } finally {
    loadingMessages.value = false
  }
}

function loadMore() {
  if (loadingMessages.value || !hasMore.value || !messages.value.length) return
  const firstId = messages.value[0]?.id
  if (firstId) {
    fetchMessages(firstId)
  }
}

function handleFocus() {
  sendLock()
}

function handleBlur() {
  releaseLock()
}

function sendLock() {
  if (currentMode.value !== 'manual') return
  if (!activeSessionId.value || wsStatus.value !== 'open') return
  if (activeLock.value && activeLock.value.adminId !== currentAdminId.value) return
  const now = Date.now()
  if (now - lockHeartbeatAt.value < 8000) return
  lockHeartbeatAt.value = now
  sendWs('TYPING_LOCK', { sessionId: activeSessionId.value })
}

function releaseLock() {
  if (currentMode.value !== 'manual') return
  if (!activeSessionId.value || wsStatus.value !== 'open') return
  if (!activeLock.value || activeLock.value.adminId !== currentAdminId.value) return
  sendWs('TYPING_UNLOCK', { sessionId: activeSessionId.value })
}

function messageClass(message: ChatMessage) {
  if (message.senderType === 3) return 'system'
  if (currentMode.value === 'ai') {
    return message.senderType === 1 ? 'admin' : 'user'
  }
  return message.senderType === 2 ? 'admin' : 'user'
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function sendMessage() {
  if (currentMode.value !== 'manual') return
  if (!canReply.value || !activeSessionId.value) return
  const content = draft.value.trim()
  if (!content) return
  sendWs('CHAT_SEND', { sessionId: activeSessionId.value, content })
  draft.value = ''
}

async function closeActiveSession() {
  if (currentMode.value !== 'manual') return
  if (!activeSession.value) return
  try {
    await closeCustomerServiceSession({ sessionId: activeSession.value.id })
    sessionList.value = sessionList.value.filter((item) => item.id !== activeSession.value?.id)
    messages.value = []
    activeSessionId.value = sessionList.value[0]?.id ?? null
    if (activeSessionId.value) {
      await fetchMessages()
    }
    ElMessage.success('会话已关闭')
  } catch (e) {
    const message = e instanceof Error ? e.message : '关闭会话失败'
    ElMessage.error(message)
  }
}

function scrollToBottom() {
  nextTick(() => {
    const container = messageListRef.value
    if (!container) return
    const last = container.lastElementChild as HTMLElement | null
    if (last) {
      last.scrollIntoView({ block: 'end' })
    }
  })
}

function switchMode(mode: ServiceMode) {
  if (mode === currentMode.value) return
  currentMode.value = mode
}

watch(
  () => draft.value,
  () => {
    if (currentMode.value === 'manual' && draft.value.trim().length > 0) sendLock()
  },
)

watch(
  () => currentMode.value,
  async (mode) => {
    sessionList.value = []
    messages.value = []
    activeSessionId.value = null
    draft.value = ''
    hasMore.value = false
    if (mode === 'manual') {
      connectWs()
    } else if (ws) {
      ws.close()
      ws = null
      wsStatus.value = 'closed'
    }
    await refreshSessions()
  },
)

onMounted(() => {
  refreshSessions()
  if (currentMode.value === 'manual') {
    connectWs()
  }
  refreshTimer.value = window.setInterval(refreshSessions, 15000)
})

onUnmounted(() => {
  if (refreshTimer.value) window.clearInterval(refreshTimer.value)
  if (reconnectTimer.value) window.clearTimeout(reconnectTimer.value)
  releaseLock()
  if (ws) {
    ws.close()
    ws = null
  }
})
</script>

<template>
  <section class="cs-page">
    <div class="cs-body">
      <aside class="cs-sessions">
        <div class="cs-mode-tabs">
          <button
            class="cs-mode-btn"
            :class="{ active: currentMode === 'manual' }"
            type="button"
            @click="switchMode('manual')"
          >
            人工客服
          </button>
          <button
            class="cs-mode-btn"
            :class="{ active: currentMode === 'ai' }"
            type="button"
            @click="switchMode('ai')"
          >
            AI客服
          </button>
        </div>
        <div class="cs-panel-title">进行中会话</div>
        <el-scrollbar class="cs-session-list">
          <div
            v-for="item in sessionList"
            :key="item.id"
            class="cs-session-item"
            :class="{ active: item.id === activeSessionId }"
            @click="selectSession(item)"
          >
            <div class="cs-avatar">
              <img v-if="item.userAvatar" :src="item.userAvatar" alt="" />
              <img v-else :src="avatarMedalUrl" alt="" class="cs-avatar-medal" />
            </div>
            <div class="cs-session-main">
              <div class="cs-session-top">
                <span class="cs-session-name">{{ item.userName || '匿名用户' }}</span>
                <span class="cs-session-time">{{ formatDateTime(item.updateTime) }}</span>
              </div>
              <div class="cs-session-preview">{{ normalizePreview(item.lastMessagePreview) }}</div>
            </div>
            <span
              v-if="currentMode === 'manual' && lockMap[item.id] && lockMap[item.id].adminId !== currentAdminId"
              class="cs-session-lock"
            >
              回复中
            </span>
          </div>
        </el-scrollbar>
        <div v-if="!sessionList.length && !loadingSessions" class="cs-empty">暂无进行中会话</div>
      </aside>

      <main class="cs-chat">
        <header class="cs-chat-header">
          <div class="cs-chat-user">
            <div class="cs-avatar large">
              <img v-if="activeSession?.userAvatar" :src="activeSession?.userAvatar" alt="" />
              <img v-else :src="avatarMedalUrl" alt="" class="cs-avatar-medal" />
            </div>
            <div class="cs-chat-info">
              <div class="cs-chat-name">{{ activeSession?.userName || '余味居客服' }}</div>
              <div class="cs-chat-meta">
                <span v-if="activeSession">会话 ID：{{ activeSession.id }}</span>
                <span v-if="activeLock && !isActiveLockOwner">
                  {{ activeLock.adminName || '其他客服' }} 正在回复
                </span>
                <span v-else-if="activeLock && isActiveLockOwner">你正在回复</span>
                <span v-if="wsError">{{ wsError }}</span>
              </div>
            </div>
          </div>
          <div class="cs-chat-actions">
            <template v-if="currentMode === 'manual'">
              <button class="cs-icon-btn" type="button" :disabled="loadingSessions" @click="refreshSessions">
                <span class="cs-refresh-icon" aria-hidden="true">↻</span>
              </button>
              <div class="cs-ws-status" :data-status="wsStatus">
                <span class="cs-ws-dot"></span>
                <span class="cs-ws-text">{{ wsStatus === 'open' ? '已连接' : '连接中' }}</span>
              </div>
              <button
                class="cs-action-btn cs-action-btn-close"
                type="button"
                :disabled="!activeSession || currentMode !== 'manual'"
                @click="closeActiveSession"
              >
                <span class="transition"></span>
                <span class="gradient"></span>
                <span class="label">关闭会话</span>
              </button>
            </template>
            <div v-else class="cs-ai-note">同一用户仅显示当日对话，过往对话请查数据库</div>
          </div>
        </header>

        <el-scrollbar class="cs-chat-list">
          <div class="cs-chat-list-inner" ref="messageListRef">
            <button v-if="hasMore && currentMode === 'manual'" class="cs-load-more" @click="loadMore" :disabled="loadingMessages">
              {{ loadingMessages ? '鍔犺浇涓?..' : '鍔犺浇鏇村' }}
            </button>
            <div
              v-for="message in messages"
              :key="message.id"
              class="cs-message"
              :class="messageClass(message)"
            >
              <div v-if="message.senderType === 3" class="cs-message-system">
                {{ renderMessageContent(message) }}
              </div>
              <div v-else class="cs-message-bubble">
                <div class="cs-message-content">{{ renderMessageContent(message) }}</div>
                <div class="cs-message-meta">
                  <span>{{ formatDateTime(message.createTime) }}</span>
                  <span>
                    {{
                      currentMode === 'manual'
                        ? message.senderType === 2
                          ? '客服'
                          : '用户'
                        : message.senderType === 2
                          ? 'AI'
                          : '用户'
                    }}
                  </span>
                </div>
              </div>
            </div>
            <div v-if="!messages.length && activeSession" class="cs-empty">暂无对话记录</div>
            <div v-if="!activeSession" class="cs-empty">选择左侧会话开始沟通</div>
          </div>
        </el-scrollbar>

        <div v-if="currentMode === 'manual'" class="cs-chat-input">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            :disabled="!canReply"
            placeholder="输入回复内容..."
            @focus="handleFocus"
            @blur="handleBlur"
            @keydown="handleKeydown"
          />
          <div class="cs-chat-input-actions">
            <span class="cs-input-hint">{{ canReply ? 'Enter 发送，Shift+Enter 换行' : lockHint }}</span>
            <button class="cs-action-btn cs-action-btn-send" type="button" :disabled="!canReply || !draft.trim()" @click="sendMessage">
              <span class="transition"></span>
              <span class="gradient"></span>
              <span class="label">发送</span>
            </button>
          </div>
        </div>
        <div v-else class="cs-chat-input cs-chat-input-readonly">
          <span class="cs-input-hint">AI 会话为只读模式，可在小程序端继续对话</span>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.cs-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: calc(100vh - 120px);
}

.cs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-radius: var(--panel-radius);
  background: linear-gradient(135deg, rgba(110, 29, 32, 0.12), rgba(255, 255, 255, 0.75));
  box-shadow: var(--panel-shadow);
  border: 1px solid var(--panel-border);
  position: relative;
  overflow: hidden;
}

.cs-header-bottom {
  margin-top: 8px;
}

.cs-header::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--app-noise);
  opacity: 0.2;
  pointer-events: none;
}

.cs-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 1;
}

.cs-title-text {
  font-family: var(--app-font-brand);
  font-size: 24px;
  color: var(--app-primary);
}

.cs-subtitle {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.cs-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
}

.cs-refresh {
  background: rgba(110, 29, 32, 0.1);
  border-color: rgba(110, 29, 32, 0.2);
  color: var(--app-primary);
}

.cs-ws-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--app-border);
  font-size: 12px;
}

.cs-ws-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d98b2b;
}

.cs-ws-status[data-status='open'] .cs-ws-dot {
  background: #1aa86b;
}

.cs-body {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 18px;
  min-height: calc(100vh - 160px);
}

.cs-sessions,
.cs-chat {
  background: rgba(255, 255, 255, 0.7);
  border-radius: var(--panel-radius);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  position: relative;
  overflow: hidden;
}

.cs-panel-title {
  padding: 16px 18px 0;
  font-family: var(--app-font-sidebar);
  color: var(--app-primary);
  font-size: 18px;
}

.cs-mode-tabs {
  display: flex;
  gap: 8px;
  padding: 14px 14px 0;
}

.cs-mode-btn {
  flex: 1;
  border: 1px solid rgba(110, 29, 32, 0.25);
  border-radius: 10px;
  background: #fff;
  color: var(--app-primary);
  font-size: 13px;
  line-height: 32px;
  cursor: pointer;
}

.cs-mode-btn.active {
  background: rgba(110, 29, 32, 0.14);
  border-color: rgba(110, 29, 32, 0.5);
}

.cs-session-list {
  height: calc(100vh - 250px);
  padding: 12px 14px 18px;
}

.cs-session-item {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 12px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.cs-session-item + .cs-session-item {
  margin-top: 10px;
}

.cs-session-item:hover {
  border-color: rgba(110, 29, 32, 0.3);
  transform: translateY(-1px);
}

.cs-session-item.active {
  border-color: rgba(110, 29, 32, 0.5);
  background: rgba(110, 29, 32, 0.08);
}

.cs-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(110, 29, 32, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-primary);
  font-family: var(--app-font-brand);
}

.cs-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cs-avatar-medal {
  width: 62%;
  height: 62%;
  object-fit: contain;
}

.cs-avatar.large {
  width: 60px;
  height: 60px;
  border-radius: 18px;
}

.cs-avatar-placeholder {
  font-size: 18px;
}

.cs-session-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cs-session-top {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.cs-session-name {
  color: var(--app-text);
  font-weight: 600;
}

.cs-session-preview {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.cs-session-lock {
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 12px;
  color: #d98b2b;
}

.cs-chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.cs-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--panel-border);
  background: rgba(255, 255, 255, 0.7);
}

.cs-chat-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cs-ai-note {
  font-size: 12px;
  color: var(--app-text-secondary);
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--panel-border);
  border-radius: 999px;
  padding: 6px 12px;
}

.cs-chat-user {
  display: flex;
  gap: 14px;
  align-items: center;
}

.cs-chat-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cs-chat-name {
  font-size: 20px;
  font-family: var(--app-font-brand);
  color: var(--app-primary);
}

.cs-chat-meta {
  display: flex;
  gap: 16px;
  color: var(--app-text-secondary);
  font-size: 12px;
  flex-wrap: wrap;
}

.cs-chat-list {
  flex: 1;
  padding: 14px 20px;
  background: linear-gradient(180deg, rgba(250, 250, 250, 0.9), rgba(240, 235, 222, 0.6));
}

.cs-chat-list-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.cs-load-more {
  align-self: center;
  padding: 6px 12px;
  border-radius: 12px;
  border: 1px solid rgba(110, 29, 32, 0.2);
  background: rgba(255, 255, 255, 0.8);
  color: var(--app-primary);
  cursor: pointer;
}

.cs-message {
  display: flex;
}

.cs-message.user {
  justify-content: flex-start;
}

.cs-message.admin {
  justify-content: flex-end;
}

.cs-message.system {
  justify-content: center;
}

.cs-message-system {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(14, 16, 27, 0.08);
  font-size: 12px;
  color: var(--app-text-secondary);
}

.cs-message-bubble {
  max-width: 72%;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(14, 16, 27, 0.08);
  box-shadow: 0 6px 18px rgba(14, 16, 27, 0.08);
}

.cs-message.admin .cs-message-bubble {
  background: rgba(110, 29, 32, 0.12);
  border-color: rgba(110, 29, 32, 0.2);
}

.cs-message-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--app-text);
}

.cs-message-meta {
  margin-top: 6px;
  font-size: 11px;
  color: var(--app-text-secondary);
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.cs-chat-input {
  padding: 16px 20px;
  border-top: 1px solid var(--panel-border);
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cs-chat-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.cs-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
}

.cs-icon-btn img {
  width: 22px;
  height: 22px;
}

.cs-refresh-icon {
  font-size: 20px;
  line-height: 1;
  color: var(--app-primary);
}

.cs-action-btn {
  font-size: 15px;
  padding: 0.65em 2.2em;
  font-weight: 500;
  background: #6e1d20;
  color: #fff;
  border: none;
  position: relative;
  overflow: hidden;
  border-radius: 0.6em;
  cursor: pointer;
}

.cs-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cs-action-btn-close {
  height: 30px;
  padding: 0 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.cs-action-btn-send {
  background: #527dd0;
}

.cs-action-btn-send .transition {
  background-color: rgba(142, 211, 224, 0.6);
}

.cs-action-btn .gradient {
  position: absolute;
  width: 100%;
  height: 100%;
  left: 0;
  top: 0;
  border-radius: 0.6em;
  margin-top: -0.25em;
  background-image: linear-gradient(
    rgba(0, 0, 0, 0),
    rgba(0, 0, 0, 0),
    rgba(0, 0, 0, 0.3)
  );
}

.cs-action-btn .label {
  position: relative;
  top: -1px;
}

.cs-action-btn .transition {
  transition-timing-function: cubic-bezier(0, 0, 0.2, 1);
  transition-duration: 500ms;
  background-color: rgba(234, 180, 155, 0.45);
  border-radius: 9999px;
  width: 0;
  height: 0;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

.cs-action-btn:hover .transition {
  width: 14em;
  height: 14em;
}

.cs-action-btn:active {
  transform: scale(0.97);
}

.cs-ws-status {
  height: 30px;
  padding: 0 12px;
}

.cs-input-hint {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.cs-chat-input-readonly {
  align-items: center;
  justify-content: center;
}

.cs-empty {
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 13px;
  padding: 20px 0;
}

@media (max-width: 1100px) {
  .cs-body {
    grid-template-columns: 1fr;
  }

  .cs-session-list {
    height: 320px;
  }
}
</style>

