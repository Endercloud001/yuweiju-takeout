const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const plain = value => JSON.parse(JSON.stringify(value));
// Executes repository artifacts; only WeChat IO and webpack chunk loading are test doubles.
function harness({ token = 'synthetic-test-token', respond = () => ({ code: 1, data: [] }) } = {}) {
  const modules = {}, cache = {}, requests = [], redirects = [], toasts = [], registrations = [];
  const storage = new Map([['token', token], ['baseUserInfo', { nickName: '合成用户', gender: '1' }]]);
  const wx = {
    getSystemInfoSync: () => ({ platform: 'devtools', language: 'zh_CN', windowWidth: 375, windowHeight: 800, pixelRatio: 2 }),
    getSystemInfo: options => options.success(wx.getSystemInfoSync()),
    canIUse: () => true,
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    setStorage: options => { storage.set(options.key, options.data); options.success && options.success({ errMsg: 'setStorage:ok' }); },
    getStorage: () => {},
    request(options) {
      requests.push(plain({ url: options.url, data: options.data, header: options.header, method: options.method }));
      const answer = respond(options);
      if (answer instanceof Error) options.fail({ data: answer.message });
      else if (answer && answer.pending) answer.pending(options);
      else options.success({ data: answer, statusCode: 200 });
    },
    redirectTo: options => { redirects.push(options.url); options.success && options.success({ errMsg: 'redirectTo:ok' }); },
    showToast: options => toasts.push(options.title),
    showModal: options => options.success && options.success({ confirm: true }),
    getMenuButtonBoundingClientRect: () => ({ height: 32 }),
    login: options => options.success({ errMsg: 'login:ok', code: 'synthetic-login-code' }),
    getUserProfile: options => options.success({ userInfo: { nickName: '合成登录', avatarUrl: '', gender: 1 } })
  };
  const sandbox = { wx, console: { log() {}, warn() {}, error() {} }, setTimeout, clearTimeout, setInterval, clearInterval,
    Page: options => options, App: options => options,
    Component: options => { registrations.push(options); return options; }, Behavior: options => options,
    getApp: () => ({ $vm: {} }), getCurrentPages: () => [], module: { exports: {} } };
  sandbox.global = sandbox;
  sandbox.global.webpackJsonp = { push(chunk) { Object.assign(modules, chunk[1]); } };
  const context = vm.createContext(sandbox);
  function capture(relative) { vm.runInContext(fs.readFileSync(path.join(root, relative), 'utf8'), context, { filename: relative }); }
  function load(id) {
    if (cache[id]) return cache[id].exports;
    if (!modules[id]) throw new Error('Missing actual webpack module ' + id);
    const module = { exports: {} }; cache[id] = module;
    modules[id].call(module.exports, module, module.exports, load);
    return module.exports;
  }
  load.d = (exports, key, getter) => Object.defineProperty(exports, key, { enumerable: true, get: getter });
  load.r = exports => Object.defineProperty(exports, '__esModule', { value: true });
  load.o = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
  load.n = module => { const getter = module && module.__esModule ? () => module.default : () => module; load.d(getter, 'a', getter); return getter; };
  load.e = chunk => { capture(chunk + '.js'); return Promise.resolve(); };
  load.oe = error => { throw error; };
  capture('common/vendor.js');
  const store = load(12).default;
  // App setup normally supplies this same prototype store, without restoring App source here.
  const Vue = load(4).default; Vue.prototype.$store = store;
  wx.__webpack_require_UNI_MP_PLUGIN__ = load;
  capture('pages/order/index.js');
  const options = context.module.exports.default;
  function instance(optionsToUse = options) {
    // Do not mount or claim an official WeChat lifecycle/simulator run.
    const instance = new Vue({ ...optionsToUse, store });
    instance.$mp = { data: {} };
    instance.$refs = {
      popup: { open(type) { instance.popupMode = type; }, close() { instance.popupMode = ''; } },
      timePopup: { open(type) { instance.timeMode = type; }, close() { instance.timeMode = ''; } },
      piker: { defaultValue: [0] }
    };
    return instance;
  }
  return { context, load, capture, store, Vue, wx, requests, redirects, toasts, registrations, options, instance, storage };
}
module.exports = { harness, plain, root };
