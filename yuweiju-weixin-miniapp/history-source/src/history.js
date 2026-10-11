import { dependencies, accepted, failure } from './legacy.js';
import historicalImage from './historical-image.js';
const { store, api, uni, tools, components } = dependencies();
export default {
  mixins: [historicalImage],
  components,
  data() {
    return { recentOrdersList: [], pageInfo: { page: 1, pageSize: 10, total: 0 },
      status: '', loadingType: 0, showTitle: false, scrollinto: 'tab0', scrollH: 0,
      tabIndex: 0, tabBars: ['全部订单', '待付款', '已取消'], textTip: '', showConfirm: false,
      isEmpty: false, loading: false, loadingText: '', requestVersion: 0, disposed: false, shown: false };
  },
  onLoad() { this.disposed = false; return this.reload(); },
  onShow() {
    // First onShow accompanies onLoad. Subsequent shows refresh fulfilment status.
    if (!this.shown) { this.shown = true; return; }
    return this.reload();
  },
  onUnload() { this.dispose(); },
  beforeDestroy() { this.dispose(); },
  onReady() { uni.getSystemInfo({ success: res => { if (!this.disposed) this.scrollH = res.windowHeight - uni.upx2px(100); } }); },
  onPullDownRefresh() { return this.reload().finally(() => uni.stopPullDownRefresh()); },
  onReachBottom() { return this.lower(); },
  methods: {
    numes(list) { return (list || []).reduce((sum, item) => ({ count: sum.count + Number(item.number), total: sum.total + Number(item.number) * Number(item.amount) }), { count: 0, total: 0 }); },
    statusWord(status, time) { return tools.statusWord(status, time); },
    getOvertime(time) { return tools.getOvertime(time); },
    dispose() { this.disposed = true; this.requestVersion++; this.showTitle = false; if (this.loading) uni.hideLoading(); this.loading = false; },
    reload() {
      this.resetHistoricalImages();
      this.requestVersion++;
      this.pageInfo = { page: 1, pageSize: 10, total: 0 };
      this.recentOrdersList = []; this.isEmpty = false; this.loading = false; this.loadingType = 0;
      return this.getList(1);
    },
    async getList(page = this.pageInfo.page) {
      if (this.disposed || this.loading) return;
      const version = ++this.requestVersion;
      this.loading = true; this.loadingType = 1; this.loadingText = '数据加载中...';
      uni.showLoading({ title: '加载中', mask: true });
      try {
        const data = accepted(await api.getOrderPage({ pageSize: 10, page, status: this.status }));
        if (this.disposed || version !== this.requestVersion) return;
        if (!data || !Array.isArray(data.records)) throw new Error('加载失败，请重试');
        this.recentOrdersList = page === 1 ? data.records : this.recentOrdersList.concat(data.records);
        this.pageInfo.page = page; this.pageInfo.total = Number(data.total);
        this.isEmpty = true;
        this.loadingType = this.recentOrdersList.length >= this.pageInfo.total || data.records.length === 0 ? 2 : 0;
        this.loadingText = this.loadingType === 2 ? '没有更多了' : '';
      } catch (error) {
        if (!this.disposed && version === this.requestVersion) { this.loadingType = 0; this.loadingText = ''; failure(uni, error); }
      } finally {
        if (version === this.requestVersion && !this.disposed) { this.loading = false; uni.hideLoading(); }
      }
    },
    lower() {
      if (this.loading || this.loadingType === 2 || this.recentOrdersList.length >= this.pageInfo.total) return Promise.resolve();
      this.showTitle = true;
      return this.getList(this.pageInfo.page + 1);
    },
    dataAdd() { return this.lower(); },
    changeTab(index) {
      if (this.tabIndex === index) return Promise.resolve();
      this.tabIndex = index; this.status = index === 1 ? 1 : index === 2 ? 6 : '';
      this.scrollinto = 'tab' + index; return this.reload();
    },
    onChangeSwiperTab(event) { return this.changeTab(event.detail.current); },
    async oneMoreOrder(id) {
      try {
        accepted(await api.delShoppingCart());
        if (this.disposed) return;
        accepted(await api.repetitionOrder(id));
        if (this.disposed) return;
        const pages = getCurrentPages(); const routeIndex = pages.findIndex(page => page.route === 'pages/index/index');
        uni.navigateBack({ delta: routeIndex > -1 ? pages.length - routeIndex : 1 });
      } catch (error) { if (!this.disposed) failure(uni, error); }
    },
    goDetail(id) { store.commit('setAddressBackUrl', '/pages/historyOrder/historyOrder'); uni.redirectTo({ url: '/pages/details/index?orderId=' + id }); },
    async handleReminder(type, id) {
      try {
        accepted(await api.reminderOrder(id));
        if (this.disposed) return;
        this.showConfirm = true; this.textTip = '您的催单信息已发出！'; this.$refs.commonPopup.open(type);
        await this.reload();
      } catch (error) { if (!this.disposed) failure(uni, error); }
    },
    closePopup(type) { this.$refs.commonPopup.close(type); },
    goBack() { uni.redirectTo({ url: '/pages/my/my' }); }
  }
};
