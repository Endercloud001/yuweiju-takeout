const baseUrl = 'http://localhost:8080'

Page({
  data: {
    sessionId: null,
    messages: [],
    inputValue: '',
    hasMore: false,
    loading: false,
    wsConnected: false,
    scrollTop: 0,
  },

  onLoad() {
    this.openSession()
  },

  onUnload() {
    this.persistMessages()
    this.closeSocket()
  },

  openSession() {
    const token = wx.getStorageSync('token')
    wx.request({
      url: `${baseUrl}/user/customerService/session/open`,
      method: 'POST',
      header: { authentication: token },
      success: (res) => {
        if (res.data?.code !== 1) {
          wx.showToast({ title: res.data?.msg || '打开会话失败', icon: 'none' })
          return
        }
        const sessionId = res.data?.data?.id
        this.setData({ sessionId })
        this.restoreMessages(sessionId)
        this.connectSocket()
        this.fetchMessages()
      },
      fail: () => {
        wx.showToast({ title: '网络异常', icon: 'none' })
      },
    })
  },

  fetchMessages(beforeId) {
    const { sessionId, loading } = this.data
    if (!sessionId || loading) return
    this.setData({ loading: true })
    const token = wx.getStorageSync('token')
    const params = {
      sessionId,
      limit: 50,
    }
    if (beforeId !== undefined && beforeId !== null && beforeId !== '') {
      params.beforeId = beforeId
    }
    wx.request({
      url: `${baseUrl}/user/customerService/message/list`,
      method: 'GET',
      header: { authentication: token },
      data: params,
      success: (res) => {
        if (res.data?.code !== 1) {
          wx.showToast({ title: res.data?.msg || '获取消息失败', icon: 'none' })
          return
        }
        const list = (res.data?.data || []).map((item) => this.formatMessage(item))
        const next = beforeId ? [...list, ...this.data.messages] : list
        if (!next.length && this.data.messages.length) {
          return
        }
        this.setData({
          messages: next,
          hasMore: list.length >= 50,
        })
        this.persistMessages()
      },
      complete: () => {
        this.setData({ loading: false })
        this.scrollToBottom()
      },
    })
  },

  loadMore() {
    if (!this.data.hasMore || !this.data.messages.length) return
    const firstId = this.data.messages[0]?.id
    if (firstId) this.fetchMessages(firstId)
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  sendMessage() {
    const { inputValue, sessionId, wsConnected } = this.data
    if (!sessionId) return
    const content = (inputValue || '').trim()
    if (!content) return
    if (!wsConnected) {
      wx.showToast({ title: '连接中，请稍后', icon: 'none' })
      return
    }
    this.sendSocket('CHAT_SEND', { sessionId, content })
    this.setData({ inputValue: '' })
  },

  connectSocket() {
    const token = wx.getStorageSync('token')
    const { sessionId } = this.data
    if (!token || !sessionId) return
    const protocol = baseUrl.startsWith('https') ? 'wss' : 'ws'
    const host = baseUrl.replace(/^https?:\/\//, '')
    const url = `${protocol}://${host}/ws/customer-service/user?token=${encodeURIComponent(token)}&sessionId=${sessionId}`
    wx.connectSocket({ url })

    wx.onSocketOpen(() => {
      this.setData({ wsConnected: true })
    })

    wx.onSocketMessage((res) => {
      try {
        const payload = JSON.parse(res.data)
        if (payload.type === 'CHAT_MESSAGE' && payload.data) {
          const next = [...this.data.messages, this.formatMessage(payload.data)]
          this.setData({ messages: next })
          this.persistMessages()
          this.scrollToBottom()
        }
      } catch (e) {
        // ignore
      }
    })

    wx.onSocketClose(() => {
      this.setData({ wsConnected: false })
    })
  },

  closeSocket() {
    try {
      wx.closeSocket()
    } catch (e) {
      // ignore
    }
  },

  sendSocket(type, data) {
    wx.sendSocketMessage({
      data: JSON.stringify({ type, ...data }),
    })
  },

  formatMessage(message) {
    if (!message) return message
    const createTime = message.createTime ? this.formatTime(message.createTime) : ''
    return { ...message, createTime }
  },

  formatTime(input) {
    const d = new Date(input)
    if (Number.isNaN(d.getTime())) return input
    const pad = (n) => (n < 10 ? `0${n}` : `${n}`)
    const y = d.getFullYear()
    const m = pad(d.getMonth() + 1)
    const day = pad(d.getDate())
    const hh = pad(d.getHours())
    const mm = pad(d.getMinutes())
    const ss = pad(d.getSeconds())
    return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
  },

  scrollToBottom() {
    this.setData({ scrollTop: this.data.scrollTop + 9999 })
  },

  restoreMessages(sessionId) {
    if (!sessionId) return
    try {
      const cacheKey = `cs_messages_${sessionId}`
      const cached = wx.getStorageSync(cacheKey)
      if (cached && Array.isArray(cached)) {
        this.setData({ messages: cached })
      }
    } catch (e) {
      // ignore
    }
  },

  persistMessages() {
    const { sessionId, messages } = this.data
    if (!sessionId || !messages) return
    try {
      const cacheKey = `cs_messages_${sessionId}`
      wx.setStorageSync(cacheKey, messages)
    } catch (e) {
      // ignore
    }
  },
})
