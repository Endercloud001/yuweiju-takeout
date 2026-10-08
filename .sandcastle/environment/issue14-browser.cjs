const { chromium } = require('/usr/local/lib/node_modules/playwright');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
  const results = [];
  const record = name => { results.push(name); console.log(`ASSERT ${name} PASS`); };
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('http://127.0.0.1:5173/order', { waitUntil: 'networkidle' });
    assert(new URL(page.url()).pathname === '/login');
    record('unauthenticated_route_redirect');
    await page.getByPlaceholder('用户名').fill('sandbox_admin');
    await page.getByPlaceholder('密码', { exact: true }).fill('sandbox-only-login');
    const login = page.waitForResponse(r => r.url().endsWith('/admin/employee/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    assert.equal((await (await login).json()).code, 1);
    await page.waitForURL(url => !url.pathname.includes('login'));
    record('real_fixture_password_login');
    await page.goto('http://127.0.0.1:5173/order', { waitUntil: 'networkidle' });
    await page.getByText('ISSUE14-BROWSER-1', { exact: true }).waitFor();
    await page.getByText('风险暂不可用', { exact: true }).waitFor();
    await page.getByText('LOW', { exact: true }).waitFor();
    assert.equal(await page.locator('.el-table__body-wrapper tr').count(), 2);
    record('list_real_normal_and_unavailable_risk');
    await page.screenshot({ path: '/evidence/order-normal.png', fullPage: true });
    await page.goto('http://127.0.0.1:5173/order/detail/914101', { waitUntil: 'networkidle' });
    await page.getByText('风险暂不可用', { exact: true }).waitFor();
    assert((await page.locator('.order-detail-page').innerText()).includes('66.60'));
    record('detail_real_unavailable_preserves_amount');
    await page.screenshot({ path: '/evidence/detail-unavailable.png', fullPage: true });
    await page.goto('http://127.0.0.1:5173/order/detail/914102', { waitUntil: 'networkidle' });
    await page.getByText('LOW', { exact: true }).waitFor();
    record('detail_real_low_risk');
    await page.goto('http://127.0.0.1:5173/order?status=99', { waitUntil: 'networkidle' });
    await page.locator('.el-table__empty-block').waitFor();
    assert.equal(await page.locator('.el-table__body-wrapper tr').count(), 0);
    record('list_real_empty_state');
    await page.screenshot({ path: '/evidence/order-empty.png', fullPage: true });

    // Delay genuine requests, then inject friendly business errors for UI states only.
    for (const [name, endpoint, url] of [
      ['list', '**/admin/order/conditionSearch?*', '/order'],
      ['detail', '**/admin/order/details/914101', '/order/detail/914101'],
    ]) {
      let release;
      const delay = new Promise(resolve => { release = resolve; });
      await page.route(endpoint, async route => { await delay; await route.continue(); });
      await page.goto('http://127.0.0.1:5173' + url, { waitUntil: 'domcontentloaded' });
      await page.locator('.el-loading-mask').first().waitFor({ state: 'visible' });
      record(`${name}_loading_state`);
      release();
      await page.getByText('风险暂不可用', { exact: true }).waitFor();
      await page.locator('.el-loading-mask').first().waitFor({ state: 'hidden' });
      record(`${name}_loading_cleared_after_response`);
      await page.unroute(endpoint);
      await page.route(endpoint, route => route.fulfill({ json: { code: 0, success: false, msg: '隔离界面故障夹具', message: '隔离界面故障夹具', data: null } }));
      await page.reload({ waitUntil: 'networkidle' });
      await page.getByText('隔离界面故障夹具', { exact: true }).waitFor();
      await page.locator('.el-loading-mask').first().waitFor({ state: 'hidden' });
      record(`${name}_business_error_display_and_loading_cleared`);
      await page.screenshot({ path: `/evidence/${name}-error.png`, fullPage: true });
      await page.unroute(endpoint);
    }
    await page.goto('http://127.0.0.1:5173/order/detail/914199', { waitUntil: 'networkidle' });
    await page.getByText('订单不存在', { exact: true }).waitFor();
    assert.equal(await page.locator('.order-detail-page .detail-grid').count(), 0);
    record('detail_real_missing_order');
    assert.equal(errors.length, 0, errors.join('\n'));
    record('no_browser_page_errors');
    console.log(JSON.stringify({ results, mockedStates: ['business error display only'], realLoginAndData: true }));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
