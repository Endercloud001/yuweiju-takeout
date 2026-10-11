// Generated from history-source/src/Details.vue. Run npm run build in history-source.
var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getOwnPropSymbols = Object.getOwnPropertySymbols;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __propIsEnum = Object.prototype.propertyIsEnumerable;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __spreadValues = (a, b) => {
  for (var prop in b || (b = {}))
    if (__hasOwnProp.call(b, prop))
      __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b)) {
      if (__propIsEnum.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    }
  return a;
};
var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
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

// src/Details.vue
var Details_exports = {};
__export(Details_exports, {
  default: () => Details_default
});
module.exports = __toCommonJS(Details_exports);

// ../checkout-source/src/legacy.js
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

// src/legacy.js
function dependencies() {
  const deps = legacyDependencies();
  const load = wx.__webpack_require_UNI_MP_PLUGIN__;
  return __spreadProps(__spreadValues({}, deps), { components: {
    uniNavBar: deps.components.uniNavBar,
    uniPopup: deps.components.uniPopup,
    Empty: () => load.e("components/empty/empty").then(() => load(194))
  } });
}
function accepted(res) {
  if (!res || res.code !== 1) throw res || new Error("\u8BF7\u6C42\u5931\u8D25");
  return res.data;
}
function failure(uni2, error) {
  uni2.showToast({ title: error && (error.msg || error.message || error.data && error.data.msg) || "\u52A0\u8F7D\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5", icon: "none" });
}

// src/details.js
var { store, api, uni, tools, components } = dependencies();
var emptyOrder = () => ({ orderDetailList: [] });
var details_default = {
  components: { uniNavBar: components.uniNavBar, uniPopup: components.uniPopup },
  data() {
    return {
      showDisplay: false,
      rocallTime: "",
      textTip: "",
      showConfirm: false,
      orderDetailsData: emptyOrder(),
      timeout: false,
      isOvertime: false,
      orderId: null,
      activeRadio: 0,
      time: null,
      isPayment: false,
      times: null,
      requestVersion: 0,
      disposed: false,
      shown: false,
      loading: false,
      loaded: false,
      hidden: false
    };
  },
  computed: {
    orderListDataes() {
      return this.orderDetailsData.orderDetailList;
    },
    phone() {
      return store.state.shopPhone;
    },
    orderDataes() {
      return this.showDisplay ? this.orderListDataes : this.orderListDataes.slice(0, 2);
    }
  },
  onLoad(options) {
    this.disposed = false;
    return this.getBaseData(options && options.orderId);
  },
  onShow() {
    this.hidden = false;
    if (!this.shown) {
      this.shown = true;
      return;
    }
    if (this.orderId !== null) return this.getBaseData(this.orderId);
  },
  onHide() {
    this.hidden = true;
    this.clearTimer();
  },
  onUnload() {
    this.dispose();
  },
  beforeDestroy() {
    this.dispose();
  },
  methods: {
    clearTimer() {
      if (this.times !== null) clearTimeout(this.times);
      this.times = null;
    },
    dispose() {
      this.disposed = true;
      this.requestVersion++;
      this.clearTimer();
      if (this.loading) uni.hideLoading();
      this.loading = false;
    },
    clearDetails() {
      this.clearTimer();
      this.orderDetailsData = emptyOrder();
      this.loaded = false;
      this.showDisplay = false;
      this.timeout = false;
      this.rocallTime = "";
      store.commit("initdishListMut", []);
    },
    getBaseData(id) {
      return __async(this, null, function* () {
        if (this.disposed) return;
        const version = ++this.requestVersion;
        this.clearDetails();
        this.orderId = id === void 0 || id === null ? null : id;
        if (!/^\d+$/.test(String(id)) || Number(id) <= 0) {
          this.loading = false;
          uni.hideLoading();
          failure(uni, new Error("\u8BA2\u5355\u4E0D\u5B58\u5728"));
          return;
        }
        this.loading = true;
        uni.showLoading({ title: "\u52A0\u8F7D\u4E2D", mask: true });
        try {
          const data = accepted(yield api.getOrderDetail(id));
          if (this.disposed || version !== this.requestVersion) return;
          if (!data || data.id === void 0 || String(data.id) !== String(id) || !Array.isArray(data.orderDetailList)) throw new Error("\u8BA2\u5355\u4E0D\u5B58\u5728");
          this.orderDetailsData = data;
          this.loaded = true;
          store.commit("initdishListMut", data.orderDetailList);
          if (data.status === 1) this.runTimeBack(data.orderTime);
        } catch (error) {
          if (!this.disposed && version === this.requestVersion) {
            this.clearDetails();
            failure(uni, error);
          }
        } finally {
          if (!this.disposed && version === this.requestVersion) {
            this.loading = false;
            uni.hideLoading();
          }
        }
      });
    },
    handleReminder(type, id) {
      return __async(this, null, function* () {
        try {
          accepted(yield api.reminderOrder(id));
          if (this.disposed) return;
          this.showConfirm = true;
          this.textTip = "\u60A8\u7684\u50AC\u5355\u4FE1\u606F\u5DF2\u53D1\u51FA\uFF01";
          this.$refs.commonPopup.open(type);
          this.orderId = id;
        } catch (error) {
          if (!this.disposed) failure(uni, error);
        }
      });
    },
    cancel(type, obj) {
      return __async(this, null, function* () {
        try {
          accepted(yield api.cancelOrder(obj.id));
          if (this.disposed) return;
          this.clearTimer();
          this.isPayment = true;
          this.showConfirm = true;
          this.textTip = "\u60A8\u7684\u8BA2\u5355\u5DF2\u53D6\u6D88\uFF01";
          this.$refs.commonPopup.open(type);
          this.orderId = obj.id;
        } catch (error) {
          if (!this.disposed) failure(uni, error);
        }
      });
    },
    handleCancel(type, obj) {
      if (obj.status === 1 || obj.status === 2) return this.cancel(type, obj);
      this.showConfirm = false;
      this.$refs.commonPopup.open(type);
      this.textTip = "\u8BF7\u8054\u7CFB\u5546\u5BB6\u8FDB\u884C\u53D6\u6D88\uFF01";
    },
    oneMoreOrder(id) {
      return __async(this, null, function* () {
        try {
          accepted(yield api.delShoppingCart());
          if (this.disposed) return;
          accepted(yield api.repetitionOrder(id));
          if (this.disposed) return;
          uni.redirectTo({ url: "/pages/index/index" });
        } catch (error) {
          if (!this.disposed) failure(uni, error);
        }
      });
    },
    statusWord(status) {
      if (this.timeout && status === 1 || status === 6) return "\u8BA2\u5355\u5DF2\u53D6\u6D88";
      return { 2: "\u7B49\u5F85\u5546\u6237\u63A5\u5355", 3: "\u5546\u5BB6\u5DF2\u63A5\u5355", 4: "\u8BA2\u5355\u6D3E\u9001\u4E2D", 5: "\u8BA2\u5355\u5DF2\u5B8C\u6210" }[status];
    },
    runTimeBack(time) {
      this.clearTimer();
      if (this.disposed || this.hidden || this.orderDetailsData.status !== 1 || typeof time !== "string") return;
      const remaining = 15 * 60 * 1e3 - (Date.now() - Date.parse(time.replace(/-/g, "/")));
      if (!Number.isFinite(remaining)) return;
      if (remaining <= 0) {
        this.timeout = true;
        this.cancel("center", this.orderDetailsData);
        return;
      }
      this.timeout = false;
      this.rocallTime = String(Math.floor(remaining / 6e4)).padStart(2, "0") + ":" + String(Math.floor(remaining / 1e3) % 60).padStart(2, "0");
      this.times = setTimeout(() => this.runTimeBack(time), 1e3);
    },
    goBack() {
      uni.redirectTo({ url: "/pages/historyOrder/historyOrder" });
    },
    openPopuos(type) {
      this.$refs.commonPopup.open(type);
    },
    handleRefund(type) {
      this.showConfirm = false;
      this.openPopuos(type);
      this.textTip = "\u8BF7\u8054\u7CFB\u5546\u5BB6\u8FDB\u884C\u9000\u6B3E\uFF01";
    },
    handlePhone(type) {
      this.$refs.phone.open(type);
    },
    closePopup(type) {
      this.$refs.phone.close(type);
    },
    closePopupInfo(type) {
      this.$refs.commonPopup.close(type);
      return this.getBaseData(this.orderId);
    },
    handlePay(id) {
      store.commit("setOrderData", { orderNumber: this.orderDetailsData.number, orderAmount: this.orderDetailsData.amount, orderTime: this.orderDetailsData.orderTime });
      uni.redirectTo({ url: "/pages/pay/index?orderId=" + id });
    },
    change() {
    },
    call() {
      tools.call(store.state.shopPhone);
    }
  }
};

// src/Details.vue
var Details_default = details_default;

var render = function() {var _vm=this;var _h=_vm.$createElement;var _c=_vm._self._c||_h;
  var m0 = _vm.statusWord(_vm.orderDetailsData.status);
  var l0 = _vm.__map(_vm.orderDataes, function (obj, index) {
    var g0 = obj.amount.toFixed(2);
    return {
      $orig: _vm.__get_orig(obj),
      g0: g0
    };
  });
  if (!_vm._isMounted) {
    _vm.e0 = function ($event) {
      _vm.showDisplay = !_vm.showDisplay;
    };
  }
  _vm.$mp.data = Object.assign({}, {
    $root: {
      m0: m0,
      l0: l0
    }
  });
}; var staticRenderFns = [];
render._withStripped = true;
var load = wx.__webpack_require_UNI_MP_PLUGIN__;
var page = load(11).default(module.exports.default, render, staticRenderFns, false, null, '54d3589c', null, false).exports;
load(1).createPage(page);
