const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const compiler = require('vue-template-compiler');
const { harness, plain, root } = require('../../checkout-source/tests/legacy-harness.cjs');
const { build } = require('../build.cjs');
// Keep test-before-build invocations on the saved source, as checkout-source does.
test.before(async () => { await build(); });
const flush = () => new Promise(resolve => setImmediate(resolve));
// Render the actual native WXML as Vue VNodes: only platform loop/condition/attribute
// syntax is translated. Keep original event-opts and dispatch through uni's real __e.
// shortcut: translates this page's native directive subset; extend when the page adds new directives.
function nativeTemplate(wxml) {
  const converted = wxml.replace(/<[^/!](?:"[^"]*"|'[^']*'|[^'">])*>/g, tag => {
    const loop = tag.match(/wx:for="{{(.*?)}}"/);
    if (loop) {
      const item = tag.match(/wx:for-item="(.*?)"/)?.[1] || 'item';
      const index = tag.match(/wx:for-index="(.*?)"/)?.[1] || 'index';
      tag = tag.replace(/wx:for="{{.*?}}"/, `v-for="(${item}, ${index}) in ${loop[1]}"`).replace(/wx:for-(item|index)=".*?"/g, '').replace(/wx:key=".*?"/g, '');
    }
    return tag.replace(/wx:if=/g, 'v-if=').replace(/wx:else/g, 'v-else').replace(/([\w:-]+)="{{(.*?)}}"/g, (_, attr, expr) => `${attr.startsWith('v-') ? attr : ':' + attr}="${expr.replace(/"/g, '&quot;')}"`);
  });
  const compiled = compiler.compile(converted);
  assert.deepEqual(compiled.errors, []);
  return new Function(compiled.render);
}
function nodes(tree) {
  if (!tree) return [];
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  if (tree.data?.attrs?.hidden) return [];
  return [tree, ...nodes(tree.children)];
}
const text = tree => nodes(tree).map(node => node.text || '').join('');
const byClass = (tree, name) => nodes(tree).filter(node => String(node.data?.staticClass || '').split(' ').includes(name));
function tap(env, page, node) {
  assert.ok(node, 'native template target exists');
  const opts = node.data.attrs['data-event-opts'];
  assert.ok(opts, 'actual template event binding');
  return env.registrations.at(-1).methods.__e.call({ $vm: page }, { type: 'tap', currentTarget: { dataset: { eventOpts: opts } }, stopPropagation() {} });
}
function home(options) {
  const env = harness(options);
  env.wx.navigateTo = options => { env.redirects.push(options.url); options.success?.({ errMsg: 'navigateTo:ok' }); };
  env.capture('pages/index/index.js');
  const page = env.instance(env.load(16).default);
  // WXML property lookup is null-safe; Vue render needs neutral hidden-popover data.
  page.moreNormDishdata = {}; page.moreNormdata = []; page.dishDetailes = { type: 1, price: 0, flavors: [] };
  const render = nativeTemplate(fs.readFileSync(path.join(root, 'pages/index/index.wxml'), 'utf8'));
  function tree() { page.$options.render.call(page); Object.assign(page, page.$mp.data); return render.call(page); }
  return { env, page, tree };
}
const A = { id: 1, dishId: 11, name: '历史A', image: '/static/imgDefault.png', dishFlavor: '微辣', number: 2, amount: 18, available: true };
const B = { id: 2, setmealId: 12, name: '历史B', image: '/static/imgDefault.png', dishFlavor: '', number: 2, amount: null, available: false, unavailableReason: '商品已删除' };
for (const meal of [false, true]) for (const reason of ['商品已停售', '商品已删除', '商品或订单金额无效或超出范围']) test(`native cart ${meal ? 'setmeal' : 'dish'} ${reason}: retained rows, no trusted subtotal, event-bound owned removal then checkout`, async () => {
  let cart = [plain(A), { ...plain(B), setmealId: meal ? 12 : null, dishId: meal ? null : 12, dishFlavor: meal ? '' : '重辣', unavailableReason: reason }];
  const { env, page, tree } = home({ respond: options => {
    if (options.url.endsWith('/shoppingCart/sub')) {
      assert.equal(options.data[meal ? 'setmealId' : 'dishId'], 12); assert.equal(options.data.dishFlavor, meal ? '' : '重辣');
      if (--cart[1].number === 0) cart.pop();
    }
    return { code: 1, data: plain(cart) };
  } });
  try {
    await page.getTableOrderDishListes(); page.openOrderCartList = true;
    let rendered = tree();
    assert.equal(byClass(rendered, 'cart-invalid-reason').length, 1);
    assert.match(text(rendered), new RegExp(reason));
    assert.match(text(rendered), /历史A/); assert.match(text(rendered), /历史B/);
    assert.match(text(byClass(rendered, 'order_price')), /待恢复/);
    assert.doesNotMatch(text(byClass(rendered, 'order_price')), /36|0\.00/);
    assert.equal(byClass(rendered, 'dish_number').at(-1).children[0].text, '2');
    assert.equal(byClass(rendered, 'clear').length, 0);
    assert.equal(byClass(rendered, 'dish_add').length, 1, 'invalid B cannot be incremented');
    tap(env, page, byClass(rendered, 'order_but').at(-1)); await flush();
    assert.deepEqual(env.redirects, []); assert.equal(cart.length, 2);
    for (let remaining = 1; remaining >= 0; remaining--) {
      tap(env, page, byClass(tree(), 'dish_red').at(-1)); await flush();
      assert.equal(cart.length, remaining ? 2 : 1);
      if (remaining) assert.equal(env.store.state.orderListData[1].number, 1);
    }
    assert.deepEqual(plain(env.store.state.orderListData), [A]);
    assert.match(text(byClass(tree(), 'order_price')), /36\.00/);
    tap(env, page, byClass(tree(), 'order_but').at(-1)); await flush();
    assert.deepEqual(env.redirects, ['/pages/order/index']);
    assert.equal(env.requests.filter(r => r.method === 'POST').length, 2);
    assert.ok(env.requests.every(r => r.header.authentication === 'synthetic-test-token'));
    assert.ok(!env.requests.some(r => /clean|submit/.test(r.url)));
  } finally { page.$destroy(); }
});
test('native network failure retains cart but hides stale subtotal/blocks navigation; empty retry stays empty', async () => {
  let answer = { code: 1, data: [A] };
  const { env, page, tree } = home({ respond: () => answer });
  try {
    await page.getTableOrderDishListes(); answer = new Error('offline');
    await page.getTableOrderDishListes();
    assert.deepEqual(plain(env.store.state.orderListData), [A]);
    assert.match(text(byClass(tree(), 'order_price')), /待恢复/);
    tap(env, page, byClass(tree(), 'order_but').at(-1)); await flush();
    assert.deepEqual(env.redirects, []); assert.ok(env.toasts.length);
    answer = { code: 1, data: [] }; await page.getTableOrderDishListes();
    assert.match(text(byClass(tree(), 'order_price')), /0/);
    assert.deepEqual(plain(env.store.state.orderListData), []);
  } finally { page.$destroy(); }
});
test('homepage build is deterministic, edits affect exact generated page; shared runtime remains byte-identical', async () => {
  const shared = ['common/vendor.js','common/runtime.js','common/main.js','app.js','app.json'].map(name => [name, fs.readFileSync(path.join(root,name))]);
  const first = await build(); assert.deepEqual(await build(), first);
  for (const [name, bytes] of shared) assert.ok(bytes.equals(fs.readFileSync(path.join(root,name))));
  const scratch = path.resolve(root, '../.scratch/issue59'); fs.mkdirSync(scratch,{recursive:true});
  const dir = fs.mkdtempSync(path.join(scratch,'home-edit-')); fs.cpSync(path.resolve(__dirname,'../src'),path.join(dir,'src'),{recursive:true});
  const source = path.join(dir,'src/index.wxml'); fs.writeFileSync(source,fs.readFileSync(source,'utf8').replace('待恢复','等待移除探针'));
  const edited = await build({sourceDir:path.join(dir,'src'),outDir:path.join(dir,'out')});
  assert.notEqual(edited['index.wxml'],first['index.wxml']);
  assert.match(edited['index.wxml'],/等待移除探针/);
});

test('pre-fix legacy module reproduces partial-looking subtotal and unguarded checkout (regression control)', () => {
  const env = harness(); env.wx.navigateTo = options => env.redirects.push(options.url);
  const page = env.instance(env.load(20).default);
  try {
    env.store.commit('initdishListMut',[plain(A),plain(B)]); page.computOrderInfo();
    assert.equal(page.orderDishPrice,36,'old null coercion silently excludes invalid B');
    page.goOrder(); assert.deepEqual(env.redirects,['/pages/order/index'],'old consumer allowed checkout');
  } finally { page.$destroy(); }
});

test('homepage native catalog/menu/specification bindings retain authenticated legacy add and navigation', async () => {
  const categories = [{id:101,type:1,name:'菜品'},{id:102,type:2,name:'套餐'}];
  const flavored = {id:11,name:'口味菜品',image:'/static/imgDefault.png',price:18,description:'旧目录',flavors:[{name:'辣度',value:'["微辣","重辣"]'}]};
  const meal = {id:12,name:'目录套餐',image:'/static/imgDefault.png',price:9,flavors:[]};
  let cart = [];
  const { env,page,tree } = home({respond:options=> {
    if(options.url.endsWith('/category/list')) return {code:1,data:categories};
    if(options.url.endsWith('/dish/list')) return {code:1,data:[flavored]};
    if(options.url.endsWith('/setmeal/list')) return {code:1,data:[meal]};
    if(options.url.endsWith('/shoppingCart/add')) {
      assert.equal(options.data.dishId,11);assert.equal(options.data.dishFlavor,'微辣');
      cart=[plain(A)];
    }
    return {code:1,data:plain(cart)};
  }});
  try {
    page.$options.onShow[0].call(page);await flush();await flush();
    assert.equal(page.typeListData.length,2);assert.equal(page.dishListItems[0].name,'口味菜品');
    assert.match(text(tree()),/余味居/);assert.match(text(tree()),/选择规格/);
    tap(env,page,byClass(tree(),'check_but')[0]);
    assert.equal(page.openMoreNormPop,true);assert.deepEqual(plain(page.flavorDataes),['微辣']);
    tap(env,page,byClass(tree(),'dish_card_add').find(n=>text(n)==='加入购物车'));await flush();await flush();
    assert.deepEqual(plain(env.store.state.orderListData),[A]);
    assert.ok(env.requests.find(r=>r.url.endsWith('/shoppingCart/add')).header.authentication);
    // Menu switch goes through the existing native event-opts and legacy API/store.
    page.arr=[0,100]; page.menuHeight=200;page.menuItemHeight=50;
    const menu=nodes(tree()).find(n=>n.tag==='view' && n.data?.attrs?.['data-event-opts']?.[0]?.[1]?.[0]?.[0]==='swichMenu' && text(n)==='套餐');
    tap(env,page,menu);await flush();await flush();
    assert.equal(page.dishListItems[0].type,2);assert.equal(page.dishListItems[0].name,'目录套餐');
    assert.ok(env.requests.some(r=>r.url.endsWith('/setmeal/list') && r.data.categoryId===102));
    tap(env,page,byClass(tree(),'order_but').at(-1));await flush();
    assert.deepEqual(env.redirects,['/pages/order/index']);
  } finally {page.$destroy();}
});

for (const meal of [false,true]) test(`native ${meal?'setmeal':'dish'} detail popup preserves visible add event (scope regression)`,async()=>{
  const item={id:meal?12:11,name:'详情商品',image:'/static/imgDefault.png',price:18,flavors:[]};
  let cart=[meal?{...plain(A),dishId:null,setmealId:12}:plain(A)];
  const {env,page,tree}=home({respond:options=>{
    if(options.url.endsWith('/category/list'))return {code:1,data:[{id:101,name:'目录',type:meal?2:1}]};
    if(/\/(dish|setmeal)\/list$/.test(options.url))return {code:1,data:[item]};
    if(options.url.includes('/setmeal/dish/'))return {code:1,data:[]};
    if(options.url.endsWith('/shoppingCart/add')){
      assert.equal(options.data[meal?'setmealId':'dishId'],item.id);cart[0].number++;
    }
    return {code:1,data:plain(cart)};
  }});
  try{
    page.$options.onShow[0].call(page);await flush();await flush();
    tap(env,page,byClass(tree(),'dish_img')[0]);await flush();
    assert.equal(page.openDetailPop,true);
    const popup=byClass(tree(),'dish_detail_pop')[0];assert.ok(popup);
    const add=byClass(popup,'dish_add')[0];assert.ok(add,'normal catalog detail add remains visible');
    tap(env,page,add);await flush();await flush();
    assert.equal(env.store.state.orderListData[0].number,3);
    assert.equal(env.requests.filter(r=>r.url.endsWith('/shoppingCart/add')).length,1);
    assert.ok(!env.requests.some(r=>/clean|submit/.test(r.url)));
  }finally{page.$destroy();}
});
