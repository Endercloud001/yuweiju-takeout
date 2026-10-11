import { dependencies, accepted, failure } from './legacy.js';
import historicalImage from './historical-image.js';
const { store, api, uni, tools, components } = dependencies();
const emptyOrder = () => ({ orderDetailList: [] });
export default {
  mixins: [historicalImage],
  components: { uniNavBar: components.uniNavBar, uniPopup: components.uniPopup },
  data() {
    return { showDisplay: false, rocallTime: '', textTip: '', showConfirm: false,
      orderDetailsData: emptyOrder(), timeout: false, isOvertime: false, orderId: null,
      activeRadio: 0, time: null, isPayment: false, times: null,
      requestVersion: 0, disposed: false, shown: false, loading: false, loaded: false, hidden: false };
  },
  computed: {
    orderListDataes() { return this.orderDetailsData.orderDetailList; },
    phone() { return store.state.shopPhone; },
    orderDataes() { return this.showDisplay ? this.orderListDataes : this.orderListDataes.slice(0, 2); }
  },
  onLoad(options) { this.disposed = false; return this.getBaseData(options && options.orderId); },
  onShow() { this.hidden = false; if (!this.shown) { this.shown = true; return; } if (this.orderId !== null) return this.getBaseData(this.orderId); },
  onHide() { this.hidden = true; this.clearTimer(); },
  onUnload() { this.dispose(); },
  beforeDestroy() { this.dispose(); },
  methods: {
    clearTimer() { if (this.times !== null) clearTimeout(this.times); this.times = null; },
    dispose() { this.disposed = true; this.requestVersion++; this.clearTimer(); if (this.loading) uni.hideLoading(); this.loading = false; },
    clearDetails() {
      this.resetHistoricalImages();
      this.clearTimer(); this.orderDetailsData = emptyOrder(); this.loaded = false;
      this.showDisplay = false; this.timeout = false; this.rocallTime = '';
      store.commit('initdishListMut', []);
    },
    async getBaseData(id) {
      if (this.disposed) return;
      const version = ++this.requestVersion;
      this.clearDetails();
      this.orderId = id === undefined || id === null ? null : id;
      if (!/^\d+$/.test(String(id)) || Number(id) <= 0) {
        this.loading = false; uni.hideLoading(); failure(uni, new Error('订单不存在')); return;
      }
      this.loading = true; uni.showLoading({ title: '加载中', mask: true });
      try {
        const data = accepted(await api.getOrderDetail(id));
        if (this.disposed || version !== this.requestVersion) return;
        if (!data || data.id === undefined || String(data.id) !== String(id) || !Array.isArray(data.orderDetailList)) throw new Error('订单不存在');
        this.orderDetailsData = data; this.loaded = true;
        store.commit('initdishListMut', data.orderDetailList);
        if (data.status === 1) this.runTimeBack(data.orderTime);
      } catch (error) {
        if (!this.disposed && version === this.requestVersion) { this.clearDetails(); failure(uni, error); }
      } finally { if (!this.disposed && version === this.requestVersion) { this.loading = false; uni.hideLoading(); } }
    },
    async handleReminder(type, id) {
      try {
        accepted(await api.reminderOrder(id)); if (this.disposed) return;
        this.showConfirm = true; this.textTip = '您的催单信息已发出！'; this.$refs.commonPopup.open(type); this.orderId = id;
      } catch (error) { if (!this.disposed) failure(uni, error); }
    },
    async cancel(type, obj) {
      try {
        accepted(await api.cancelOrder(obj.id)); if (this.disposed) return;
        this.clearTimer(); this.isPayment = true; this.showConfirm = true;
        this.textTip = '您的订单已取消！'; this.$refs.commonPopup.open(type); this.orderId = obj.id;
      } catch (error) { if (!this.disposed) failure(uni, error); }
    },
    handleCancel(type, obj) {
      if (obj.status === 1 || obj.status === 2) return this.cancel(type, obj);
      this.showConfirm = false; this.$refs.commonPopup.open(type); this.textTip = '请联系商家进行取消！';
    },
    async oneMoreOrder(id) {
      try {
        accepted(await api.delShoppingCart()); if (this.disposed) return;
        accepted(await api.repetitionOrder(id)); if (this.disposed) return;
        uni.redirectTo({ url: '/pages/index/index' });
      } catch (error) { if (!this.disposed) failure(uni, error); }
    },
    statusWord(status) {
      if ((this.timeout && status === 1) || status === 6) return '订单已取消';
      return { 2: '等待商户接单', 3: '商家已接单', 4: '订单派送中', 5: '订单已完成' }[status];
    },
    runTimeBack(time) {
      this.clearTimer();
      if (this.disposed || this.hidden || this.orderDetailsData.status !== 1 || typeof time !== 'string') return;
      const remaining = 15 * 60 * 1000 - (Date.now() - Date.parse(time.replace(/-/g, '/')));
      if (!Number.isFinite(remaining)) return;
      if (remaining <= 0) { this.timeout = true; this.cancel('center', this.orderDetailsData); return; }
      this.timeout = false;
      this.rocallTime = String(Math.floor(remaining / 60000)).padStart(2, '0') + ':' + String(Math.floor(remaining / 1000) % 60).padStart(2, '0');
      this.times = setTimeout(() => this.runTimeBack(time), 1000);
    },
    goBack() { uni.redirectTo({ url: '/pages/historyOrder/historyOrder' }); },
    openPopuos(type) { this.$refs.commonPopup.open(type); },
    handleRefund(type) { this.showConfirm = false; this.openPopuos(type); this.textTip = '请联系商家进行退款！'; },
    handlePhone(type) { this.$refs.phone.open(type); },
    closePopup(type) { this.$refs.phone.close(type); },
    closePopupInfo(type) { this.$refs.commonPopup.close(type); return this.getBaseData(this.orderId); },
    handlePay(id) {
      store.commit('setOrderData', { orderNumber: this.orderDetailsData.number, orderAmount: this.orderDetailsData.amount, orderTime: this.orderDetailsData.orderTime });
      uni.redirectTo({ url: '/pages/pay/index?orderId=' + id });
    },
    change() {},
    call() { tools.call(store.state.shopPhone); }
  }
};
