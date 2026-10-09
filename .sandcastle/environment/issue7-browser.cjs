const {chromium} = require('/usr/local/lib/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
  const root = path.resolve(__dirname, '../..');
  const fixture = JSON.parse(fs.readFileSync(path.join(root, '.scratch/issue7/browser-fixture.json'), 'utf8'));
  const browser = await chromium.launch({executablePath:'/usr/bin/chromium', headless:true, args:['--no-sandbox']});
  try {
    const page = await browser.newPage();
    await page.goto('http://127.0.0.1:18088/', {waitUntil:'networkidle'});
    await page.getByPlaceholder('用户名').fill(fixture.username);
    await page.getByPlaceholder('密码', {exact:true}).fill(fixture.password);
    const response = page.waitForResponse(r => r.url().endsWith('/admin/employee/login') && r.request().method() === 'POST');
    await page.getByRole('button', {name:'登录'}).click();
    assert.equal((await (await response).json()).code, 1);
    await page.waitForURL(url => !url.pathname.includes('login'));
    await page.screenshot({path:path.join(root, '.scratch/issue7/synthetic-page-login.png')});
    console.log('ISSUE7 SYNTHETIC_NORMAL_PASSWORD_ENTERED_THROUGH_ADMIN_PAGE_PASS; HUMAN_REAL_PAGE_ACCEPTANCE_PENDING');
  } finally { await browser.close(); }
})().catch(() => {console.error('ISSUE7 synthetic browser login failed (credentials and tokens omitted)'); process.exitCode=1;});
