const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { build, compilePage, pages } = require('../build.cjs');
const { root, harness, order } = require('./runtime.cjs');
const repo = path.resolve(root, '..');
function filesUnder(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? filesUnder(path.join(dir, entry.name)) : [path.join(dir, entry.name)]);
}
test('repeat real builds are deterministic and leave all other11 pages/shared assets untouched', async () => {
  const targets = Object.values(pages).map(page => path.join(root, path.dirname(page.target)) + path.sep);
  const protectedFiles = filesUnder(path.join(root, 'pages')).filter(file => !targets.some(target => file.startsWith(target)))
    .concat(...['common', 'components', 'uni_modules', 'node-modules'].map(dir => filesUnder(path.join(root, dir))))
    .concat(['app.js', 'app.json', 'app.wxss'].map(file => path.join(root, file)));
  const before = new Map(protectedFiles.map(file => [file, fs.readFileSync(file)]));
  const first = await build(); const second = await build(); assert.deepEqual(first, second);
  for (const [file, bytes] of before) assert.ok(bytes.equals(fs.readFileSync(file)), 'changed ' + path.relative(root, file));
  const registered = JSON.parse(fs.readFileSync(path.join(root, 'app.json'))).pages;
  assert.equal(registered.filter(page => !Object.values(pages).some(target => target.target === page)).length, 11);
  for (const page of registered) for (const ext of ['js', 'json', 'wxml', 'wxss']) assert.ok(fs.existsSync(path.join(root, page + '.' + ext)));
  for (const [name, files] of Object.entries(first)) assert.deepEqual(Object.keys(files).sort(), ['js', 'json', 'wxml', 'wxss'].map(ext => path.basename(pages[name].target) + '.' + ext).sort());
});
test('editing both saved SFCs changes compiled template and real generated page behavior in issue6 scratch', async () => {
  const scratch = path.join(repo, '.scratch/issue6'); fs.mkdirSync(scratch, { recursive: true });
  const owned = fs.mkdtempSync(path.join(scratch, 'source-edit-'));
  for (const name of Object.keys(pages)) {
    // Keep relative imports pointing at the real local adapters; only the SFC is varied.
    const source = path.join(owned, name + '.vue');
    const original = fs.readFileSync(path.resolve(__dirname, '../src', name + '.vue'), 'utf8');
    fs.writeFileSync(source, original.replace('"./' + (name === 'Details' ? 'details' : 'history') + '.js"', JSON.stringify(path.resolve(__dirname, '../src', name === 'Details' ? 'details.js' : 'history.js'))));
    const config = { source, outDir: path.join(owned, name) }; const first = await compilePage(name, config);
    fs.writeFileSync(source, fs.readFileSync(source, 'utf8').replace(pages[name].title, '源码编辑探针').replace('export default page;', 'export default { ...page, data() { return { ...page.data(), textTip: "合成编辑" }; } };'));
    const changed = await compilePage(name, config); const stem = path.basename(pages[name].target);
    assert.notEqual(first[stem + '.wxml'], changed[stem + '.wxml']); assert.match(changed[stem + '.wxml'], /源码编辑探针/);
    assert.notEqual(first[stem + '.js'], changed[stem + '.js']);
    const env = harness(name); env.context.module = { exports: {} }; vm.runInContext(changed[stem + '.js'], env.context);
    const page = env.instance(env.context.module.exports.default); // See override argument supported below.
    assert.equal(page.textTip, '合成编辑'); page.$destroy();
  }
});
test('compiled templates resolve real components/events/refs/static assets and stored fees/images', async () => {
  const built = await build();
  for (const name of Object.keys(pages)) {
    const stem = path.basename(pages[name].target); const output = built[name]; new vm.Script(output[stem + '.js']);
    const env = harness(name, { respond: () => ({ code: 1, data: order() }) }); const page = env.instance();
    const config = JSON.parse(output[stem + '.json']); const template = output[stem + '.wxml'];
    for (const target of Object.values(config.usingComponents)) for (const ext of ['js', 'json', 'wxml', 'wxss']) assert.ok(fs.existsSync(path.join(root, target.slice(1) + '.' + ext)));
    for (const component of Object.values(env.options.components)) { const loaded = await component(); assert.equal(typeof loaded.default, 'object'); }
    for (const target of template.matchAll(/src="([^"{]+)"/g)) assert.ok(fs.existsSync(path.resolve(root, path.dirname(pages[name].target), target[1])));
    assert.match(template, /data-ref="commonPopup"/); assert.match(template, /bind:__l="__l"/);
    page.$options.render.call(page);
    for (const event of template.matchAll(/data-event-opts="{{(.*?)}}"/g)) {
      const events = vm.runInNewContext('(' + event[1] + ')', { index: 0, tabNumber: 0, y: 0 });
      for (const [, handlers] of events) for (const [method] of handlers) assert.equal(typeof page[method], 'function', method);
    }
    if (name === 'Details') {
      await page.getBaseData(974); page.$options.render.call(page);
      assert.match(template, /以订单合计为准/); assert.match(template, /orderDetailsData.amount/); assert.match(template, /orderDetailsData.packAmount/);
      assert.doesNotMatch(template, /配送费.*￥[26]/); assert.match(template, /obj.\$orig.image/);
    } else { assert.match(template, /num.image/); assert.match(template, /<empty/); }
    page.$destroy();
  }
});
