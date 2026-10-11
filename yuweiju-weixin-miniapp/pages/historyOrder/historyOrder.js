// Generated from history-source/src/HistoryOrder.vue. Run npm run build in history-source.
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

// src/HistoryOrder.vue
var HistoryOrder_exports = {};
__export(HistoryOrder_exports, {
  default: () => HistoryOrder_default
});
module.exports = __toCommonJS(HistoryOrder_exports);

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

// src/history.js
var { store, api, uni, tools, components } = dependencies();
var history_default = {
  components,
  data() {
    return {
      recentOrdersList: [],
      pageInfo: { page: 1, pageSize: 10, total: 0 },
      status: "",
      loadingType: 0,
      showTitle: false,
      scrollinto: "tab0",
      scrollH: 0,
      tabIndex: 0,
      tabBars: ["\u5168\u90E8\u8BA2\u5355", "\u5F85\u4ED8\u6B3E", "\u5DF2\u53D6\u6D88"],
      textTip: "",
      showConfirm: false,
      isEmpty: false,
      loading: false,
      loadingText: "",
      requestVersion: 0,
      disposed: false,
      shown: false
    };
  },
  onLoad() {
    this.disposed = false;
    return this.reload();
  },
  onShow() {
    if (!this.shown) {
      this.shown = true;
      return;
    }
    return this.reload();
  },
  onUnload() {
    this.dispose();
  },
  beforeDestroy() {
    this.dispose();
  },
  onReady() {
    uni.getSystemInfo({ success: (res) => {
      if (!this.disposed) this.scrollH = res.windowHeight - uni.upx2px(100);
    } });
  },
  onPullDownRefresh() {
    return this.reload().finally(() => uni.stopPullDownRefresh());
  },
  onReachBottom() {
    return this.lower();
  },
  methods: {
    numes(list) {
      return (list || []).reduce((sum, item) => ({ count: sum.count + Number(item.number), total: sum.total + Number(item.number) * Number(item.amount) }), { count: 0, total: 0 });
    },
    statusWord(status, time) {
      return tools.statusWord(status, time);
    },
    getOvertime(time) {
      return tools.getOvertime(time);
    },
    dispose() {
      this.disposed = true;
      this.requestVersion++;
      this.showTitle = false;
      if (this.loading) uni.hideLoading();
      this.loading = false;
    },
    reload() {
      this.requestVersion++;
      this.pageInfo = { page: 1, pageSize: 10, total: 0 };
      this.recentOrdersList = [];
      this.isEmpty = false;
      this.loading = false;
      this.loadingType = 0;
      return this.getList(1);
    },
    getList() {
      return __async(this, arguments, function* (page = this.pageInfo.page) {
        if (this.disposed || this.loading) return;
        const version = ++this.requestVersion;
        this.loading = true;
        this.loadingType = 1;
        this.loadingText = "\u6570\u636E\u52A0\u8F7D\u4E2D...";
        uni.showLoading({ title: "\u52A0\u8F7D\u4E2D", mask: true });
        try {
          const data = accepted(yield api.getOrderPage({ pageSize: 10, page, status: this.status }));
          if (this.disposed || version !== this.requestVersion) return;
          if (!data || !Array.isArray(data.records)) throw new Error("\u52A0\u8F7D\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5");
          this.recentOrdersList = page === 1 ? data.records : this.recentOrdersList.concat(data.records);
          this.pageInfo.page = page;
          this.pageInfo.total = Number(data.total);
          this.isEmpty = true;
          this.loadingType = this.recentOrdersList.length >= this.pageInfo.total || data.records.length === 0 ? 2 : 0;
          this.loadingText = this.loadingType === 2 ? "\u6CA1\u6709\u66F4\u591A\u4E86" : "";
        } catch (error) {
          if (!this.disposed && version === this.requestVersion) {
            this.loadingType = 0;
            this.loadingText = "";
            failure(uni, error);
          }
        } finally {
          if (version === this.requestVersion && !this.disposed) {
            this.loading = false;
            uni.hideLoading();
          }
        }
      });
    },
    lower() {
      if (this.loading || this.loadingType === 2 || this.recentOrdersList.length >= this.pageInfo.total) return Promise.resolve();
      this.showTitle = true;
      return this.getList(this.pageInfo.page + 1);
    },
    dataAdd() {
      return this.lower();
    },
    changeTab(index) {
      if (this.tabIndex === index) return Promise.resolve();
      this.tabIndex = index;
      this.status = index === 1 ? 1 : index === 2 ? 6 : "";
      this.scrollinto = "tab" + index;
      return this.reload();
    },
    onChangeSwiperTab(event) {
      return this.changeTab(event.detail.current);
    },
    oneMoreOrder(id) {
      return __async(this, null, function* () {
        try {
          accepted(yield api.delShoppingCart());
          if (this.disposed) return;
          accepted(yield api.repetitionOrder(id));
          if (this.disposed) return;
          const pages = getCurrentPages();
          const routeIndex = pages.findIndex((page) => page.route === "pages/index/index");
          uni.navigateBack({ delta: routeIndex > -1 ? pages.length - routeIndex : 1 });
        } catch (error) {
          if (!this.disposed) failure(uni, error);
        }
      });
    },
    goDetail(id) {
      store.commit("setAddressBackUrl", "/pages/historyOrder/historyOrder");
      uni.redirectTo({ url: "/pages/details/index?orderId=" + id });
    },
    handleReminder(type, id) {
      return __async(this, null, function* () {
        try {
          accepted(yield api.reminderOrder(id));
          if (this.disposed) return;
          this.showConfirm = true;
          this.textTip = "\u60A8\u7684\u50AC\u5355\u4FE1\u606F\u5DF2\u53D1\u51FA\uFF01";
          this.$refs.commonPopup.open(type);
          yield this.reload();
        } catch (error) {
          if (!this.disposed) failure(uni, error);
        }
      });
    },
    closePopup(type) {
      this.$refs.commonPopup.close(type);
    },
    goBack() {
      uni.redirectTo({ url: "/pages/my/my" });
    }
  }
};

// src/HistoryOrder.vue
var HistoryOrder_default = history_default;

var render = function() {var _vm=this;var _h=_vm.$createElement;var _c=_vm._self._c||_h;
  var l1 = _vm.__map(_vm.tabBars, function (tab, tabNumber) {
    var l0 = _vm.__map(_vm.recentOrdersList, function (item, index) {
      var m0 = _vm.statusWord(item.status, item.orderTime);
      var g0 = item.amount.toFixed(2);
      var m1 = _vm.numes(item.orderDetailList);
      var m2 = _vm.getOvertime(item.orderTime);
      return {
        $orig: _vm.__get_orig(item),
        m0: m0,
        g0: g0,
        m1: m1,
        m2: m2
      };
    });
    return {
      $orig: _vm.__get_orig(tab),
      l0: l0
    };
  });
  _vm.$mp.data = Object.assign({}, {
    $root: {
      l1: l1
    }
  });
}; var staticRenderFns = [];
render._withStripped = true;
var load = wx.__webpack_require_UNI_MP_PLUGIN__;
var page = load(11).default(module.exports.default, render, staticRenderFns, false, null, '5cf07246', null, false).exports;
load(1).createPage(page);
