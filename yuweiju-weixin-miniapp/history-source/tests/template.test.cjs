const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const compiler = require('vue-template-compiler');
const { harness, order, plain, flush } = require('./runtime.cjs');

// Compile the saved SFC as a Vue VNode tree and run it on the actual old
// Vue/store/API/tools. This exposes row text, conditions, bindings and handlers
// without relying on uni compiler's private generated variable names.
function template(name) {
  const source = fs.readFileSync(path.resolve(__dirname, '../src', name + '.vue'), 'utf8');
  const compiled = compiler.compile(compiler.parseComponent(source).template.content);
  assert.deepEqual(compiled.errors, []);
  const render = new Function(compiled.render);
  return page => render.call(page);
}
function nodes(tree) {
  if (!tree) return [];
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  return [tree, ...nodes(tree.children)];
}
function text(tree) { return nodes(tree).map(node => node.text || '').join('').trim(); }
function hasClass(node, name) {
  const classes = node.data && [node.data.staticClass, node.data.class].flat(Infinity);
  return (classes || []).some(value => typeof value === 'string' && value.split(/\s+/).includes(name));
}
function byClass(tree, name) { return nodes(tree).filter(node => hasClass(node, name)); }
function button(tree, label) { return nodes(tree).find(node => node.tag === 'button' && text(node) === label); }
function tap(node) {
  assert.ok(node, 'visible tap target');
  assert.equal(typeof node.data.on.tap, 'function');
  return node.data.on.tap({ type: 'tap' });
}
function timeAgo(minutes) {
  const d = new Date(Date.now() - minutes * 60000);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

for (const [age, label, canPay] of [[1, '待付款', true], [20, '已取消', false]]) {
  test(`actual history SFC pending row ${label}: scoped label, bindings and detail route`, async () => {
    const data = { ...order(974, 1), orderTime: timeAgo(age) };
    const env = harness('HistoryOrder', { respond: () => ({ code: 1, data: { records: [data], total: 1 } }) });
    const page = env.instance();
    try {
      await page.$options.onLoad[0].call(page);
      const tree = template('HistoryOrder')(page);
      const rows = byClass(tree, 'order_lists');
      assert.equal(rows.length, page.tabBars.length);
      for (const row of rows) {
        assert.equal(text(byClass(row, 'type')[0]), label); // Never match top tab labels.
        assert.equal(text(byClass(row, 'numAndAum')[0]), '￥19.00共1件');
        assert.equal(nodes(row).find(node => node.tag === 'image').data.attrs.src, data.orderDetailList[0].image);
      }
      tap(byClass(rows[0], 'orderBox')[0]);
      assert.equal(env.redirects.at(-1), '/pages/details/index?orderId=974');
      assert.equal(env.store.state.addressBackUrl, '/pages/historyOrder/historyOrder');
    } finally { page.$destroy(); }
  });
  test(`actual history SFC ${canPay ? 'fresh' : 'expired'} pending payment button and route`, () => {
    const env = harness('HistoryOrder'); const page = env.instance();
    try {
      page.recentOrdersList = [{ ...order(974, 1), orderTime: timeAgo(age) }];
      const row = byClass(template('HistoryOrder')(page), 'order_lists')[0];
      const payment = button(row, '去支付');
      assert.equal(Boolean(payment), canPay);
      if (canPay) {
        tap(payment);
        assert.equal(env.redirects.at(-1), '/pages/details/index?orderId=974');
        assert.equal(env.store.state.addressBackUrl, '/pages/historyOrder/historyOrder');
      }
    } finally { page.$destroy(); }
  });
}

test('actual SFC onShow refresh renders fulfilment status and clears the pending payment action', async () => {
  let data = { ...order(974, 1), orderTime: timeAgo(1) };
  const env = harness('HistoryOrder', { respond: () => ({ code: 1, data: { records: [data], total: 1 } }) });
  const page = env.instance(); const render = template('HistoryOrder');
  try {
    await page.$options.onLoad[0].call(page); page.$options.onShow[0].call(page);
    assert.ok(button(byClass(render(page), 'order_lists')[0], '去支付'));
    data = order(974, 5);
    await page.$options.onShow[0].call(page);
    const rows = byClass(render(page), 'order_lists');
    for (const row of rows) {
      assert.equal(text(byClass(row, 'type')[0]), '已完成');
      assert.equal(button(row, '去支付'), undefined);
    }
    assert.equal(page.recentOrdersList.length, 1);
    assert.equal(env.requests.length, 2);
  } finally { page.$destroy(); }
});

for (const age of [1, 20]) {
  test(`actual detail SFC ${age < 15 ? 'fresh' : 'expired'} pending countdown and payment route`, async () => {
    const data = { ...order(974, 1), orderTime: timeAgo(age) };
    const env = harness('Details', { respond: options => ({ code: 1, data: options.method === 'GET' ? data : null }) });
    const page = env.instance();
    try {
      await page.$options.onLoad[0].call(page, { orderId: '974' });
      await flush();
      const tree = template('Details')(page);
      assert.equal(Boolean(button(tree, '立即支付')), age < 15);
      assert.equal(Boolean(button(tree, '取消订单')), age < 15);
      if (age < 15) {
        assert.match(text(byClass(tree, 'time')[0]), /等待支付：\s*\d{2}:\d{2}/);
        assert.equal(env.timers.size, 1);
        tap(button(tree, '立即支付'));
        assert.equal(env.redirects.at(-1), '/pages/pay/index?orderId=974');
        assert.deepEqual(plain(env.store.state.orderData), { orderNumber: data.number, orderAmount: 19, orderTime: data.orderTime });
      } else {
        assert.equal(text(byClass(tree, 'tit')[0]), '订单已取消');
        assert.equal(env.timers.size, 0);
        assert.ok(env.requests.some(request => request.method === 'PUT' && /\/cancel\/974$/.test(request.url)));
      }
    } finally { page.$destroy(); }
  });
}

test('actual detail SFC retains cancellation precedence, status buttons and event arguments', async () => {
  const env = harness('Details', { respond: () => ({ code: 1 }) });
  const page = env.instance(); const render = template('Details');
  try {
    page.loaded = true;
    for (const [status, timeout, labels, title] of [
      [1, false, ['取消订单', '立即支付', '再来一单'], '等待支付'],
      [1, true, ['再来一单'], '订单已取消'],
      [2, true, ['取消订单', '催单', '再来一单'], '等待商户接单'],
      [3, true, ['取消订单', '再来一单'], '商家已接单'],
      [4, true, ['取消订单', '再来一单'], '订单派送中'],
      [5, false, ['申请退款', '再来一单'], '订单已完成'],
      [6, false, ['再来一单'], '订单已取消'],
      [7, false, [], '']
    ]) {
      page.orderDetailsData = order(974, status); page.timeout = timeout;
      const tree = render(page); const actions = byClass(tree, 'againBtn')[0];
      assert.deepEqual(nodes(actions).filter(node => node.tag === 'button').map(text), labels);
      assert.ok(text(byClass(tree, 'orderInfoTip')[0]).includes(title));
      if (status === 2) {
        await tap(button(actions, '催单'));
        assert.match(env.requests.at(-1).url, /\/reminder\/974$/);
        assert.deepEqual(page.popups.at(-1), ['common', 'open', 'center']);
        await tap(button(actions, '取消订单'));
        assert.match(env.requests.at(-1).url, /\/cancel\/974$/);
        assert.equal(env.requests.at(-1).method, 'PUT');
      }
      if (status === 3 || status === 4) {
        const count = env.requests.length; tap(button(actions, '取消订单'));
        assert.equal(env.requests.length, count);
        assert.equal(page.textTip, '请联系商家进行取消！');
      }
    }
  } finally { page.$destroy(); }
});

test('actual detail SFC binds persisted amount/image and expands original detail rows', () => {
  const env = harness('Details'); const page = env.instance(); const render = template('Details');
  try {
    page.loaded = true;
    page.orderDetailsData = { ...order(), orderDetailList: [1, 2, 3].map(id => ({ ...order().orderDetailList[0], id, name: '合成' + id })) };
    let tree = render(page);
    assert.equal(text(byClass(tree, 'totalMoney')[0]).replace(/\s/g, ''), '合计￥19');
    assert.equal(byClass(tree, 'type_item').length, 2);
    assert.equal(nodes(tree).find(node => hasClass(node, 'dish_img_url')).data.attrs.src, order().orderDetailList[0].image);
    const expand = nodes(byClass(tree, 'iconUp')[0]).find(node => node.data && node.data.on && node.data.on.tap);
    tap(expand); tree = render(page);
    assert.equal(byClass(tree, 'type_item').length, 3);
    assert.equal(text(byClass(tree, 'iconUp')[0]), '点击收起');
    tap(expand); assert.equal(byClass(render(page), 'type_item').length, 2);
  } finally { page.$destroy(); }
});
