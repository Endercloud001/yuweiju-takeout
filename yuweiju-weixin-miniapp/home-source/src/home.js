// Keep legacy module 20's login, catalog, menu, flavors, and navigation contracts.
// Only the live cart read/recovery behavior is adapted; no shared vendor mutation.
export function createHome(load) {
  const legacy = load(20).default;
  const store = load(12).default;
  const api = load(24);
  const uni = load(1).default;
  const toast = error => uni.showToast({ title: error && (error.msg || error.message) || '购物车读取失败，请重试', icon: 'none' });
  const invalid = item => item.available === false || item.amount == null || !Number.isFinite(Number(item.amount)) || Number(item.amount) < 0 || !Number.isInteger(item.number) || item.number <= 0;
  return {
    ...legacy,
    data() { return { ...legacy.data.call(this), cartReady: false, cartBusy: false }; },
    computed: {
      ...legacy.computed,
      cartInvalid() { return this.orderListDataes.some(invalid); }
    },
    methods: {
      ...legacy.methods,
      computOrderInfo() {
        this.orderDishNumber = this.orderListDataes.reduce((sum, item) => sum + (Number(item.number) || 0), 0);
        this.orderDishPrice = this.cartInvalid ? null : this.orderListDataes.reduce((sum, item) => sum + Math.round(Number(item.amount) * 100) * item.number, 0) / 100;
      },
      async getTableOrderDishListes() {
        this.cartReady = false;
        try {
          const res = await api.getShoppingCartList({});
          if (res.code !== 1 || !Array.isArray(res.data)) throw res;
          store.commit('initdishListMut', res.data);
          this.computOrderInfo();
          this.cartReady = true;
          return true;
        } catch (error) { toast(error); return false; }
      },
      async goOrder() {
        if (this.cartBusy) return;
        if (!await this.getTableOrderDishListes()) return;
        if (this.cartInvalid) { this.openOrderCartList = true; toast({ msg: '请先移除失效商品后结算' }); return; }
        if (!this.orderDishNumber) { toast({ msg: '购物车为空' }); return; }
        uni.navigateTo({ url: '/pages/order/index' });
      },
      async redDishAction(item, form) {
        if (form !== '购物车') return legacy.methods.redDishAction.call(this, item, form);
        if (this.cartBusy) return;
        this.cartBusy = true;
        this.cartReady = false;
        try {
          const params = { dishFlavor: item.dishFlavor || '' };
          if (item.dishId != null) params.dishId = item.dishId;
          else params.setmealId = item.setmealId;
          const res = await api.newShoppingCartSub(params);
          if (res.code !== 1) throw res;
          await this.getTableOrderDishListes();
          // Existing catalog refresh also reconciles visible catalog quantities.
          if (this.rightIdAndType.id) this.getDishListDataes(this.rightIdAndType);
        } catch (error) { toast(error); }
        finally { this.cartBusy = false; }
      },
      addDishAction(item, form) {
        if (form === '购物车' && invalid(item)) { toast({ msg: item.unavailableReason || '商品不可购买，请移除' }); return; }
        return legacy.methods.addDishAction.call(this, item, form);
      },
      clearCardOrder() {
        if (!this.cartReady || this.cartInvalid) { toast({ msg: '请逐项移除失效商品' }); return; }
        return legacy.methods.clearCardOrder.call(this);
      }
    }
  };
}
