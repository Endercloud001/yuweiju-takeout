// Generated from home-source native template/render and cart adapter. Run npm run build.
(global.webpackJsonp = global.webpackJsonp || []).push([["pages/index/index"], {
15: function(module, exports, load) { load(5); wx.__webpack_require_UNI_MP_PLUGIN__ = load; load(1).createPage(load(16).default); },
16: function(module, exports, load) {
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

// src/home.js
var home_exports = {};
__export(home_exports, {
  createHome: () => createHome
});
module.exports = __toCommonJS(home_exports);
function createHome(load) {
  const legacy = load(20).default;
  const store = load(12).default;
  const api = load(24);
  const uni = load(1).default;
  const toast = (error) => uni.showToast({ title: error && (error.msg || error.message) || "\u8D2D\u7269\u8F66\u8BFB\u53D6\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5", icon: "none" });
  const invalid = (item) => item.available === false || item.amount == null || !Number.isFinite(Number(item.amount)) || Number(item.amount) < 0 || !Number.isInteger(item.number) || item.number <= 0;
  return __spreadProps(__spreadValues({}, legacy), {
    data() {
      return __spreadProps(__spreadValues({}, legacy.data.call(this)), { cartReady: false, cartBusy: false });
    },
    computed: __spreadProps(__spreadValues({}, legacy.computed), {
      cartInvalid() {
        return this.orderListDataes.some(invalid);
      }
    }),
    methods: __spreadProps(__spreadValues({}, legacy.methods), {
      computOrderInfo() {
        this.orderDishNumber = this.orderListDataes.reduce((sum, item) => sum + (Number(item.number) || 0), 0);
        this.orderDishPrice = this.cartInvalid ? null : this.orderListDataes.reduce((sum, item) => sum + Math.round(Number(item.amount) * 100) * item.number, 0) / 100;
      },
      getTableOrderDishListes() {
        return __async(this, null, function* () {
          this.cartReady = false;
          try {
            const res = yield api.getShoppingCartList({});
            if (res.code !== 1 || !Array.isArray(res.data)) throw res;
            store.commit("initdishListMut", res.data);
            this.computOrderInfo();
            this.cartReady = true;
            return true;
          } catch (error) {
            toast(error);
            return false;
          }
        });
      },
      goOrder() {
        return __async(this, null, function* () {
          if (this.cartBusy) return;
          if (!(yield this.getTableOrderDishListes())) return;
          if (this.cartInvalid) {
            this.openOrderCartList = true;
            toast({ msg: "\u8BF7\u5148\u79FB\u9664\u5931\u6548\u5546\u54C1\u540E\u7ED3\u7B97" });
            return;
          }
          if (!this.orderDishNumber) {
            toast({ msg: "\u8D2D\u7269\u8F66\u4E3A\u7A7A" });
            return;
          }
          uni.navigateTo({ url: "/pages/order/index" });
        });
      },
      redDishAction(item, form) {
        return __async(this, null, function* () {
          if (form !== "\u8D2D\u7269\u8F66") return legacy.methods.redDishAction.call(this, item, form);
          if (this.cartBusy) return;
          this.cartBusy = true;
          this.cartReady = false;
          try {
            const params = { dishFlavor: item.dishFlavor || "" };
            if (item.dishId != null) params.dishId = item.dishId;
            else params.setmealId = item.setmealId;
            const res = yield api.newShoppingCartSub(params);
            if (res.code !== 1) throw res;
            yield this.getTableOrderDishListes();
            if (this.rightIdAndType.id) this.getDishListDataes(this.rightIdAndType);
          } catch (error) {
            toast(error);
          } finally {
            this.cartBusy = false;
          }
        });
      },
      addDishAction(item, form) {
        if (form === "\u8D2D\u7269\u8F66" && invalid(item)) {
          toast({ msg: item.unavailableReason || "\u5546\u54C1\u4E0D\u53EF\u8D2D\u4E70\uFF0C\u8BF7\u79FB\u9664" });
          return;
        }
        return legacy.methods.addDishAction.call(this, item, form);
      },
      clearCardOrder() {
        if (!this.cartReady || this.cartInvalid) {
          toast({ msg: "\u8BF7\u9010\u9879\u79FB\u9664\u5931\u6548\u5546\u54C1" });
          return;
        }
        return legacy.methods.clearCardOrder.call(this);
      }
    })
  });
}

var options = module.exports.createHome(load);
// Recovered native uni render for index.wxml. Keep $root bindings paired with the native template.
var render = function() {
  var _vm = this
  var _h = _vm.$createElement
  var _c = _vm._self._c || _h
  var l0 =
    _vm.dishListItems && _vm.dishListItems.length > 0
      ? _vm.__map(_vm.dishListItems, function(item, index) {
          var $orig = _vm.__get_orig(item)

          var g0 = item.price.toFixed(2)
          return {
            $orig: $orig,
            g0: g0
          }
        })
      : null
  var m0 = _vm.orderListData()
  var g1 = !(m0.length === 0) ? (_vm.cartReady && !_vm.cartInvalid ? _vm.orderDishPrice.toFixed(2) : '待恢复') : null

  var l2 = _vm.__map(_vm.moreNormdata, function(obj, index) {
    var $orig = _vm.__get_orig(obj)

    var l1 = _vm.__map(obj.value, function(item, ind) {
      var $orig = _vm.__get_orig(item)

      var g2 = _vm.flavorDataes.findIndex(function(it) {
        return item === it
      })
      return {
        $orig: $orig,
        g2: g2
      }
    })

    return {
      $orig: $orig,
      l1: l1
    }
  })

  var g3 = _vm.dishDetailes.type == 1 ? _vm.dishDetailes.price.toFixed(2) : null

  if (!_vm._isMounted) {
    _vm.e0 = function() {
      return (_vm.openOrderCartList = !_vm.openOrderCartList)
    }

    _vm.e1 = function() {
      return (_vm.openDetailPop = false)
    }

    _vm.e2 = function() {
      return (_vm.openDetailPop = false)
    }

    _vm.e3 = function($event) {
      _vm.openOrderCartList = !_vm.openOrderCartList
    }

    _vm.e4 = function($event) {
      $event.stopPropagation()
      _vm.openOrderCartList = _vm.openOrderCartList
    }
  }

  _vm.$mp.data = Object.assign(
    {},
    {
      $root: {
        l0: l0,
        m0: m0,
        g1: g1,
        l2: l2,
        g3: g3
      }
    }
  )
}

render._withStripped = true;
module.exports = { default: load(11).default(options, render, [], false, null, '57280228', null, false).exports };
}
}, [[15,"common/runtime","common/vendor"]]]);
