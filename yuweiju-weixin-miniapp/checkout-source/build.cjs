const fs = require('node:fs');
const path = require('node:path');
const compiler = require('@dcloudio/uni-template-compiler');
const stripWith = require('vue-template-es2015-compiler');
const esbuild = require('esbuild');
const sourceDir = path.join(__dirname, 'src');
const pageConfig = {
  navigationStyle: 'custom', navigationBarTitleText: '提交订单', enablePullDownRefresh: false,
  navigationBarBackgroundColor: '#333333',
  usingComponents: {
    'uni-nav-bar': '/components/uni-nav-bar/uni-nav-bar',
    'uni-list': '/uni_modules/uni-list/components/uni-list/uni-list',
    'uni-list-item': '/node-modules/@dcloudio/uni-ui/lib/uni-list-item/uni-list-item',
    'uni-popup': '/uni_modules/uni-popup/components/uni-popup/uni-popup',
    pikers: '/components/uni-piker/index'
  }
};
async function compileCheckout({ source = path.join(sourceDir, 'Checkout.vue'), outDir = path.resolve(__dirname, '../pages/order') } = {}) {
  const sfc = compiler.parseComponent(fs.readFileSync(source, 'utf8'));
  if (!sfc.template || !sfc.script || !sfc.styles.length) throw new Error('Checkout requires template, script and style');
  const result = compiler.compile(sfc.template.content, {
    resourcePath: 'pages/order/index.wxml', scopeId: 'data-v-0ca91b30', mp: { platform: 'mp-weixin' }
  });
  if (result.errors.length) throw new Error(JSON.stringify(result.errors));
  if (Object.keys(result.files).length) throw new Error('Checkout unexpectedly needs extra template outputs');
  const renderCode = stripWith(`var render = function() {${result.render}}; var staticRenderFns = [];`);
  const compiled = await esbuild.build({
    stdin: { contents: sfc.script.content, resolveDir: path.dirname(source), sourcefile: source, loader: 'js' },
    absWorkingDir: __dirname, bundle: true, write: false, platform: 'neutral', format: 'cjs', target: 'es2015',
    banner: { js: '// Generated from checkout-source/src/Checkout.vue. Run npm run build in checkout-source.' }
  });
  const entry = `${compiled.outputFiles[0].text}\n${renderCode}\nrender._withStripped = true;\n` +
    `var load = wx.__webpack_require_UNI_MP_PLUGIN__;\n` +
    `var page = load(11).default(module.exports.default, render, staticRenderFns, false, null, '0ca91b30', null, false).exports;\n` +
    `load(1).createPage(page);\n`;
  // No runtime/vendor/App output: this explicit four-file list is the complete build write set.
  const files = { 'index.js': entry, 'index.wxml': result.template + '\n',
    'index.wxss': sfc.styles.map(style => style.content.replace(/^\r?\n/, '').replace(/\r?\n$/, '')).join('\n'),
    'index.json': JSON.stringify(pageConfig, null, 2) + '\n' };
  fs.mkdirSync(outDir, { recursive: true });
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(outDir, name), content);
  return files;
}
module.exports = { compileCheckout };
if (require.main === module) {
  if (process.argv.length > 2) throw new Error('Build takes no arguments; use the documented default checkout output');
  compileCheckout().then(files => console.log(`Compiled Checkout.vue: ${Object.keys(files).join(', ')}`)).catch(error => { console.error(error); process.exitCode = 1; });
}
