// main.js already exposes this loader; keep the same authenticated store and API.
export function legacyDependencies() {
  const load = wx.__webpack_require_UNI_MP_PLUGIN__;
  if (typeof load !== 'function') throw new Error('Checkout requires the existing uni-app main runtime');
  return {
    store: load(12).default,
    api: load(24),
    uni: load(1).default,
    tools: load(29),
    components: {
      uniNavBar: () => load.e('components/uni-nav-bar/uni-nav-bar').then(() => load(145)),
      uniList: () => load.e('uni_modules/uni-list/components/uni-list/uni-list').then(() => load(152)),
      uniListItem: () => load.e('node-modules/@dcloudio/uni-ui/lib/uni-list-item/uni-list-item').then(() => load(173)),
      uniPopup: () => load.e('uni_modules/uni-popup/components/uni-popup/uni-popup').then(() => load(166)),
      pikers: () => load.e('components/uni-piker/index').then(() => load(180))
    }
  };
}
