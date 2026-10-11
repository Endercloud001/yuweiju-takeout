const { harness: checkoutHarness, plain, root } = require('../../checkout-source/tests/legacy-harness.cjs');
function harness(page = 'HistoryOrder', options) {
  const env = checkoutHarness(options);
  const loading = [], navigation = [], phones = [], timers = new Map(); let nextTimer = 0;
  env.wx.showLoading = options => loading.push(options.title);
  env.wx.hideLoading = () => loading.push('hide');
  env.wx.stopPullDownRefresh = options => { loading.push('refresh-stop'); options.success && options.success({ errMsg: 'stopPullDownRefresh:ok' }); };
  env.wx.navigateBack = options => { navigation.push(options.delta); options.success && options.success({ errMsg: 'navigateBack:ok' }); };
  env.wx.makePhoneCall = options => { phones.push(options.phoneNumber); options.success && options.success({ errMsg: 'makePhoneCall:ok' }); };
  env.context.getCurrentPages = () => [{ route: 'pages/index/index' }, { route: 'pages/my/my' }, { route: 'pages/historyOrder/historyOrder' }];
  // Only timer scheduling/WeChat IO is replaced; Vue/API/request/store are actual artifacts.
  env.context.setTimeout = (fn, ms) => { const id = ++nextTimer; timers.set(id, { fn, ms }); return id; };
  env.context.clearTimeout = id => timers.delete(id);
  env.context.module = { exports: {} };
  env.capture(page === 'HistoryOrder' ? 'pages/historyOrder/historyOrder.js' : 'pages/details/index.js');
  const pageOptions = env.context.module.exports.default;
  function instance(optionsOverride = pageOptions) {
    const vm = env.instance(optionsOverride);
    const popups = [];
    vm.$refs.commonPopup = { open: type => popups.push(['common', 'open', type]), close: type => popups.push(['common', 'close', type]) };
    vm.$refs.phone = { open: type => popups.push(['phone', 'open', type]), close: type => popups.push(['phone', 'close', type]) };
    vm.popups = popups;
    return vm;
  }
  return { ...env, options: pageOptions, instance, loading, navigation, phones, timers };
}
const order = (id = 974, status = 5) => ({ id, number: 'synthetic-' + id, status, amount: 19, packAmount: 1,
  orderTime: '2020-10-05 12:00:00', orderDetailList: [{ id: 1, number: 1, amount: 18, name: '合成商品', image: 'https://example.invalid/stored-history.jpg' }] });
const flush = () => new Promise(resolve => setImmediate(resolve));
module.exports = { harness, plain, root, order, flush };
