const fs = require('node:fs');
const path = require('node:path');
const compiler = require('@dcloudio/uni-template-compiler');
const stripWith = require('vue-template-es2015-compiler');
const esbuild = require('esbuild');
const pages = {
  HistoryOrder: { target: 'pages/historyOrder/historyOrder', scope: '5cf07246', title: '历史订单',
    components: { 'uni-nav-bar': '/components/uni-nav-bar/uni-nav-bar', 'uni-popup': '/uni_modules/uni-popup/components/uni-popup/uni-popup', empty: '/components/empty/empty' } },
  Details: { target: 'pages/details/index', scope: '54d3589c', title: '订单详情',
    components: { 'uni-nav-bar': '/components/uni-nav-bar/uni-nav-bar', 'uni-popup': '/uni_modules/uni-popup/components/uni-popup/uni-popup', 'uni-list-item': '/node-modules/@dcloudio/uni-ui/lib/uni-list-item/uni-list-item' } }
};
async function compilePage(name, { source = path.join(__dirname, 'src', name + '.vue'), outDir } = {}) {
  const config = pages[name]; if (!config) throw new Error('Unknown history page');
  const sfc = compiler.parseComponent(fs.readFileSync(source, 'utf8'));
  if (!sfc.template || !sfc.script || !sfc.styles.length) throw new Error('Page requires template/script/style');
  const result = compiler.compile(sfc.template.content, { resourcePath: config.target + '.wxml', scopeId: 'data-v-' + config.scope, mp: { platform: 'mp-weixin' } });
  if (result.errors.length || Object.keys(result.files).length) throw new Error(JSON.stringify(result.errors));
  const renderCode = stripWith(`var render = function() {${result.render}}; var staticRenderFns = [];`);
  const compiled = await esbuild.build({ stdin: { contents: sfc.script.content, resolveDir: path.dirname(source), sourcefile: source, loader: 'js' },
    absWorkingDir: __dirname, bundle: true, write: false, platform: 'neutral', format: 'cjs', target: 'es2015',
    banner: { js: `// Generated from history-source/src/${name}.vue. Run npm run build in history-source.` } });
  const entry = `${compiled.outputFiles[0].text}\n${renderCode}\nrender._withStripped = true;\n` +
    `var load = wx.__webpack_require_UNI_MP_PLUGIN__;\n` +
    `var page = load(11).default(module.exports.default, render, staticRenderFns, false, null, '${config.scope}', null, false).exports;\n` +
    `load(1).createPage(page);\n`;
  const pageConfig = { navigationStyle: 'custom', navigationBarTitleText: config.title, enablePullDownRefresh: false,
    navigationBarBackgroundColor: '#333333', usingComponents: config.components };
  const stem = path.basename(config.target);
  const files = { [stem + '.js']: entry, [stem + '.wxml']: result.template + '\n',
    [stem + '.wxss']: sfc.styles.map(style => style.content.replace(/^\r?\n/, '').replace(/\r?\n$/, '')).join('\n'),
    [stem + '.json']: JSON.stringify(pageConfig, null, 2) + '\n' };
  const output = outDir || path.resolve(__dirname, '..', path.dirname(config.target));
  fs.mkdirSync(output, { recursive: true });
  // Explicit four-file write set per page, no runtime/shared/vendor outputs.
  for (const [file, text] of Object.entries(files)) fs.writeFileSync(path.join(output, file), text);
  return files;
}
async function build() { const output = {}; for (const name of Object.keys(pages)) output[name] = await compilePage(name); return output; }
module.exports = { compilePage, build, pages };
if (require.main === module) {
  if (process.argv.length > 2) throw new Error('Build takes no arguments');
  build().then(() => console.log('Compiled HistoryOrder.vue and Details.vue (eight page files only)')).catch(error => { console.error(error); process.exitCode = 1; });
}
