// Issue15 browser rendering regression, not an official WeChat renderer.
// Reads real WXML/WXSS and app style imports; rpx is viewportWidth / 750.
// Adapter models native block views, inline text, scroll-view width:100% and
// a constrained vertical flex scroller. Native fonts, keyboard, navigation,
// socket events and SDK layout are outside this fixture's evidence boundary.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('/usr/local/lib/node_modules/playwright');

const root = path.resolve(__dirname, '../..');
const miniapp = path.join(root, 'yuweiju-weixin-miniapp');
const source = path.join(miniapp, 'pages/customer-service/index');
const escape = value => String(value).replace(/[&<>"']/g, c => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));
function styles(file) {
  return fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/@import\s+['"]([^'"]+)['"];?/g,
    (_, imported) => styles(path.resolve(path.dirname(file), imported)));
}

// Synthetic content only; no requests, authentication or conversation logging.
const cases = [
  { name: 'short', content: '测试消息' },
  { name: 'unbroken', content: 'Ab9'.repeat(666) + 'XY' },
  { name: 'multiline', content: '第一行\n第二行\n第三行' },
  { name: 'long-spaced', content: '测试内容 wrap words '.repeat(100) },
];
const messages = cases.flatMap(test => [1, 2].map(senderType => ({
  ...test, senderType, createTime: '2026-10-13 06:00:00',
})));

function renderWxml() {
  let template = fs.readFileSync(`${source}.wxml`, 'utf8');
  const loop = template.match(/<block\s+wx:for="{{messages}}"[^>]*>([\s\S]*?)<\/block>/);
  assert.ok(loop, 'actual message loop must be rendered');
  const rows = messages.map(item => loop[1]
    .replace(/<view class="cs-system" wx:else>[\s\S]*?<\/view>/, '')
    .replace(/\s+wx:if="{{item.senderType !== 3}}"/, '')
    .replace(/{{item.senderType === 1 \? 'admin' : item.senderType === 2 \? 'user' : 'system'}}/g,
      item.senderType === 1 ? 'admin' : 'user')
    .replace(/{{item.content}}/g, escape(item.content))
    .replace(/{{item.createTime}}/g, escape(item.createTime))).join('');
  template = template.replace(loop[0], rows)
    .replace(/<view class="cs-load"[^>]*>[\s\S]*?<\/view>/, '')
    .replace(/<view class="cs-empty"[^>]*>[\s\S]*?<\/view>/, '')
    .replace(/{{wsConnected \? 'online' : 'offline'}}/g, 'online')
    .replace(/{{wsConnected \? '已连接' : '连接中'}}/g, '已连接')
    .replace(/{{scrollTop}}/g, '0').replace(/{{inputValue}}/g, '')
    .replace(/<textarea([\s\S]*?)\/>/, '<textarea$1></textarea>');
  assert.ok(!template.includes('{{'), 'fixture must resolve actual WXML bindings');
  return template;
}

async function main() {
  const browser = await chromium.launch({ executablePath: '/usr/bin/chromium',
    headless: true, args: ['--no-sandbox'] });
  const results = [];
  try {
    const page = await browser.newPage();
    const html = renderWxml();
    const wxss = styles(path.join(miniapp, 'app.wxss')) + '\n' + styles(`${source}.wxss`);
    for (const width of [320, 375, 390, 430]) {
      await page.setViewportSize({ width, height: 780 });
      const css = wxss.replace(/(-?\d+(?:\.\d+)?)rpx/g, (_, n) => `${Number(n) * width / 750}px`);
      await page.setContent(`<style>
        html, body { margin:0; height:100%; }
        page, view, scroll-view { display:block; }
        text { display:inline; }
        scroll-view { width:100%; min-height:0; overflow-y:auto; overflow-x:hidden; }
        textarea { min-width:0; resize:none; }
        button { padding:0; border:0; margin:0; flex-shrink:0; }
        ${css}
      </style><page>${html}</page>`);
      const geometry = await page.evaluate(() => {
        const rect = element => {
          const r = element.getBoundingClientRect ? element.getBoundingClientRect() : element;
          return { left:r.left, right:r.right, top:r.top, bottom:r.bottom, width:r.width, height:r.height };
        };
        const list = document.querySelector('.cs-list');
        const input = document.querySelector('.cs-input');
        const rows = [...document.querySelectorAll('.cs-message')].map(row => {
          const bubble = row.querySelector('.cs-bubble');
          const content = row.querySelector('.cs-content');
          const time = row.querySelector('.cs-time');
          const range = document.createRange();
          range.selectNodeContents(content);
          const textRects = [...range.getClientRects()].map(rect);
          const timeRange = document.createRange();
          timeRange.selectNodeContents(time);
          return { row:rect(row), bubble:rect(bubble), content:rect(content), time:rect(time),
            textRects, timeRects:[...timeRange.getClientRects()].map(rect),
            contentText:content.textContent, timeText:time.textContent,
            background:getComputedStyle(bubble).backgroundColor,
            direction:getComputedStyle(row).justifyContent,
            timeAlign:getComputedStyle(time).textAlign,
            contentHeight:content.clientHeight, contentScrollHeight:content.scrollHeight };
        });
        const initialTop = list.scrollTop;
        list.scrollTop = list.scrollHeight;
        const bottomTop = list.scrollTop;
        const last = rect(list.querySelector('.cs-message:last-child'));
        const lastTime = rect(list.querySelector('.cs-message:last-child .cs-time'));
        list.querySelector('.cs-message:last-child').scrollIntoView({ block:'start' });
        const lastStart = rect(list.querySelector('.cs-message:last-child'));
        list.scrollTop = 0;
        const restoredTop = list.scrollTop;
        return { list:rect(list), input:rect(input), header:rect(document.querySelector('.cs-header')),
          rows, initialTop, bottomTop, restoredTop, last, lastTime, lastStart,
          scrollHeight:list.scrollHeight, clientHeight:list.clientHeight,
          documentWidth:document.documentElement.scrollWidth,
          textarea:rect(document.querySelector('.cs-textarea')),
          send:rect(document.querySelector('.cs-send')) };
      });
      // Geometry tolerance is for subpixel rounding, not a pixel baseline.
      const inside = (r, container, label) => {
        assert.ok(r.left >= container.left - 0.1 && r.right <= container.right + 0.1,
          `${width}: ${label} extends outside [${container.left},${container.right}]: [${r.left},${r.right}]`);
      };
      const viewport = { left:0, right:width };
      const summary = { viewport:width, listWidth:geometry.list.width,
        listRight:geometry.list.right, rows:geometry.rows.length,
        bubbleRights:geometry.rows.map(row => row.bubble.right), passed:false };
      results.push(summary);
      inside(geometry.list, viewport, 'list');
      assert.equal(geometry.rows.length, messages.length);
      for (const [i, row] of geometry.rows.entries()) {
        inside(row.row, geometry.list, `row ${i}`);
        inside(row.bubble, viewport, `bubble ${i}`);
        inside(row.bubble, row.row, `bubble ${i} in row`);
        inside(row.content, row.bubble, `content ${i}`);
        inside(row.time, row.bubble, `timestamp ${i}`);
        for (const r of [...row.textRects, ...row.timeRects]) inside(r, row.bubble, `text ${i}`);
        assert.equal(row.contentText, messages[i].content, `${width}: all synthetic content retained`);
        assert.equal(row.timeText, messages[i].createTime);
        assert.equal(row.timeAlign, 'right');
        assert.equal(row.direction, messages[i].senderType === 1 ? 'flex-end' : 'flex-start');
        assert.equal(row.background, messages[i].senderType === 1 ? 'rgba(110, 29, 32, 0.12)' : 'rgb(255, 255, 255)');
        assert.ok(row.contentScrollHeight <= row.contentHeight + 1, `${width}: content vertically clipped`);
        if (messages[i].name !== 'short') assert.ok(row.textRects.length >= 3, `${width}: long/multiline content wraps`);
        const gap = messages[i].senderType === 1 ? row.row.right - row.bubble.right : row.bubble.left - row.row.left;
        const expectedGap = (messages[i].senderType === 1 ? 12 : 6) * width / 750;
        assert.ok(Math.abs(gap - expectedGap) < 0.1, `${width}: original side padding/direction preserved`);
      }
      assert.ok(geometry.documentWidth <= width, `${width}: page horizontally overflows`);
      inside(geometry.input, viewport, 'input');
      inside(geometry.textarea, geometry.input, 'textarea');
      inside(geometry.send, geometry.input, 'send button');
      assert.ok(geometry.input.bottom <= 780.1 && geometry.input.top >= geometry.list.bottom - 0.1,
        `${width}: input stays below constrained list`);
      assert.ok(geometry.header.bottom <= geometry.list.top + 0.1);
      assert.ok(geometry.scrollHeight > geometry.clientHeight && geometry.bottomTop > geometry.initialTop,
        `${width}: history vertically scrolls`);
      assert.ok(geometry.last.bottom <= geometry.list.bottom + 1 && geometry.lastTime.top >= geometry.list.top - 0.1,
        `${width}: final message ending and timestamp reachable by scroll`);
      assert.ok(geometry.lastStart.top >= geometry.list.top - 1 && geometry.lastStart.top < geometry.list.bottom,
        `${width}: long final message beginning reachable by scroll (${geometry.lastStart.top}, list ${geometry.list.top})`);
      assert.equal(geometry.restoredTop, 0);
      summary.passed = true;
    }
    console.log(JSON.stringify({ passed:true, fixture:'browser WXML/WXSS emulation; not official SDK', results }, null, 2));
  } catch (error) {
    console.log(JSON.stringify({ passed:false, failure:error.message, results }, null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
