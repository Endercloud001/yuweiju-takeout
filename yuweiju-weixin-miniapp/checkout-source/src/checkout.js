// Recovered from checkout module 39. Dependencies deliberately stay in the old runtime.
export function createCheckout({ store, api, uni, tools, components }) {
  const commit = (name, value) => store.commit(name, value);
  const toast = error => uni.showToast({ title: error && (error.msg || error.data && error.data.msg) || '操作失败', icon: 'none' });
  return {
    components,
    data() {
      return {
        platform: 'ios', pricingReady: false, orderDishPrice: 0, orderDishNumber: 0, showDisplay: false,
        psersonUrl: '../../static/btn_waiter_sel.png', nickName: '', gender: '0',
        phoneNumber: '', address: '', addressBookId: '', addressLabel: '', tagLabel: '',
        remark: '', arrivalTime: '', scrollH: 0, addressList: [], isHandlePy: false,
        tablewareData: '无需餐具', tableware: '', activeRadio: '无需餐具',
        baseData: ['无需餐具', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
        radioGroup: ['依据餐量提供', '无需餐具'], status: 0, num: 0,
        tabIndex: 0, scrollinto: 'tab0', popleft: ['今天', '明天'], weeks: [],
        popright: ['立即派送', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30',
          '12:00', '12:30', '13:00', '13:30', '14:00', '14:30', '15:00', '15:30',
          '16:00', '16:30', '17:00', '17:30', '18:00', '18:30', '19:00', '19:30',
          '20:00', '20:30', '21:00', '21:30', '22:00', '22:30', '23:00'],
        newDateData: [], selectValue: 0, isTomorrow: false,
        toDate: null, tomorrowStart: null, newDate: null
      };
    },
    computed: {
      orderListDataes() { return store.state.orderListData; },
      orderDataes() { if (!this.pricingReady) return []; return this.showDisplay ? this.orderListDataes : this.orderListDataes.slice(0, 3); }
    },
    created() {
      const now = new Date();
      this.toDate = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      this.tomorrowStart = this.toDate + 24 * 3600 * 1000;
      this.newDate = now.getHours() * 3600 + now.getMinutes() * 60;
      this.getDateDate();
      this.weeks = [this.toDate, this.tomorrowStart].map(tools.getWeekDate);
      this.getAddressList();
    },
    async onLoad() {
      this.platform = uni.getSystemInfoSync().platform;
      const user = store.state.baseUserInfo || {};
      this.psersonUrl = user.avatarUrl;
      this.nickName = user.nickName;
      this.gender = user.gender;
      this.remark = store.state.remarkData;
      await this.refreshCart();
      const selected = store.state.addressData;
      if (selected && selected.detail) return this.applyAddress(selected);
      return this.getAddressBookDefault();
    },
    onReady() {
      uni.getSystemInfo({ success: res => { this.scrollH = res.windowHeight - uni.upx2px(100); } });
    },
    methods: {
      computOrderInfo() {
        let count = 0;
        let cents = 0;
        this.orderListDataes.forEach(item => {
          const price = String(item.amount);
          if (!Number.isInteger(item.number) || item.number <= 0 || !/^\d+(\.\d{1,2})?$/.test(price)) {
            throw { msg: '商品数量或金额无效' };
          }
          const parts = price.split('.');
          const unit = Number(parts[0]) * 100 + Number((parts[1] || '').padEnd(2, '0'));
          count += item.number;
          cents += unit * item.number;
          if (count > 2147483647 || !Number.isSafeInteger(cents) || cents > 9999999999) throw { msg: '订单金额超出范围' };
        });
        const total = count ? cents + count * 100 + 200 : 0;
        if (total > 9999999999) throw { msg: '订单金额超出范围' };
        this.orderDishNumber = count;
        this.orderDishPrice = total / 100;
      },
      async refreshCart() {
        this.pricingReady = false;
        try {
          const res = await api.getShoppingCartList();
          if (res.code !== 1 || !Array.isArray(res.data)) throw res;
          commit('initdishListMut', res.data);
          this.computOrderInfo();
          if (!this.orderDishNumber) throw { msg: '购物车为空' };
          this.pricingReady = true;
          return true;
        } catch (error) {
          this.orderDishPrice = 0;
          this.orderDishNumber = 0;
          toast(error);
          return false;
        }
      },
      applyAddress(address) {
        this.address = address.provinceName + address.cityName + address.districtName + address.detail;
        this.phoneNumber = address.phone;
        this.nickName = address.consignee;
        this.gender = address.sex;
        this.addressBookId = address.id;
        this.addressLabel = tools.getLableVal(address.label);
        this.tagLabel = address.label;
        return this.getHarfAnOur(address.id);
      },
      async getAddressList() {
        try {
          const res = await api.queryAddressBookList();
          if (res.code === 1) this.addressList = res.data || [];
        } catch (error) { toast(error); }
      },
      async getAddressBookDefault() {
        try {
          const res = await api.getAddressBookDefault();
          if (res.code === 1 && res.data) return this.applyAddress(res.data);
        } catch (error) { toast(error); }
      },
      async getHarfAnOur(addressBookId) {
        this.arrivalTime = '计算中...';
        commit('setArrivalTime', this.arrivalTime);
        if (!addressBookId) return;
        try {
          const res = await api.getEstimatedDeliveryTime(addressBookId);
          this.arrivalTime = res.code === 1 && res.data ? res.data : res.msg || '超出配送范围';
        } catch (error) {
          this.arrivalTime = error && error.msg || '超出配送范围';
        }
        commit('setArrivalTime', this.arrivalTime);
      },
      goBack() { uni.redirectTo({ url: '/pages/index/index' }); },
      goAddress() {
        commit('setAddressBackUrl', '/pages/order/index');
        uni.redirectTo({ url: this.addressList.length ? '/pages/address/address' : '/pages/addOrEditAddress/addOrEditAddress' });
      },
      goRemark() {
        commit('setAddressBackUrl', '/pages/order/index');
        uni.redirectTo({ url: '/pages/remark/index' });
      },
      async payOrderHandle() {
        if (this.isHandlePy) return;
        if (!this.address) { toast({ msg: '请选择收货地址' }); return false; }
        this.isHandlePy = true;
        const displayedAmount = this.orderDishPrice;
        const displayedQuantity = this.orderDishNumber;
        try {
          if (!await this.refreshCart()) return;
          if (displayedAmount !== this.orderDishPrice || displayedQuantity !== this.orderDishNumber) {
            toast({ msg: '商品价格或数量已更新，请确认后再次提交' });
            return;
          }
        } finally { this.isHandlePy = false; }
        this.isHandlePy = true;
        const params = {
          payMethod: 1, addressBookId: this.addressBookId, remark: this.remark,
          estimatedDeliveryTime: null, deliveryStatus: this.arrivalTime === '立即派送' ? 1 : 0,
          tablewareStatus: this.status, tablewareNumber: this.num,
          packAmount: this.orderDishNumber, amount: this.orderDishPrice
        };
        try {
          const res = await api.submitOrderSubmit(params);
          if (res.code !== 1) { toast(res); return; }
          this.orderDishPrice = Number(res.data.orderAmount);
          commit('setOrderData', res.data);
          commit('setRemark', '');
          if (res.data && res.data.estimatedDeliveryTime) {
            this.arrivalTime = res.data.estimatedDeliveryTime.substring(11, 16);
            commit('setArrivalTime', this.arrivalTime);
          }
          uni.redirectTo({ url: '/pages/pay/index?orderId=' + res.data.id });
        } catch (error) { toast(error); }
        finally { this.isHandlePy = false; }
      },
      openPopuos(type) { this.$refs.popup.open(type); },
      closePopup() { this.$refs.popup.close(); },
      openTimePopuo(type) { this.$refs.timePopup.open(type); },
      onsuer() { this.$refs.timePopup.close(); },
      change() {},
      touchstart() {},
      changeCont(value) { this.tableware = value; },
      handleRadio(event) { this.activeRadio = event.detail.value; },
      handlePiker() {
        this.closePopup();
        if (this.tableware !== '') {
          this.num = Number(this.tableware);
          this.status = 0;
          if (this.tableware === '无需餐具') this.num = 0;
          if (this.tableware === '依据餐量提供') { this.num = this.orderDishNumber; this.status = 1; }
          // Preserve the original visible suffix, including the no-tableware label.
          this.tablewareData = this.tableware + '份';
        } else {
          this.tablewareData = this.baseData[this.$refs.piker.defaultValue[0]];
          this.status = this.activeRadio === '依据餐量提供' ? 1 : 0;
          this.num = this.status ? this.orderDishNumber : 0;
        }
      },
      getDateDate() {
        this.newDateData = this.popright.filter(item => {
          const parts = item.split(':');
          return this.newDate < Number(parts[0]) * 3600 + Number(parts[1]) * 60;
        });
        this.newDateData.splice(0, 2);
        this.newDateData.unshift('立即派送');
      },
      dateChange(index) {
        this.isTomorrow = index === 1;
        if (this.isTomorrow) this.newDateData = this.popright.slice(1);
        else this.getDateDate();
        this.tabIndex = index;
      },
      timeClick(value, index) { this.selectValue = index; return this.setTime(value); },
      setTime(value) {
        if (value === '立即派送') this.getHarfAnOur(this.addressBookId);
        else this.arrivalTime = value;
        commit('setArrivalTime', this.arrivalTime);
        this.onsuer();
      }
    }
  };
}
