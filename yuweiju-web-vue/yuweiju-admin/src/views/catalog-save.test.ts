import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import type { InternalAxiosRequestConfig } from 'axios'
import DishEditor from './dish/add.vue'
import SetmealEditor from './setmeal/add.vue'

const { adapter, push } = vi.hoisted(() => ({ adapter: vi.fn(), push: vi.fn() }))
// Only HTTP transport is synthetic: real page -> API module -> request/error wrapper -> axios interceptors.
vi.mock('axios', async importOriginal => {
  const actual = await importOriginal<typeof import('axios')>()
  return { ...actual, default: { ...actual.default, create: (config: object) => actual.default.create({ ...config, adapter }) } }
})
const route = reactive<{ query: { id?: string } }>({ query: {} })
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
let wrapper: ReturnType<typeof mount> | undefined
let rejected = true
const saves: Array<{ method: string; url: string; body: Record<string, unknown> }> = []
const message = '金额必须为0到99999999.99且最多两位有效小数'
async function tick() { await flushPromises(); await nextTick() }
async function click(selector: string) {
  const button = document.querySelector<HTMLElement>(selector)
  expect(button, selector).not.toBeNull(); button!.click(); await tick()
}
beforeEach(() => {
  rejected = true; saves.length = 0
  adapter.mockImplementation(async (config: InternalAxiosRequestConfig) => {
    const url = config.url ?? ''; const method = config.method ?? ''
    let data: unknown
    if (method === 'post' || method === 'put') {
      saves.push({ method, url, body: JSON.parse(config.data as string) as Record<string, unknown> })
      data = rejected
        ? { code: 0, success: false, message, msg: message, data: null }
        : { code: 1, success: true, message: '成功', msg: '成功', data: null }
    } else if (url === '/admin/category/list') {
      const type = Number(config.params?.type)
      data = { code: 1, data: [{ id: type === 2 ? 900002 : 900001, name: '合成分类', type, sort: 1, status: 1 }] }
    } else if (url === '/admin/dish/page') {
      data = { code: 1, data: { total: 1, records: [{ id: 910001, name: '合成菜品', price: 8, categoryId: 900001, status: 1 }] } }
    } else if (url.includes('/admin/dish/') || url.includes('/admin/setmeal/')) {
      const meal = url.includes('setmeal')
      data = { code: 1, data: { id: 920001, name: '原名称', price: 18, categoryId: meal ? 900002 : 900001,
        image: 'http://local.invalid/original.png', description: '原描述', flavors: [],
        setmealDishes: [{ dishId: 910001, name: '合成菜品', price: 8, copies: 3 }] } }
    } else throw new Error(`Unexpected transport ${method} ${url}`)
    return { data, status: 200, statusText: 'OK', headers: {}, config }
  })
})
afterEach(() => {
  wrapper?.unmount(); wrapper = undefined; ElMessage.closeAll(); document.body.innerHTML = ''
  vi.restoreAllMocks(); vi.clearAllMocks()
})

describe('catalog real create/edit correction UX (#60/F4)', () => {
  for (const [kind, Editor] of [['dish', DishEditor], ['setmeal', SetmealEditor]] as const) {
    for (const editing of [false, true]) {
      it(`${kind} ${editing ? 'edit' : 'create'} displays backend rejection, retains values, and saves correction`, async () => {
        route.query = editing ? { id: '920001' } : {}
        const success = vi.spyOn(ElMessage, 'success')
        const error = vi.spyOn(ElMessage, 'error')
        wrapper = mount(Editor, { attachTo: document.body, global: { plugins: [ElementPlus] } }); await tick()
        await wrapper.get('.name-input input').setValue('待纠正目录')
        await wrapper.get('.price-input input').setValue('18.375')
        if (!editing) {
          await wrapper.get('.category-select .el-select__wrapper').trigger('click'); await tick()
          await click('.el-select-dropdown__item')
          if (kind === 'setmeal') {
            await click('.add-dish-btn')
            await click('.picker-list .el-table__body input[type=checkbox]')
            await click('.dish-picker-dialog .picker-btn.is-confirm')
            expect(wrapper.get('.summary-chip').text()).toContain('合成菜品')
          }
        }
        await wrapper.get('.action-btn.is-primary').trigger('click'); await tick()
        expect(saves).toHaveLength(1)
        expect(saves[0]).toMatchObject({ method: editing ? 'put' : 'post', url: `/admin/${kind}`,
          body: { name: '待纠正目录', price: 18.375, categoryId: kind === 'setmeal' ? 900002 : 900001 } })
        if (kind === 'setmeal') expect(saves[0]!.body.setmealDishes).toEqual([{ dishId: 910001, copies: editing ? 3 : 1 }])
        expect(error).toHaveBeenCalledExactlyOnceWith(message)
        expect(document.querySelectorAll('.el-message--error')).toHaveLength(1)
        expect(document.body.textContent).toContain(message)
        expect(success).not.toHaveBeenCalled(); expect(push).not.toHaveBeenCalled()
        expect(wrapper.get('.name-input input').element).toHaveProperty('value', '待纠正目录')
        expect(wrapper.get('.price-input input').element).toHaveProperty('value', '18.375')
        expect(wrapper.get('.action-btn.is-primary').attributes('disabled')).toBeUndefined()
        // A zero-price correction is legitimate and must reach the current backend.
        await wrapper.get('.price-input input').setValue('0'); rejected = false
        await wrapper.get('.action-btn.is-primary').trigger('click'); await tick()
        expect(saves).toHaveLength(2)
        expect(saves[1]!.body).toEqual({ ...saves[0]!.body, price: 0 })
        expect(success).toHaveBeenCalledExactlyOnceWith('保存成功')
        expect(push).toHaveBeenCalledExactlyOnceWith({ name: kind === 'dish' ? 'Dish' : 'Setmeal' })
      })
    }
  }
})
