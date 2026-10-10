const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { compileCheckout } = require('../build.cjs');
const { root, harness } = require('./legacy-harness.cjs');
const repo = path.resolve(root, '..');
function filesUnder(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesUnder(file) : [file];
  });
}
test('two real builds preserve all twelve other pages and shared runtime/vendor/component artifacts', async () => {
  const protectedFiles = filesUnder(path.join(root, 'pages')).filter(file => !file.startsWith(path.join(root, 'pages/order') + path.sep))
    .concat(...['common', 'components', 'uni_modules', 'node-modules'].map(dir => filesUnder(path.join(root, dir))))
    .concat(['app.js', 'app.json', 'app.wxss'].map(file => path.join(root, file)));
  const before = new Map(protectedFiles.map(file => [file, fs.readFileSync(file)]));
  const first = await compileCheckout(); const second = await compileCheckout();
  assert.deepEqual(first, second);
  assert.deepEqual(Object.keys(first).sort(), ['index.js', 'index.json', 'index.wxml', 'index.wxss']);
  for (const [file, bytes] of before) assert.ok(bytes.equals(fs.readFileSync(file)), 'build modified ' + path.relative(root, file));
  const pages = JSON.parse(fs.readFileSync(path.join(root, 'app.json'))).pages;
  assert.equal(pages.filter(page => page !== 'pages/order/index').length, 12);
  for (const page of pages) for (const extension of ['js', 'json', 'wxml', 'wxss']) assert.ok(fs.existsSync(path.join(root, page + '.' + extension)), page + '.' + extension);
});
test('saved template and business source edits change genuinely compiled outputs in isolated project scratch', async () => {
  const scratch = path.join(repo, '.scratch/issue5'); fs.mkdirSync(scratch, { recursive: true });
  const owned = fs.mkdtempSync(path.join(scratch, 'build-edit-'));
  const source = path.join(owned, 'src'); fs.mkdirSync(source);
  for (const file of ['Checkout.vue', 'checkout.js', 'legacy.js']) fs.copyFileSync(path.resolve(__dirname, '../src', file), path.join(source, file));
  const options = { source: path.join(source, 'Checkout.vue'), outDir: path.join(owned, 'output') };
  const original = await compileCheckout(options);
  fs.writeFileSync(options.source, fs.readFileSync(options.source, 'utf8').replace('提交订单', '编译编辑探针'));
  const modifiedTemplate = await compileCheckout(options);
  assert.notEqual(modifiedTemplate['index.wxml'], original['index.wxml']);
  assert.match(modifiedTemplate['index.wxml'], /编译编辑探针/);
  const script = path.join(source, 'checkout.js');
  fs.writeFileSync(script, fs.readFileSync(script, 'utf8').replace('this.orderDishPrice += 6 +', 'this.orderDishPrice += 7 +'));
  const modifiedScript = await compileCheckout(options);
  assert.notEqual(modifiedScript['index.js'], modifiedTemplate['index.js']);
  // Execute the edited bundle with actual old Vue/store, using synthetic IO, to prove changed behavior.
  const env = harness(); env.context.module = { exports: {} };
  vm.runInContext(modifiedScript['index.js'], env.context);
  const page = env.instance(env.context.module.exports.default);
  page.computOrderInfo(); assert.equal(page.orderDishPrice, 7); page.$destroy();
  // Retain this small owned probe under .scratch for review; never overwrite the task source.
});
test('generated JavaScript syntax, template events/refs and component/static references resolve', async () => {
  const output = await compileCheckout(); new vm.Script(output['index.js']);
  const config = JSON.parse(output['index.json']); const wxml = output['index.wxml'];
  const env = harness();
  for (const target of Object.values(config.usingComponents)) {
    for (const extension of ['js', 'json', 'wxml', 'wxss']) assert.ok(fs.existsSync(path.join(root, target.slice(1) + '.' + extension)), target + '.' + extension);
  }
  for (const component of Object.keys(env.options.components)) {
    const loaded = await env.options.components[component](); assert.equal(typeof loaded.default, 'object');
  }
  for (const target of wxml.matchAll(/src="([^"{]+)"/g)) assert.ok(fs.existsSync(path.resolve(root, 'pages/order', target[1])), 'static ' + target[1]);
  assert.match(wxml, /data-ref="popup"/); assert.match(wxml, /data-ref="timePopup"/); assert.match(wxml, /data-ref="piker"/);
  assert.match(wxml, /bind:__l="__l"/); assert.match(wxml, /slot="footer"/);
  const page = env.instance(); env.options.render.call(page);
  for (const attr of wxml.matchAll(/data-event-opts="{{(.*?)}}"/g)) {
    const events = vm.runInNewContext('(' + attr[1] + ')', { index: 0, i: 0 });
    for (const [, handlers] of events) for (const [name] of handlers) assert.equal(typeof page[name], 'function', 'unresolved event ' + name);
  }
  page.$destroy();
});
