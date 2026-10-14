import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import ElementPlus from 'element-plus'
import OrderDetail from './detail.vue'
import OrderList from './index.vue'
import placeholder from '../../assets/reference_images/菜品图片缺失补位图.svg'

const fixture = vi.hoisted(() => ({
  id: 974, number: 'ISSUE12', status: 5, amount: 19,
  riskLevel: 'UNAVAILABLE', riskScore: null as number | null, riskReasons: '风险结果暂不可用', decisionStatus: 'PENDING',
  orderDetailList: [
    { name: '历史A', image: 'https://example.invalid/historical-A.png', number: 1, amount: 3 },
    { name: '空图', image: null, number: 2, amount: 3 },
    { name: '空串', image: '', number: 1, amount: 3 },
    { name: '空白', image: ' \t ', number: 1, amount: 3 },
    { name: '坏图', image: 'https://example.invalid/broken.png', number: 1, amount: 3 },
  ],
}))
const route = reactive({ params: { id: '974' }, query: {} })
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../../api/modules/order', () => ({
  getOrderPage: vi.fn(async () => ({ code: 1, data: { total: 1, records: [fixture] } })),
  getOrderDetails: vi.fn(async () => ({ code: 1, data: fixture })),
  confirmOrder: vi.fn(), rejectOrder: vi.fn(), cancelOrder: vi.fn(),
  deliveryOrder: vi.fn(), completeOrder: vi.fn(), submitOrderRiskFeedback: vi.fn(),
}))
// jsdom has no layout observation; page rendering and image DOM/error events remain real Vue/Element Plus.
vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
afterEach(() => { document.body.innerHTML = '' })

describe('existing order details render historical images inline', () => {
  for (const [label, View] of [['standalone detail', OrderDetail], ['list detail dialog', OrderList]] as const) {
    it(`${label} keeps dish/quantity/price/19 and displays A or local fallback without mutating API rows`, async () => {
      const before = JSON.stringify(fixture)
      const wrapper = mount(View, { attachTo: document.body, global: { plugins: [ElementPlus] } })
      try {
        await flushPromises()
        if (label === 'list detail dialog') {
          const view = wrapper.findAll('.op-radio .name').find(node => node.text() === '查看')!
          expect(view.exists()).toBe(true)
          await view.trigger('click')
          await flushPromises()
        }
        // Dialog teleports to body; standalone stays mounted in the same body.
        const rows = [...document.querySelectorAll('.dish-line')]
        expect(rows).toHaveLength(5)
        const images = rows.map(row => row.querySelector('img')!)
        expect(images.map(img => img.getAttribute('src'))).toEqual([
          fixture.orderDetailList[0]!.image, placeholder, placeholder, placeholder, fixture.orderDetailList[4]!.image,
        ])
        expect(rows[0]!.textContent).toContain('历史A')
        expect(rows[1]!.textContent).toMatch(/[x×]2/)
        expect(rows[0]!.textContent).toContain('3')
        expect(document.body.textContent).toContain('19')
        images[4]!.dispatchEvent(new Event('error'))
        await flushPromises()
        expect(rows[4]!.querySelector('img')!.getAttribute('src')).toBe(placeholder)
        expect(rows[0]!.querySelector('img')!.getAttribute('src')).toBe(fixture.orderDetailList[0]!.image)
        expect(JSON.stringify(fixture)).toBe(before)
      } finally { wrapper.unmount() }
    })
  }
})

describe('actual order detail risk states', () => {
  for (const [level, score, reason, decision, expected] of [
    ['UNAVAILABLE', null, '风险结果暂不可用', 'PENDING', '风险暂不可用'],
    ['HIGH', 90, 'suspicious_remark', 'REJECT', '已驳回'],
    ['LOW', 10, '特征数据不完整，规则降级：order_details', 'APPROVE', '已通过'],
  ] as const) {
    it(`renders ${level} and persisted feedback`, async () => {
      Object.assign(fixture, { riskLevel: level, riskScore: score, riskReasons: reason, decisionStatus: decision })
      const wrapper = mount(OrderDetail, { global: { plugins: [ElementPlus] } })
      await flushPromises()
      expect(wrapper.text()).toContain(expected)
      expect(wrapper.text()).toContain(reason)
      wrapper.unmount()
    })
  }
})
