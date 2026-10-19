# Homepage cart source (#59)

`npm ci --no-audit --no-fund && npm test && npm run build`.

The original SFC is absent. This minimal native source retains the actual WeChat
`index.wxml`, paired uni `$root` render, CSS and page config. The build bundles
`src/home.js` into page module 16 and writes only the four `pages/index/index.*`
files. It reuses legacy module 20's login/catalog/menu/flavor behavior, module 12's
Vuex store, module 24's authenticated API and the existing uni runtime. No shared
vendor output is rewritten. Native template edits and render bindings must stay
paired. Tests execute both the actual native template/event dispatcher and legacy
Vue/store/API. This is not an official WeChat compiler or interaction acceptance.
