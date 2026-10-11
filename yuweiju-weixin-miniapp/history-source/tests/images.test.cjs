const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const compiler = require('vue-template-compiler');
const vm = require('node:vm');
const { harness, order, plain, root } = require('./runtime.cjs');
const fallback = '/static/history-dish-placeholder.png';
const historical = 'https://example.invalid/historical-A.png';
function renderer(name) {
  const sfc = compiler.parseComponent(fs.readFileSync(path.resolve(__dirname, '../src', name + '.vue'), 'utf8'));
  const compiled = compiler.compile(sfc.template.content);
  assert.deepEqual(compiled.errors, []);
  const render = new Function(compiled.render);
  return page => render.call(page);
}
function nodes(tree) { return !tree ? [] : Array.isArray(tree) ? tree.flatMap(nodes) : [tree, ...nodes(tree.children)]; }
function images(tree) { return nodes(tree).filter(node => node.tag === 'image' && node.data.attrs['data-history-src'] !== undefined); }
function error(node) {
  assert.equal(typeof node.data.on.error, 'function');
  node.data.on.error({ type: 'error', currentTarget: { dataset: { historySrc: node.data.attrs['data-history-src'], historyRow: node.data.attrs['data-history-row'] } } });
}
for (const name of ['HistoryOrder', 'Details']) {
  test(`${name} actual saved template: null/empty/blank/local fallback and historical A, error/recovery/row isolation`, async () => {
    const data = { ...order(), orderDetailList: [null, '', ' \t\n', historical, historical].map((image, i) => ({ id: i+1, name: '历史'+i, image, number: 1, amount: 3 })) };
    const before = JSON.stringify(data);
    const env = harness(name, { respond: () => ({ code: 1, data: name === 'Details' ? data : { records: [data], total: 1 } }) });
    const page = env.instance(); const render = renderer(name);
    try {
      await page.$options.onLoad[0].call(page, { orderId: '974' });
      if (name === 'Details') page.showDisplay = true;
      let displayed = images(render(page));
      assert.deepEqual(displayed.slice(0, 5).map(node => node.data.attrs.src), [fallback, fallback, fallback, historical, historical]);
      const failedNode = displayed[3]; error(failedNode);
      displayed = images(render(page));
      assert.equal(displayed[3].data.attrs.src, fallback);
      assert.equal(displayed[4].data.attrs.src, historical, 'same URL other row stays historical');
      error(displayed[3]);
      assert.equal(images(render(page))[3].data.attrs.src, fallback, 'fallback error never retries historical URL');
      assert.equal(JSON.stringify(data), before, 'display errors never mutate API rows');
      if (name === 'Details') assert.deepEqual(plain(env.store.state.orderListData), plain(data.orderDetailList));
      const rows = name === 'Details' ? page.orderDetailsData.orderDetailList : page.recentOrdersList[0].orderDetailList;
      rows[3].image = historical + '?new-source';
      error(failedNode); // Late event carries the OLD rendered source in currentTarget.dataset.
      assert.equal(images(render(page))[3].data.attrs.src, historical + '?new-source');
      rows[3].image = historical;
      assert.equal(images(render(page))[3].data.attrs.src, historical, 'returning to a previous source retries');
      rows.splice(3, 1, { ...rows[3], image: historical });
      error(failedNode);
      assert.equal(images(render(page))[3].data.attrs.src, historical, 'replacement row with same ID/URL retries');
      error(images(render(page))[3]);
      assert.equal(images(render(page))[3].data.attrs.src, fallback);
      if (name === 'Details') await page.getBaseData(974); else await page.reload();
      if (name === 'Details') page.showDisplay = true;
      assert.equal(images(render(page))[3].data.attrs.src, historical, 'refresh clears display failures');
      assert.equal(JSON.stringify(data), before, 'only explicit test source/row replacement occurred');
      if (name === 'Details') assert.deepEqual(plain(env.store.state.orderListData), plain(data.orderDetailList));
      // Execute actual generated uni render too: it must bind the same loadable fallback.
      page.$options.render.call(page);
      assert.ok(JSON.stringify(plain(page.$mp.data)).includes(fallback));
    } finally { page.$destroy(); }
  });
  test(`${name} generated WeChat binderror uses actual legacy event dispatcher and isolates replacement rows`, async () => {
    const data = { ...order(), orderDetailList: [1,2].map(id => ({ id, name: '历史'+id, image: historical, number: 1, amount: 9 })) };
    const env = harness(name, { respond: () => ({ code: 1, data: name === 'Details' ? data : { records: [data], total: 1 } }) });
    const page = env.instance(); const render = renderer(name);
    try {
      await page.$options.onLoad[0].call(page, { orderId: '974' });
      const target = name === 'Details' ? 'pages/details/index.wxml' : 'pages/historyOrder/historyOrder.wxml';
      const wxml = fs.readFileSync(path.join(root, target), 'utf8');
      const imageTag = wxml.match(/<image\b[^>]*data-history-src="[^>]*>/)[0];
      assert.match(imageTag, /binderror="__e"/);
      const binding = imageTag.match(/data-event-opts="{{(.*?)}}"/)[1];
      const eventOpts = vm.runInNewContext('(' + binding + ')', { index: 0, y: 0, tabNumber: 0 });
      const displayed = images(render(page))[0];
      const dataset = { eventOpts, historySrc: displayed.data.attrs.src, historyRow: displayed.data.attrs['data-history-row'] };
      const dispatch = env.registrations.at(-1).methods.__e;
      dispatch.call({ $vm: page }, { type: 'error', currentTarget: { dataset } });
      assert.deepEqual(images(render(page)).slice(0,2).map(node => node.data.attrs.src), [fallback, historical]);
      const rows = name === 'Details' ? page.orderDetailsData.orderDetailList : page.recentOrdersList[0].orderDetailList;
      rows.splice(0,1,{ ...rows[0] });
      dispatch.call({ $vm: page }, { type: 'error', currentTarget: { dataset } });
      assert.equal(images(render(page))[0].data.attrs.src, historical, 'old indexed event cannot fail replacement');
      page.$options.render.call(page);
    } finally { page.$destroy(); }
  });
}
test('shared local fallback is a tracked portable 8-bit PNG with nonempty decoded pixels', () => {
  const png = fs.readFileSync(path.join(root, fallback));
  assert.equal(png.subarray(0,8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(png.readUInt32BE(16), 172); assert.equal(png.readUInt32BE(20), 172);
  assert.equal(png[24], 8);
  const zlib = require('node:zlib'); let pos = 8; const parts = [];
  while (pos < png.length) { const len = png.readUInt32BE(pos); if (png.toString('ascii', pos+4, pos+8) === 'IDAT') parts.push(png.subarray(pos+8, pos+8+len)); pos += 12 + len; }
  assert.ok(zlib.inflateSync(Buffer.concat(parts)).length > 172*172);
});
