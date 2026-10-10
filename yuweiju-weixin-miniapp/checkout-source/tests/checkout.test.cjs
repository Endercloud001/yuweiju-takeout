const test = require('node:test');
const assert = require('node:assert/strict');
const { harness, plain } = require('./legacy-harness.cjs');
const address = { id: 41, provinceName: '测试省', cityName: '测试市', districtName: '测试区', detail: '合成路1号', phone: '15200000001', consignee: '合成收件人', sex: '1', label: '1' };
const cart = [
  { id: 1, name: '测试菜品', number: 2, amount: 12.5, image: '/static/test.png', dishFlavor: '少盐' },
  { id: 2, name: '测试套餐', number: 1, amount: 20, image: '/static/test.png' },
  { id: 3, name: '测试饮料', number: 3, amount: 4, image: '/static/test.png' },
  { id: 4, name: '测试小吃', number: 1, amount: 6, image: '/static/test.png' }
];
function response(options) {
  if (options.url.endsWith('/addressBook/list')) return { code: 1, data: [address] };
  if (options.url.endsWith('/addressBook/default')) return { code: 1, data: address };
  if (options.url.endsWith('/estimatedDeliveryTime')) return { code: 1, data: '12:30' };
  throw new Error('Unexpected synthetic request ' + options.url);
}
async function checkout(env) {
  env.store.commit('initdishListMut', plain(cart));
  const page = env.instance();
  await env.options.onLoad.call(page);
  await Promise.resolve();
  return page;
}
test('real generated page registers with old adapter; cart quantities, fees and render stay compatible', async () => {
  const env = harness({ respond: response });
  const page = await checkout(env);
  assert.equal(env.registrations.length, 1);
  assert.equal(typeof env.registrations[0].methods.__e, 'function');
  assert.equal(env.options._scopeId, 'data-v-0ca91b30');
  assert.equal(page.orderDishNumber, 7);
  assert.equal(page.orderDishPrice, 76);
  assert.equal(page.orderDataes.length, 3);
  assert.equal(page.orderDataes[0].number, 2);
  env.options.render.call(page);
  assert.equal(page.$mp.data.$root.l0[0].g0, '12.50');
  assert.equal(page.$mp.data.$root.g1, '76.00');
  assert.equal(page.$mp.data.$root.g2, '76.00');
  page.showDisplay = true;
  env.options.render.call(page);
  assert.equal(page.$mp.data.$root.l0.length, 4);
  env.store.commit('initdishListMut', []); page.computOrderInfo();
  assert.equal(page.orderDishNumber, 0); assert.equal(page.orderDishPrice, 6);
  page.$destroy();
});
test('default/selected address and actual old address/remark return handlers share the same store', async () => {
  const env = harness({ respond: response });
  const page = await checkout(env);
  assert.equal(page.addressBookId, 41);
  assert.equal(page.address, '测试省测试市测试区合成路1号');
  assert.equal(env.store.state.arrivals, '12:30');
  page.goAddress();
  assert.equal(env.store.state.addressBackUrl, '/pages/order/index');
  assert.equal(env.redirects.at(-1), '/pages/address/address');
  env.capture('pages/address/address.js');
  const addressScript = env.load(89).default;
  // Locate by the actual emitted Vue options, rather than asserting copied setter logic.
  assert.equal(typeof addressScript.methods.choseAddress, 'function');
  const chosen = { ...address, id: 42, detail: '合成路2号', label: '2' };
  const addressPage = { addressBackUrl: '/pages/order/index', setAddress: value => env.store.commit('setAddress', value) };
  addressScript.methods.choseAddress.call(addressPage, {}, chosen);
  assert.match(env.redirects.at(-1), /^\/pages\/order\/index\?address=/);
  env.capture('pages/remark/index.js');
  const remarkScript = env.load(104).default;
  assert.equal(typeof remarkScript.methods.handleSaveRemark, 'function');
  remarkScript.methods.handleSaveRemark.call({ remark: '合成备注：少辣', setRemark: value => env.store.commit('setRemark', value) });
  const returned = env.instance(); await env.options.onLoad.call(returned);
  assert.equal(returned.addressBookId, 42);
  assert.equal(returned.address, '测试省测试市测试区合成路2号');
  assert.equal(returned.tagLabel, '2');
  assert.equal(returned.remark, '合成备注：少辣');
  returned.goRemark(); assert.equal(env.redirects.at(-1), '/pages/remark/index');
  returned.addressList = []; returned.goAddress(); assert.equal(env.redirects.at(-1), '/pages/addOrEditAddress/addOrEditAddress');
  page.$destroy(); returned.$destroy();
});
test('tableware confirmation/cancel and delivery date/time popups keep existing behavior', async () => {
  const env = harness({ respond: response }); const page = await checkout(env);
  page.openPopuos('bottom'); assert.equal(page.popupMode, 'bottom');
  page.changeCont('3'); page.handlePiker();
  assert.equal(page.num, 3); assert.equal(page.status, 0); assert.equal(page.tablewareData, '3份'); assert.equal(page.popupMode, '');
  page.changeCont('无需餐具'); page.handlePiker(); assert.equal(page.num, 0);
  page.tableware = ''; page.handleRadio({ detail: { value: '依据餐量提供' } }); page.handlePiker();
  assert.equal(page.status, 1); assert.equal(page.num, 7);
  page.openPopuos('bottom'); page.changeCont('5'); page.closePopup(); assert.equal(page.num, 7);
  page.openTimePopuo('bottom'); assert.equal(page.timeMode, 'bottom');
  page.dateChange(1); assert.equal(page.newDateData[0], '09:00'); assert.equal(page.isTomorrow, true);
  page.timeClick('11:30', 5); assert.equal(page.arrivalTime, '11:30'); assert.equal(env.store.state.arrivals, '11:30'); assert.equal(page.timeMode, '');
  page.newDate = 9 * 3600; page.dateChange(0); assert.equal(page.newDateData[0], '立即派送'); assert.equal(page.newDateData[1], '10:30');
  page.setTime('立即派送'); await Promise.resolve(); assert.equal(page.arrivalTime, '12:30');
  page.$destroy();
});
test('submit sends public fields and current authentication, hands order to unchanged simulated payment', async () => {
  const order = { id: 501, orderNumber: 'SYNTHETIC-501', amount: 76, estimatedDeliveryTime: '2026-10-10 12:35:00' };
  const env = harness({ respond: options => {
    if (options.url.endsWith('/submit')) return { code: 1, data: order };
    if (options.url.endsWith('/payment')) return { code: 1, data: null };
    return response(options);
  } });
  const page = await checkout(env);
  env.store.commit('setToken', 'synthetic-new-token');
  page.remark = '测试备注'; page.status = 1; page.num = 7;
  await page.payOrderHandle();
  const submit = env.requests.find(request => request.url.endsWith('/submit'));
  assert.equal(submit.url, 'http://localhost:8080/user/order/submit'); assert.equal(submit.method, 'POST');
  assert.equal(submit.header.authentication, 'synthetic-new-token');
  assert.equal(submit.header['Content-Type'], 'application/json');
  assert.deepEqual(submit.data, { payMethod: 1, addressBookId: 41, remark: '测试备注', estimatedDeliveryTime: null,
    deliveryStatus: 0, tablewareStatus: 1, tablewareNumber: 7, packAmount: 7, amount: 76 });
  assert.deepEqual(plain(env.store.state.orderData), order); assert.equal(env.store.state.remarkData, '');
  assert.equal(env.store.state.arrivals, '12:35'); assert.equal(env.redirects.at(-1), '/pages/pay/index?orderId=501');
  env.capture('pages/pay/index.js'); const payScript = env.load(61).default;
  const payment = { timeout: false, times: null, orderId: 501, orderDataInfo: env.store.state.orderData, activeRadio: 0 };
  payScript.methods.handleSave.call(payment); await Promise.resolve(); await Promise.resolve();
  const payRequest = env.requests.find(request => request.url.endsWith('/payment'));
  assert.deepEqual(payRequest.data, { orderNumber: 'SYNTHETIC-501', payMethod: 1 });
  assert.equal(payRequest.header.authentication, 'synthetic-new-token');
  assert.equal(env.redirects.at(-1), '/pages/success/index?orderId=501');
  page.$destroy();
});
test('missing address and rejected business/network submit preserve remark/order, unlock and allow retry', async () => {
  let answer = { code: 0, msg: '测试拒绝' };
  const env = harness({ respond: options => options.url.endsWith('/submit') ? answer : response(options) });
  const page = await checkout(env); page.address = '';
  const before = env.requests.length; await page.payOrderHandle(); assert.equal(env.requests.length, before);
  assert.equal(env.toasts.at(-1), '请选择收货地址'); assert.equal(page.isHandlePy, false);
  page.address = '合成地址'; page.remark = '保留备注'; env.store.commit('setRemark', page.remark);
  await page.payOrderHandle(); assert.equal(env.toasts.at(-1), '测试拒绝'); assert.equal(page.isHandlePy, false);
  assert.equal(env.store.state.remarkData, '保留备注'); assert.deepEqual(plain(env.store.state.orderData), {});
  answer = new Error('合成网络失败'); await page.payOrderHandle(); assert.equal(env.toasts.at(-1), '合成网络失败'); assert.equal(page.isHandlePy, false);
  assert.equal(env.redirects.length, 0);
  answer = { code: 1, data: { id: 502 } }; await page.payOrderHandle(); assert.equal(env.redirects.at(-1), '/pages/pay/index?orderId=502');
  page.$destroy();
});
test('in-flight submit prevents duplicate requests; authentication rejection never navigates to payment', async () => {
  let finish;
  const env = harness({ respond: options => options.url.endsWith('/submit') ? { pending: opts => { finish = opts; } } : response(options) });
  const page = await checkout(env); const pending = page.payOrderHandle();
  await page.payOrderHandle(); assert.equal(env.requests.filter(request => request.url.endsWith('/submit')).length, 1);
  finish.success({ data: { code: 401, msg: '合成未登录' } }); await pending;
  assert.equal(page.isHandlePy, false); assert.equal(env.toasts.at(-1), '合成未登录'); assert.equal(env.redirects.length, 0);
  assert.equal(env.requests.at(-1).header.authentication, 'synthetic-test-token');
  page.$destroy();
});
test('unchanged normal login stores token and checkout uses it without a second store', async () => {
  const env = harness({ token: '', respond: options => options.url.endsWith('/user/user/login') ? { code: 1, data: { token: 'synthetic-login-token' } } : response(options) });
  const home = env.load(20).default;
  let initialized = false;
  home.methods.getData.call({
    getShopInfo() {}, token: () => env.store.state.token,
    setBaseUserInfo: user => env.store.commit('setBaseUserInfo', user),
    setToken: token => env.store.commit('setToken', token), init() { initialized = true; }
  });
  await Promise.resolve(); await Promise.resolve();
  assert.equal(initialized, true); assert.equal(env.store.state.token, 'synthetic-login-token');
  assert.equal(env.storage.get('token'), 'synthetic-login-token');
  assert.equal(env.store.state.baseUserInfo.nickName, '合成登录');
  const login = env.requests.find(request => request.url.endsWith('/user/user/login'));
  assert.equal(login.method, 'POST'); assert.deepEqual(login.data, { code: 'synthetic-login-code' });
  const page = await checkout(env);
  const estimate = env.requests.find(request => request.url.endsWith('/estimatedDeliveryTime'));
  assert.equal(estimate.header.authentication, 'synthetic-login-token');
  assert.deepEqual(estimate.data, { addressBookId: 41 });
  page.$destroy();
});
test('no default address remains editable; rejected estimation displays failure and does not forge a time', async () => {
  const env = harness({ respond: options => {
    if (options.url.endsWith('/addressBook/list')) return { code: 1, data: [] };
    if (options.url.endsWith('/addressBook/default')) return { code: 1, data: null };
    if (options.url.endsWith('/estimatedDeliveryTime')) return { code: 0, msg: '合成超出范围' };
    throw new Error('Unexpected request');
  } });
  const page = await checkout(env); assert.equal(page.address, ''); assert.equal(page.addressBookId, '');
  page.goAddress(); assert.equal(env.redirects.at(-1), '/pages/addOrEditAddress/addOrEditAddress');
  await page.applyAddress(address); assert.equal(page.arrivalTime, '合成超出范围'); assert.equal(env.store.state.arrivals, '合成超出范围');
  page.$destroy();
});
