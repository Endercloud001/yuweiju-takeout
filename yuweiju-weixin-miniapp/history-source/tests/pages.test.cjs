const test = require('node:test');
const assert = require('node:assert/strict');
const { harness, plain, order, flush } = require('./runtime.cjs');
const hook = (page, name, ...args) => page.$options[name][0].apply(page, args);

test('actual legacy API sends own authentication, public page/status parameters and persisted data', async () => {
  const env = harness('HistoryOrder', { respond: () => ({ code: 1, data: { records: [order()], total: 1 } }) });
  const page = env.instance(); await hook(page, 'onLoad');
  assert.match(env.requests[0].url, /\/user\/order\/historyOrders$/);
  assert.equal(env.requests[0].header.authentication, 'synthetic-test-token');
  assert.equal(env.requests[0].method, 'GET'); assert.deepEqual(env.requests[0].data, { pageSize: 10, page: 1, status: '' });
  assert.equal(page.recentOrdersList[0].amount, 19); assert.equal(page.recentOrdersList[0].orderDetailList[0].image, order().orderDetailList[0].image);
  assert.equal(page.loading, false); assert.equal(page.loadingType, 2); page.$options.render.call(page); page.$destroy();
});

test('real generated list pages, blocks duplicate loading, retries failed page without skipping, stops at total', async () => {
  let failed = true, pending;
  const env = harness('HistoryOrder', { respond: options => {
    if (options.data.page === 1) return { code: 1, data: { records: Array.from({ length: 10 }, (_, i) => order(i + 1)), total: 11 } };
    if (failed) { failed = false; return new Error('合成网络失败'); }
    return { pending: options => { pending = options; } };
  } });
  const page = env.instance(); await hook(page, 'onLoad'); await page.lower();
  assert.equal(page.pageInfo.page, 1); assert.equal(page.recentOrdersList.length, 10); assert.equal(page.loading, false);
  const more = page.lower(); await page.lower(); assert.equal(env.requests.length, 3);
  pending.success({ data: { code: 1, data: { records: [order(11)], total: 11 } }, statusCode: 200 }); await more;
  assert.equal(page.pageInfo.page, 2); assert.equal(page.recentOrdersList.length, 11); assert.equal(page.loadingType, 2);
  await hook(page, 'onReachBottom'); assert.equal(env.requests.length, 3); page.$destroy();
});

test('tabs use existing statuses and reset empty/total state; page reload and onShow replace records/status', async () => {
  let status = 2;
  const env = harness('HistoryOrder', { respond: options => ({ code: 1, data: { records: options.data.status === 6 ? [] : [order(974, status)], total: options.data.status === 6 ? 0 : 1 } }) });
  const page = env.instance(); await hook(page, 'onLoad'); hook(page, 'onShow'); assert.equal(env.requests.length, 1);
  await page.changeTab(1); assert.equal(env.requests.at(-1).data.status, 1);
  await page.onChangeSwiperTab({ detail: { current: 2 } }); assert.equal(env.requests.at(-1).data.status, 6);
  assert.equal(page.isEmpty, true); assert.equal(page.recentOrdersList.length, 0); await page.lower(); assert.equal(env.requests.length, 3);
  await page.changeTab(0); status = 5; await hook(page, 'onShow');
  assert.equal(page.recentOrdersList.length, 1); assert.equal(page.recentOrdersList[0].status, 5);
  await hook(page, 'onPullDownRefresh'); assert.equal(page.pageInfo.page, 1); assert.equal(page.recentOrdersList.length, 1);
  assert.equal(env.loading.at(-1), 'refresh-stop'); page.$destroy();
});

test('tab switch discards a late old response, unload discards current response and ends loading', async () => {
  const pending = [];
  const env = harness('HistoryOrder', { respond: () => ({ pending: options => pending.push(options) }) });
  const page = env.instance(); const first = hook(page, 'onLoad'); const second = page.changeTab(1);
  pending[1].success({ data: { code: 1, data: { records: [order(12, 1)], total: 1 } } }); await second;
  pending[0].success({ data: { code: 1, data: { records: [order(13)], total: 100 } } }); await first;
  assert.equal(page.recentOrdersList[0].id, 12); assert.equal(page.pageInfo.total, 1);
  const third = page.reload(); hook(page, 'onUnload'); pending[2].success({ data: { code: 1, data: { records: [order()], total: 1 } } }); await third;
  assert.equal(page.recentOrdersList.length, 0); assert.equal(page.loading, false); page.$destroy();
});

for (const answer of [{ code: 0, msg: '拒绝' }, { code: 200, data: { records: [order()], total: 1 } }, new Error('合成网络错误')]) {
  test('list business/auth/network rejection ends loading without accepted data: ' + (answer.code ?? 'network'), async () => {
    const env = harness('HistoryOrder', { token: '', respond: () => answer }); const page = env.instance();
    await hook(page, 'onLoad'); assert.equal(env.requests[0].header.authentication, ''); assert.equal(page.recentOrdersList.length, 0);
    assert.equal(page.loading, false); assert.ok(env.toasts.length); assert.equal(env.loading.at(-1), 'hide'); page.$destroy();
  });
}

test('detail id routing, persisted19 and original images are rendered using real runtime/store', async () => {
  const env = harness('Details', { respond: () => ({ code: 1, data: order() }) }); const page = env.instance();
  await hook(page, 'onLoad', { orderId: '974' }); hook(page, 'onShow'); assert.equal(env.requests.length, 1);
  assert.match(env.requests[0].url, /\/user\/order\/orderDetail\/974$/); assert.equal(env.requests[0].header.authentication, 'synthetic-test-token');
  assert.equal(page.orderId, '974'); assert.equal(page.orderDetailsData.amount, 19); assert.equal(page.orderDetailsData.packAmount, 1);
  assert.equal(page.orderDataes[0].image, order().orderDetailList[0].image); assert.equal(env.store.state.orderListData.length, 1);
  page.$options.render.call(page); assert.equal(page.$mp.data.$root.l0[0].g0, '18.00');
  page.handlePay(974); assert.deepEqual(plain(env.store.state.orderData), { orderNumber: 'synthetic-974', orderAmount: 19, orderTime: order().orderTime });
  assert.equal(env.redirects.at(-1), '/pages/pay/index?orderId=974'); page.goBack(); assert.equal(env.redirects.at(-1), '/pages/historyOrder/historyOrder'); page.$destroy();
});

test('detail route missing/invalid id never requests and clears stale store details', async () => {
  const env = harness('Details'); const page = env.instance(); env.store.commit('initdishListMut', order().orderDetailList);
  for (const id of [undefined, '', 'NaN', '0', '../974']) { await page.getBaseData(id); assert.equal(page.loaded, false); assert.equal(page.orderDataes.length, 0); }
  assert.equal(env.requests.length, 0); assert.equal(env.store.state.orderListData.length, 0); page.$options.render.call(page); page.$destroy();
});

for (const answer of [{ code: 0, msg: '不属于当前用户' }, { code: 1, data: null }, { code: 1, data: {} }, { code: 200, data: order(975) }, new Error('合成网络错误')]) {
  test('detail denial/empty/failure clears stale details/store and timers: ' + (answer.code ?? 'network') + JSON.stringify(answer.data), async () => {
    let response = { code: 1, data: order() };
    const env = harness('Details', { respond: () => response }); const page = env.instance(); await page.getBaseData(974);
    response = answer; await page.getBaseData(975); assert.equal(page.loaded, false); assert.equal(page.orderDataes.length, 0);
    assert.equal(page.orderDetailsData.amount, undefined); assert.equal(env.store.state.orderListData.length, 0); assert.equal(page.loading, false);
    assert.equal(env.timers.size, 0); assert.ok(env.toasts.length); page.$options.render.call(page); page.$destroy();
  });
}

test('detail refresh replaces status and local items independent from cart store mutations', async () => {
  const data = order(974, 2); data.orderDetailList = [1, 2, 3].map(id => ({ ...order().orderDetailList[0], id }));
  const env = harness('Details', { respond: () => ({ code: 1, data: plain(data) }) }); const page = env.instance();
  await hook(page, 'onLoad', { orderId: 974 }); hook(page, 'onShow'); assert.equal(page.orderDataes.length, 2);
  page.showDisplay = true; assert.equal(page.orderDataes.length, 3); env.store.commit('initdishListMut', []); assert.equal(page.orderDataes.length, 3);
  data.status = 5; await hook(page, 'onShow'); assert.equal(page.orderDetailsData.status, 5); assert.equal(page.showDisplay, false); page.$destroy();
});

test('pending detail results cannot survive route change or destruction', async () => {
  const pending = []; const env = harness('Details', { respond: () => ({ pending: options => pending.push(options) }) }); const page = env.instance();
  const a = page.getBaseData(974), b = page.getBaseData(975);
  pending[1].success({ data: { code: 1, data: order(975) } }); await b;
  pending[0].success({ data: { code: 1, data: order(974) } }); await a; assert.equal(page.orderDetailsData.id, 975);
  const c = page.getBaseData(976); page.$destroy(); pending[2].success({ data: { code: 1, data: order(976) } }); await c;
  assert.equal(page.loaded, false); assert.equal(page.loading, false); assert.equal(env.store.state.orderListData.length, 0);
});

test('countdown replacement, hide/unload/destruction cleanup and expired unpaid cancel use old API', async () => {
  const data = order(974, 1); data.orderTime = new Date().toISOString().replace('T', ' ').slice(0, 19);
  const env = harness('Details', { respond: options => options.url.includes('/orderDetail/') ? { code: 1, data } : { code: 1, data: null } });
  const page = env.instance(); await page.getBaseData(974); assert.equal(env.timers.size, 1); assert.match(page.rocallTime, /^\d\d:\d\d$/);
  await page.getBaseData(974); assert.equal(env.timers.size, 1); hook(page, 'onHide'); assert.equal(env.timers.size, 0);
  page.hidden = false; page.runTimeBack(data.orderTime); hook(page, 'onUnload'); assert.equal(env.timers.size, 0); page.$destroy();
  const expired = env.instance(); expired.orderDetailsData = order(974, 1); expired.runTimeBack(order().orderTime); await flush();
  assert.equal(expired.timeout, true); assert.equal(env.requests.at(-1).method, 'PUT'); assert.match(env.requests.at(-1).url, /\/cancel\/974$/); assert.equal(env.timers.size, 0); expired.$destroy();
});

test('history navigation, reminder reload, sequential reorder and rejected cart clear retain behavior', async () => {
  let denied = false;
  const env = harness('HistoryOrder', { respond: options => denied ? { code: 0, msg: '拒绝' } : { code: 1, data: options.url.includes('historyOrders') ? { records: [order(974, 2)], total: 1 } : null } });
  const page = env.instance(); await hook(page, 'onLoad'); page.goDetail(974);
  assert.equal(env.store.state.addressBackUrl, '/pages/historyOrder/historyOrder'); assert.equal(env.redirects.at(-1), '/pages/details/index?orderId=974');
  await page.handleReminder('center', 974); assert.match(env.requests.at(-2).url, /\/reminder\/974$/); assert.equal(page.recentOrdersList.length, 1); assert.match(page.textTip, /催单/);
  await page.oneMoreOrder(974); assert.equal(env.requests.at(-2).method, 'DELETE'); assert.match(env.requests.at(-1).url, /\/repetition\/974$/); assert.equal(env.requests.at(-1).method, 'POST'); assert.equal(env.navigation.at(-1), 3);
  const count = env.requests.length; denied = true; await page.oneMoreOrder(974); assert.equal(env.requests.length, count + 1); assert.equal(env.navigation.length, 1);
  page.closePopup(); page.goBack(); assert.equal(env.redirects.at(-1), '/pages/my/my'); page.$destroy();
});

test('detail reminder/cancel/refund/phone/reorder actions preserve endpoints, popups and rejection', async () => {
  let denied = false;
  const env = harness('Details', { respond: options => denied ? { code: 0, msg: '拒绝' } : { code: 1, data: options.url.includes('orderDetail') ? order(974, 2) : null } });
  const page = env.instance(); await page.getBaseData(974);
  await page.handleReminder('center', 974); assert.match(page.textTip, /催单/);
  await page.handleCancel('center', page.orderDetailsData); assert.match(env.requests.at(-1).url, /\/cancel\/974$/); assert.match(page.textTip, /已取消/);
  await page.closePopupInfo(); assert.match(env.requests.at(-1).url, /\/orderDetail\/974$/);
  const before = env.requests.length; page.handleCancel('center', order(974, 3)); assert.equal(env.requests.length, before); assert.match(page.textTip, /联系商家/);
  page.handleRefund('center'); assert.match(page.textTip, /退款/); page.handlePhone('bottom'); assert.deepEqual(page.popups.at(-1), ['phone', 'open', 'bottom']);
  env.store.commit('setShopPhone', '10000'); page.call(); assert.deepEqual(env.phones, ['10000']); page.closePopup();
  await page.oneMoreOrder(974); assert.equal(env.requests.at(-2).method, 'DELETE'); assert.equal(env.requests.at(-1).method, 'POST'); assert.equal(env.redirects.at(-1), '/pages/index/index');
  denied = true; const count = env.redirects.length; await page.oneMoreOrder(974); await page.cancel('center', order()); await page.handleReminder('center', 974); assert.equal(env.redirects.length, count); assert.ok(env.toasts.length >= 3); page.$destroy();
});

 test('hidden detail cannot restart a timer from a late successful response; onShow resumes fresh status', async () => {
  const pending = []; const data = order(974, 1); data.orderTime = new Date().toISOString().replace('T', ' ').slice(0, 19);
  const env = harness('Details', { respond: () => ({ pending: options => pending.push(options) }) }); const page = env.instance();
  const first = hook(page, 'onLoad', { orderId: 974 }); hook(page, 'onShow'); hook(page, 'onHide');
  pending[0].success({ data: { code: 1, data } }); await first; assert.equal(env.timers.size, 0);
  const refreshed = hook(page, 'onShow'); pending[1].success({ data: { code: 1, data } }); await refreshed;
  assert.equal(env.timers.size, 1); page.$destroy(); assert.equal(env.timers.size, 0);
});
