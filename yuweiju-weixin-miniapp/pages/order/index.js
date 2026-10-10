// Generated from checkout-source/src/Checkout.vue. Run npm run build in checkout-source.
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);
var __async = (__this, __arguments, generator) => {
  return new Promise((resolve, reject) => {
    var fulfilled = (value) => {
      try {
        step(generator.next(value));
      } catch (e) {
        reject(e);
      }
    };
    var rejected = (value) => {
      try {
        step(generator.throw(value));
      } catch (e) {
        reject(e);
      }
    };
    var step = (x) => x.done ? resolve(x.value) : Promise.resolve(x.value).then(fulfilled, rejected);
    step((generator = generator.apply(__this, __arguments)).next());
  });
};

// src/Checkout.vue
var Checkout_exports = {};
__export(Checkout_exports, {
  default: () => Checkout_default
});
module.exports = __toCommonJS(Checkout_exports);

// src/checkout.js
function createCheckout({ store, api, uni, tools, components }) {
  const commit = (name, value) => store.commit(name, value);
  const toast = (error) => uni.showToast({ title: error && (error.msg || error.data && error.data.msg) || "\u64CD\u4F5C\u5931\u8D25", icon: "none" });
  return {
    components,
    data() {
      return {
        platform: "ios",
        orderDishPrice: 0,
        orderDishNumber: 0,
        showDisplay: false,
        psersonUrl: "../../static/btn_waiter_sel.png",
        nickName: "",
        gender: "0",
        phoneNumber: "",
        address: "",
        addressBookId: "",
        addressLabel: "",
        tagLabel: "",
        remark: "",
        arrivalTime: "",
        scrollH: 0,
        addressList: [],
        isHandlePy: false,
        tablewareData: "\u65E0\u9700\u9910\u5177",
        tableware: "",
        activeRadio: "\u65E0\u9700\u9910\u5177",
        baseData: ["\u65E0\u9700\u9910\u5177", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"],
        radioGroup: ["\u4F9D\u636E\u9910\u91CF\u63D0\u4F9B", "\u65E0\u9700\u9910\u5177"],
        status: 0,
        num: 0,
        tabIndex: 0,
        scrollinto: "tab0",
        popleft: ["\u4ECA\u5929", "\u660E\u5929"],
        weeks: [],
        popright: [
          "\u7ACB\u5373\u6D3E\u9001",
          "09:00",
          "09:30",
          "10:00",
          "10:30",
          "11:00",
          "11:30",
          "12:00",
          "12:30",
          "13:00",
          "13:30",
          "14:00",
          "14:30",
          "15:00",
          "15:30",
          "16:00",
          "16:30",
          "17:00",
          "17:30",
          "18:00",
          "18:30",
          "19:00",
          "19:30",
          "20:00",
          "20:30",
          "21:00",
          "21:30",
          "22:00",
          "22:30",
          "23:00"
        ],
        newDateData: [],
        selectValue: 0,
        isTomorrow: false,
        toDate: null,
        tomorrowStart: null,
        newDate: null
      };
    },
    computed: {
      orderListDataes() {
        return store.state.orderListData;
      },
      orderDataes() {
        return this.showDisplay ? this.orderListDataes : this.orderListDataes.slice(0, 3);
      }
    },
    created() {
      const now = /* @__PURE__ */ new Date();
      this.toDate = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      this.tomorrowStart = this.toDate + 24 * 3600 * 1e3;
      this.newDate = now.getHours() * 3600 + now.getMinutes() * 60;
      this.getDateDate();
      this.weeks = [this.toDate, this.tomorrowStart].map(tools.getWeekDate);
      this.getAddressList();
    },
    onLoad() {
      this.platform = uni.getSystemInfoSync().platform;
      const user = store.state.baseUserInfo || {};
      this.psersonUrl = user.avatarUrl;
      this.nickName = user.nickName;
      this.gender = user.gender;
      this.remark = store.state.remarkData;
      this.computOrderInfo();
      const selected = store.state.addressData;
      if (selected && selected.detail) return this.applyAddress(selected);
      return this.getAddressBookDefault();
    },
    onReady() {
      uni.getSystemInfo({ success: (res) => {
        this.scrollH = res.windowHeight - uni.upx2px(100);
      } });
    },
    methods: {
      computOrderInfo() {
        this.orderDishNumber = 0;
        this.orderDishPrice = 0;
        this.orderListDataes.forEach((item) => {
          this.orderDishNumber += item.number;
          this.orderDishPrice += item.number * item.amount;
        });
        this.orderDishPrice += 6 + this.orderDishNumber;
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
      getAddressList() {
        return __async(this, null, function* () {
          try {
            const res = yield api.queryAddressBookList();
            if (res.code === 1) this.addressList = res.data || [];
          } catch (error) {
            toast(error);
          }
        });
      },
      getAddressBookDefault() {
        return __async(this, null, function* () {
          try {
            const res = yield api.getAddressBookDefault();
            if (res.code === 1 && res.data) return this.applyAddress(res.data);
          } catch (error) {
            toast(error);
          }
        });
      },
      getHarfAnOur(addressBookId) {
        return __async(this, null, function* () {
          this.arrivalTime = "\u8BA1\u7B97\u4E2D...";
          commit("setArrivalTime", this.arrivalTime);
          if (!addressBookId) return;
          try {
            const res = yield api.getEstimatedDeliveryTime(addressBookId);
            this.arrivalTime = res.code === 1 && res.data ? res.data : res.msg || "\u8D85\u51FA\u914D\u9001\u8303\u56F4";
          } catch (error) {
            this.arrivalTime = error && error.msg || "\u8D85\u51FA\u914D\u9001\u8303\u56F4";
          }
          commit("setArrivalTime", this.arrivalTime);
        });
      },
      goBack() {
        uni.redirectTo({ url: "/pages/index/index" });
      },
      goAddress() {
        commit("setAddressBackUrl", "/pages/order/index");
        uni.redirectTo({ url: this.addressList.length ? "/pages/address/address" : "/pages/addOrEditAddress/addOrEditAddress" });
      },
      goRemark() {
        commit("setAddressBackUrl", "/pages/order/index");
        uni.redirectTo({ url: "/pages/remark/index" });
      },
      payOrderHandle() {
        return __async(this, null, function* () {
          if (this.isHandlePy) return;
          if (!this.address) {
            toast({ msg: "\u8BF7\u9009\u62E9\u6536\u8D27\u5730\u5740" });
            return false;
          }
          this.isHandlePy = true;
          const params = {
            payMethod: 1,
            addressBookId: this.addressBookId,
            remark: this.remark,
            estimatedDeliveryTime: null,
            deliveryStatus: this.arrivalTime === "\u7ACB\u5373\u6D3E\u9001" ? 1 : 0,
            tablewareStatus: this.status,
            tablewareNumber: this.num,
            packAmount: this.orderDishNumber,
            amount: this.orderDishPrice
          };
          try {
            const res = yield api.submitOrderSubmit(params);
            if (res.code !== 1) {
              toast(res);
              return;
            }
            commit("setOrderData", res.data);
            commit("setRemark", "");
            if (res.data && res.data.estimatedDeliveryTime) {
              this.arrivalTime = res.data.estimatedDeliveryTime.substring(11, 16);
              commit("setArrivalTime", this.arrivalTime);
            }
            uni.redirectTo({ url: "/pages/pay/index?orderId=" + res.data.id });
          } catch (error) {
            toast(error);
          } finally {
            this.isHandlePy = false;
          }
        });
      },
      openPopuos(type) {
        this.$refs.popup.open(type);
      },
      closePopup() {
        this.$refs.popup.close();
      },
      openTimePopuo(type) {
        this.$refs.timePopup.open(type);
      },
      onsuer() {
        this.$refs.timePopup.close();
      },
      change() {
      },
      touchstart() {
      },
      changeCont(value) {
        this.tableware = value;
      },
      handleRadio(event) {
        this.activeRadio = event.detail.value;
      },
      handlePiker() {
        this.closePopup();
        if (this.tableware !== "") {
          this.num = Number(this.tableware);
          this.status = 0;
          if (this.tableware === "\u65E0\u9700\u9910\u5177") this.num = 0;
          if (this.tableware === "\u4F9D\u636E\u9910\u91CF\u63D0\u4F9B") {
            this.num = this.orderDishNumber;
            this.status = 1;
          }
          this.tablewareData = this.tableware + "\u4EFD";
        } else {
          this.tablewareData = this.baseData[this.$refs.piker.defaultValue[0]];
          this.status = this.activeRadio === "\u4F9D\u636E\u9910\u91CF\u63D0\u4F9B" ? 1 : 0;
          this.num = this.status ? this.orderDishNumber : 0;
        }
      },
      getDateDate() {
        this.newDateData = this.popright.filter((item) => {
          const parts = item.split(":");
          return this.newDate < Number(parts[0]) * 3600 + Number(parts[1]) * 60;
        });
        this.newDateData.splice(0, 2);
        this.newDateData.unshift("\u7ACB\u5373\u6D3E\u9001");
      },
      dateChange(index) {
        this.isTomorrow = index === 1;
        if (this.isTomorrow) this.newDateData = this.popright.slice(1);
        else this.getDateDate();
        this.tabIndex = index;
      },
      timeClick(value, index) {
        this.selectValue = index;
        return this.setTime(value);
      },
      setTime(value) {
        if (value === "\u7ACB\u5373\u6D3E\u9001") this.getHarfAnOur(this.addressBookId);
        else this.arrivalTime = value;
        commit("setArrivalTime", this.arrivalTime);
        this.onsuer();
      }
    }
  };
}

// src/legacy.js
function legacyDependencies() {
  const load = wx.__webpack_require_UNI_MP_PLUGIN__;
  if (typeof load !== "function") throw new Error("Checkout requires the existing uni-app main runtime");
  return {
    store: load(12).default,
    api: load(24),
    uni: load(1).default,
    tools: load(29),
    components: {
      uniNavBar: () => load.e("components/uni-nav-bar/uni-nav-bar").then(() => load(145)),
      uniList: () => load.e("uni_modules/uni-list/components/uni-list/uni-list").then(() => load(152)),
      uniListItem: () => load.e("node-modules/@dcloudio/uni-ui/lib/uni-list-item/uni-list-item").then(() => load(173)),
      uniPopup: () => load.e("uni_modules/uni-popup/components/uni-popup/uni-popup").then(() => load(166)),
      pikers: () => load.e("components/uni-piker/index").then(() => load(180))
    }
  };
}

// src/Checkout.vue
var Checkout_default = createCheckout(legacyDependencies());

var render = function() {var _vm=this;var _h=_vm.$createElement;var _c=_vm._self._c||_h;
  var l0 = _vm.__map(_vm.orderDataes, function (obj, index) {
    var g0 = obj.amount.toFixed(2);
    return {
      $orig: _vm.__get_orig(obj),
      g0: g0
    };
  });
  var g1 = _vm.orderDishPrice.toFixed(2);
  var g2 = _vm.orderDishPrice.toFixed(2);
  if (!_vm._isMounted) {
    _vm.e0 = function ($event) {
      _vm.showDisplay = !_vm.showDisplay;
    };
  }
  _vm.$mp.data = Object.assign({}, {
    $root: {
      l0: l0,
      g1: g1,
      g2: g2
    }
  });
}; var staticRenderFns = [];
render._withStripped = true;
var load = wx.__webpack_require_UNI_MP_PLUGIN__;
var page = load(11).default(module.exports.default, render, staticRenderFns, false, null, '0ca91b30', null, false).exports;
load(1).createPage(page);
