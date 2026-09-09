const baseUrl = 'http://localhost:8080'

Page({
  data: {
    sessionId: null,
    sessionDateKey: '',
    messages: [],
    inputValue: '',
    hasMore: false,
    loading: false,
    sending: false,
    scrollTop: 0,
  },

  onLoad() {
    this.ensureTodaySession(true)
  },

  onShow() {
    this.ensureTodaySession(false)
  },

  ensureTodaySession(force) {
    const today = this.getTodayKey()
    if (!force && this.data.sessionId && this.data.sessionDateKey === today) {
      return
    }
    this.setData({
      sessionId: null,
      sessionDateKey: today,
      messages: [],
      hasMore: false,
    })
    this.openSession()
  },

  getTodayKey() {
    const now = new Date()
    const y = now.getFullYear()
    const m = `${now.getMonth() + 1}`.padStart(2, '0')
    const d = `${now.getDate()}`.padStart(2, '0')
    return `${y}-${m}-${d}`
  },

  openSession() {
    if (this._openingSession) return
    this._openingSession = true
    const token = wx.getStorageSync('token')
    wx.request({
      url: `${baseUrl}/user/aiAssistant/session/open`,
      method: 'POST',
      header: { authentication: token },
      success: (res) => {
        if (res.data?.code !== 1) {
          wx.showToast({ title: res.data?.msg || '打开会话失败', icon: 'none' })
          return
        }
        const sessionId = res.data?.data?.id
        this.setData({
          sessionId,
          sessionDateKey: this.getTodayKey(),
        })
        this.fetchMessages()
      },
      fail: () => wx.showToast({ title: '网络异常', icon: 'none' }),
      complete: () => {
        this._openingSession = false
      },
    })
  },

  fetchMessages(beforeId) {
    const { sessionId, loading } = this.data
    if (!sessionId || loading) return
    this.setData({ loading: true })
    const token = wx.getStorageSync('token')
    const params = { sessionId, limit: 50 }
    if (beforeId) params.beforeId = beforeId

    wx.request({
      url: `${baseUrl}/user/aiAssistant/message/list`,
      method: 'GET',
      data: params,
      header: { authentication: token },
      success: (res) => {
        if (res.data?.code !== 1) {
          wx.showToast({ title: res.data?.msg || '拉取消息失败', icon: 'none' })
          return
        }
        const list = (res.data?.data || []).map((item) => ({
          ...item,
          createTimeText: this.formatTime(item.createTime),
          dishes: [],
        }))
        const next = beforeId ? [...list, ...this.data.messages] : list
        this.setData({
          messages: next,
          hasMore: list.length >= 50,
        })
      },
      complete: () => {
        this.setData({ loading: false })
        this.scrollToBottom()
      },
    })
  },

  loadMore() {
    if (!this.data.hasMore || !this.data.messages.length) return
    const firstId = this.data.messages[0].id
    this.fetchMessages(firstId)
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  sendInputMessage() {
    const content = (this.data.inputValue || '').trim()
    if (!content || !this.data.sessionId || this.data.sending) return
    const userMessage = this.buildLocalMessage(1, content)
    this.setData({
      messages: [...this.data.messages, userMessage],
      inputValue: '',
      sending: true,
    })
    this.scrollToBottom()
    this.sendMessageRequest(content, 1)
  },

  sendMessageRequest(content, senderType) {
    const token = wx.getStorageSync('token')
    wx.request({
      url: `${baseUrl}/user/aiAssistant/message/send`,
      method: 'POST',
      header: {
        authentication: token,
        'content-type': 'application/json',
      },
      data: {
        sessionId: this.data.sessionId,
        content,
        senderType,
      },
      success: (res) => {
        if (res.data?.code !== 1) {
          wx.showToast({ title: res.data?.msg || '发送失败', icon: 'none' })
          return
        }
        const payload = res.data?.data || {}
        const dishes = (payload.dishes || []).map((dish) => ({
          ...dish,
          added: false,
          selectedFlavor: (dish.flavors && dish.flavors.length) ? dish.flavors[0] : '',
          quantity: 1,
        }))
        const aiMessage = this.buildLocalMessage(2, payload.reply || '好的', dishes)
        this.setData({
          messages: [...this.data.messages, aiMessage],
        })
        this.scrollToBottom()
      },
      fail: () => wx.showToast({ title: '网络异常', icon: 'none' }),
      complete: () => this.setData({ sending: false }),
    })
  },

  onDishAdd(e) {
    const dish = e.detail?.dish
    const flavor = e.detail?.flavor || ''
    const quantity = e.detail?.quantity || 1
    const messageId = e.currentTarget.dataset.messageId
    if (!dish || !messageId) return
    this.addDishToCart(dish, flavor, quantity, messageId)
  },

  addDishToCart(dish, flavor, quantity, messageId) {
    const token = wx.getStorageSync('token')
    const runAdd = (index) => {
      if (index >= quantity) {
        this.markDishAdded(messageId, dish.dishId)
        wx.showToast({ title: '已加入购物车', icon: 'success' })
        return
      }
      wx.request({
        url: `${baseUrl}/user/shoppingCart/add`,
        method: 'POST',
        header: {
          authentication: token,
          'content-type': 'application/json',
        },
        data: {
          dishId: dish.dishId,
          dishFlavor: flavor || '',
        },
        success: (res) => {
          if (res.data?.code !== 1) {
            wx.showToast({ title: res.data?.msg || '加购失败', icon: 'none' })
            return
          }
          runAdd(index + 1)
        },
        fail: () => wx.showToast({ title: '网络异常', icon: 'none' }),
      })
    }
    runAdd(0)
  },

  markDishAdded(messageId, dishId) {
    const messages = this.data.messages.map((msg) => {
      if (msg.id !== messageId) return msg
      const dishes = (msg.dishes || []).map((dish) =>
        dish.dishId === dishId ? { ...dish, added: true } : dish,
      )
      return { ...msg, dishes }
    })
    this.setData({ messages })
  },

  onDishCancel(e) {
    const dish = e.detail?.dish
    if (!dish || !dish.name || this.data.sending) return
    this.setData({ sending: true })
    const content = `用户拒绝了菜品[${dish.name}]，请换一个推荐菜品。`
    this.sendMessageRequest(content, 3)
  },

  buildLocalMessage(senderType, content, dishes = []) {
    return {
      id: `local_${Date.now()}_${Math.random().toString(16).slice(2)}`,
      senderType,
      content,
      createTime: new Date().toISOString(),
      createTimeText: this.formatTime(new Date()),
      dishes,
    }
  },

  formatTime(input) {
    const d = new Date(input)
    if (Number.isNaN(d.getTime())) return ''
    const pad = (n) => (n < 10 ? `0${n}` : `${n}`)
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  },

  scrollToBottom() {
    this.setData({ scrollTop: this.data.scrollTop + 9999 })
  },
})
