// Reuse the merged checkout runtime bridge and authenticated API; no vendor copy.
import { legacyDependencies } from '../../checkout-source/src/legacy.js';
export function dependencies() {
  const deps = legacyDependencies();
  const load = wx.__webpack_require_UNI_MP_PLUGIN__;
  return { ...deps, components: {
    uniNavBar: deps.components.uniNavBar,
    uniPopup: deps.components.uniPopup,
    Empty: () => load.e('components/empty/empty').then(() => load(194))
  } };
}
export function accepted(res) {
  // Legacy request accepts code200 too; pages must retain strict business code1.
  if (!res || res.code !== 1) throw res || new Error('请求失败');
  return res.data;
}
export function failure(uni, error) {
  uni.showToast({ title: error && (error.msg || error.message || (error.data && error.data.msg)) || '加载失败，请重试', icon: 'none' });
}
