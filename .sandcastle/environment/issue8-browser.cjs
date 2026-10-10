const {chromium} = require('/usr/local/lib/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
  const root = path.resolve(__dirname, '../..');
  const out = path.join(root, '.scratch/issue8');
  const fixture = JSON.parse(fs.readFileSync(path.join(out, 'browser-fixture.json'), 'utf8'));
  const browser = await chromium.launch({executablePath:'/usr/bin/chromium', headless:true, args:['--no-sandbox']});
  const results = [];
  try {
    const page = await browser.newPage();
    // All navigations/requests must remain container loopback; external assets are not needed.
    await page.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.hostname === '127.0.0.1' && ['18087','18088'].includes(url.port)) return route.continue();
      return route.abort();
    });
    await page.goto('http://127.0.0.1:18088/', {waitUntil:'networkidle'});
    await page.getByPlaceholder('用户名').fill('sandbox_admin');
    await page.getByPlaceholder('密码', {exact:true}).fill('sandbox-only-login');
    const response = page.waitForResponse(r => r.url().endsWith('/admin/employee/login') && r.request().method() === 'POST');
    await page.getByRole('button', {name:'登录'}).click();
    assert.equal((await (await response).json()).code, 1);
    await page.waitForURL(url => !url.pathname.includes('login'));
    results.push({scenario:'synthetic normal password entered through actual Vue login page',passed:true});
    for (const group of ['category','dish','setmeal']) {
      const loaded = page.waitForResponse(r => r.url().includes(`/admin/${group}/page`) && r.request().method() === 'GET');
      await page.goto(`http://127.0.0.1:18088/${group}`, {waitUntil:'networkidle'});
      assert.equal((await (await loaded).json()).code, 1);
      // Search through the actual catalog view so owned records are visible regardless of fixture size.
      await page.locator('.order-search input').first().fill(fixture.prefix);
      const filtered = page.waitForResponse(r => r.url().includes(`/admin/${group}/page`) && new URL(r.url()).searchParams.get('name') === fixture.prefix);
      await page.locator('.order-search button.flip-btn').click();
      const body = await (await filtered).json();
      assert.equal(body.code, 1);
      assert.ok(body.data.records.some(row => row.name === fixture[group]));
      await page.getByText(fixture[group], {exact:true}).first().waitFor({state:'visible'});
      await page.screenshot({path:path.join(out, `synthetic-${group}-catalog.png`)});
      results.push({scenario:`actual Vue ${group} view search renders owned catalog record`,passed:true});
      console.log(`ISSUE8 BROWSER ${group.toUpperCase()}_VIEW_RESULT_PASS`);
    }
    fs.writeFileSync(path.join(out,'browser-results.json'),JSON.stringify(results,null,2));
    console.log('ISSUE8 SYNTHETIC_PAGE_LOGIN_CATALOG_PASS; HUMAN_ALL_ACCEPTANCE_PENDING');
  } finally { await browser.close(); }
})().catch(error => {
  // Only assertion/locator summary; no HTTP bodies, cookies or tokens.
  console.error('ISSUE8 synthetic browser failed: ' + error.name);
  process.exitCode=1;
});
