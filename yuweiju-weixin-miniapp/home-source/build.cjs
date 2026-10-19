const fs = require('node:fs');
const path = require('node:path');
const esbuild = require('esbuild');
async function build({ sourceDir = path.join(__dirname, 'src'), outDir = path.resolve(__dirname, '../pages/index') } = {}) {
  const bundled = await esbuild.build({ entryPoints: [path.join(sourceDir, 'home.js')], bundle: true, write: false, platform: 'neutral', format: 'cjs', target: 'es2015' });
  const render = fs.readFileSync(path.join(sourceDir, 'render.js'), 'utf8');
  const js = `// Generated from home-source native template/render and cart adapter. Run npm run build.\n` +
    `(global.webpackJsonp = global.webpackJsonp || []).push([["pages/index/index"], {\n` +
    `15: function(module, exports, load) { load(5); wx.__webpack_require_UNI_MP_PLUGIN__ = load; load(1).createPage(load(16).default); },\n` +
    `16: function(module, exports, load) {\n${bundled.outputFiles[0].text}\n` +
    `var options = module.exports.createHome(load);\n${render}\nrender._withStripped = true;\n` +
    `module.exports = { default: load(11).default(options, render, [], false, null, '57280228', null, false).exports };\n` +
    `}\n}, [[15,"common/runtime","common/vendor"]]]);\n`;
  const files = { 'index.js': js };
  for (const ext of ['wxml', 'wxss', 'json']) files['index.' + ext] = fs.readFileSync(path.join(sourceDir, 'index.' + ext), 'utf8');
  fs.mkdirSync(outDir, { recursive: true });
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(outDir, name), content);
  return files;
}
module.exports = { build };
if (require.main === module) build().then(() => console.log('Built homepage native template/render and cart adapter (four page files only)')).catch(error => { console.error(error); process.exitCode = 1; });
