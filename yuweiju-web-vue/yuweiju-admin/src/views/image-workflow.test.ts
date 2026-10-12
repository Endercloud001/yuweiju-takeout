import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import Cookies from 'js-cookie'
import DishEditor from './dish/add.vue'
import SetmealEditor from './setmeal/add.vue'
import DefaultImagePicker from '../components/default-image-picker.vue'
import { getDefaultImages } from '../api/modules/common'
import { editDish } from '../api/modules/dish'
import { editSetmeal } from '../api/modules/setmeal'

vi.hoisted(() => { vi.stubEnv('VITE_API_BASE', 'http://local.invalid') })
const route = reactive({ query: { id: '920001' } })
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../api/modules/category', () => ({ getCategoryList: vi.fn(async () => ({ code: 1, data: [{ id: 900001, name: 'Synthetic' }] })) }))
vi.mock('../api/modules/dish', () => ({
  addDish: vi.fn(), editDish: vi.fn(async () => ({ code: 1 })), getDishPage: vi.fn(async () => ({ code: 1, data: { records: [], total: 0 } })),
  getDishById: vi.fn(async () => ({ code: 1, data: { id: 920001, name: 'Synthetic dish', categoryId: 900001, price: 18, image: '', flavors: [] } })),
}))
vi.mock('../api/modules/setmeal', () => ({
  addSetmeal: vi.fn(), editSetmeal: vi.fn(async () => ({ code: 1 })),
  getSetmealById: vi.fn(async () => ({ code: 1, data: { id: 920001, name: 'Synthetic meal', categoryId: 900001, price: 18, image: '', setmealDishes: [{ dishId: 900001, name: 'Synthetic dish', copies: 1 }] } })),
}))
vi.mock('../api/modules/common', () => ({ getDefaultImages: vi.fn() }))
vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
const images = [
  { name: 'alpha.png', objectKey: 'dish/alpha.png', url: 'http://local.invalid/alpha.png', scene: 'dish', size: 68 },
  { name: 'beta.png', objectKey: 'dish/beta.png', url: 'http://local.invalid/beta.png', scene: 'dish', size: 68 },
]
const mounted: Array<ReturnType<typeof mount>> = []
afterEach(() => {
  for (const wrapper of mounted.splice(0)) wrapper.unmount()
  Cookies.remove('token'); document.body.innerHTML = ''; vi.restoreAllMocks(); vi.clearAllMocks()
})
async function tick() { await flushPromises(); await nextTick() }
async function confirm() {
  const button = document.querySelector<HTMLButtonElement>('.picker-btn.is-confirm')!
  expect(button).not.toBeNull(); button.click(); await tick()
}
async function filterBeta() {
  const input = document.querySelector<HTMLInputElement>('.picker-search input')!
  input.value = ' BETA '; input.dispatchEvent(new Event('input', { bubbles: true })); await tick()
  expect(document.querySelectorAll('.picker-card')).toHaveLength(1)
  expect(document.querySelector('.picker-card')?.textContent).toContain('beta.png')
}
describe('default image selection', () => {
  it('filters real dialog cards and emits selected image without changing API items', async () => {
    vi.mocked(getDefaultImages).mockResolvedValue({ code: 1, msg: 'success', data: images })
    const before = JSON.stringify(images)
    const wrapper = mount(DefaultImagePicker, { props: { modelValue: false, scene: 'dish', currentUrl: images[0]!.url }, attachTo: document.body, global: { plugins: [ElementPlus] } }); mounted.push(wrapper)
    await wrapper.setProps({ modelValue: true }); await tick()
    expect(getDefaultImages).toHaveBeenCalledWith({ scene: 'dish', limit: 48 })
    await filterBeta(); await confirm()
    expect(wrapper.emitted('select')).toEqual([[images[1]]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(JSON.stringify(images)).toBe(before)
  })
  it('reports empty and upstream failure without emitting a stale selection', async () => {
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }))
    vi.mocked(getDefaultImages).mockResolvedValue({ code: 1, msg: 'success', data: [] })
    const wrapper = mount(DefaultImagePicker, { props: { modelValue: false, scene: 'setmeal' }, attachTo: document.body, global: { plugins: [ElementPlus] } }); mounted.push(wrapper)
    await wrapper.setProps({ modelValue: true }); await tick(); expect(document.body.textContent).toContain('默认图片库为空'); await confirm(); expect(wrapper.emitted('select')).toBeUndefined()
    await wrapper.setProps({ modelValue: false }); vi.mocked(getDefaultImages).mockRejectedValue(new Error('获取默认图片失败'))
    await wrapper.setProps({ modelValue: true }); await tick()
    expect(document.querySelector('.picker-error')?.textContent).toBe('获取默认图片失败'); expect(error).toHaveBeenCalledTimes(1)
    await confirm(); expect(wrapper.emitted('select')).toBeUndefined()
  })
})
describe('existing product editors upload/select/save', () => {
  for (const [scene, Editor, save] of [['dish', DishEditor, editDish], ['setmeal', SetmealEditor, editSetmeal]] as const) {
    it(`${scene} sends real picker URL in existing save payload`, async () => {
      vi.mocked(getDefaultImages).mockResolvedValue({ code: 1, msg: 'success', data: images })
      const wrapper = mount(Editor, { attachTo: document.body, global: { plugins: [ElementPlus] } }); mounted.push(wrapper); await tick()
      const open = wrapper.findAll('button').find(node => node.text().includes('从默认图片库选择图片'))!
      await open.trigger('click'); await tick(); expect(getDefaultImages).toHaveBeenCalledWith({ scene, limit: 48 })
      await filterBeta(); await confirm()
      expect(wrapper.get('.preview-img-inline').attributes('src')).toBe(images[1]!.url)
      await wrapper.get('.action-btn.is-primary').trigger('click'); await tick()
      expect(save).toHaveBeenCalledWith(expect.objectContaining({ id: 920001, image: images[1]!.url, name: expect.any(String), categoryId: 900001, price: 18 }))
    })
    it(`${scene} real upload handles business and transport errors and saves successful URL`, async () => {
      Cookies.set('token', 'synthetic-component-token')
      const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }))
      const requests: XMLHttpRequest[] = []
      vi.spyOn(XMLHttpRequest.prototype, 'open').mockImplementation(function (this: XMLHttpRequest, _method: string, url: string | URL) {
        expect(_method.toLowerCase()).toBe('post'); expect(String(url)).toBe('http://local.invalid/admin/common/upload'); requests.push(this)
      })
      vi.spyOn(XMLHttpRequest.prototype, 'setRequestHeader').mockImplementation(function (name: string, value: string) { if (name === 'token') expect(value).toBe('synthetic-component-token') })
      vi.spyOn(XMLHttpRequest.prototype, 'send').mockImplementation(function (body: Document | XMLHttpRequestBodyInit | null | undefined) { expect(body).toBeInstanceOf(FormData) })
      const wrapper = mount(Editor, { attachTo: document.body, global: { plugins: [ElementPlus] } }); mounted.push(wrapper); await tick()
      const upload = async () => {
        const input = wrapper.get('input[type=file]'); Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['png'], 'synthetic.png', { type: 'image/png' })] }); await input.trigger('change'); await tick(); return requests.at(-1)!
      }
      let xhr = await upload(); expect(xhr).toBeDefined(); expect(XMLHttpRequest.prototype.setRequestHeader).toHaveBeenCalledWith('token', 'synthetic-component-token')
      Object.defineProperty(xhr, 'status', { configurable: true, value: 200 }); Object.defineProperty(xhr, 'responseText', { configurable: true, value: JSON.stringify({ code: 0, msg: '上传失败' }) }); xhr.dispatchEvent(new Event('load')); await tick()
      expect(error).toHaveBeenCalledWith('上传失败'); expect(wrapper.find('.preview-img-inline').exists()).toBe(false)
      xhr = await upload(); xhr.dispatchEvent(new Event('error')); await tick(); expect(error).toHaveBeenCalledWith('上传失败，请稍后重试'); expect(wrapper.find('.preview-img-inline').exists()).toBe(false)
      xhr = await upload(); Object.defineProperty(xhr, 'status', { configurable: true, value: 200 }); Object.defineProperty(xhr, 'responseText', { configurable: true, value: JSON.stringify({ code: 1, data: 'http://local.invalid/uploaded.png' }) }); xhr.dispatchEvent(new Event('load')); await tick()
      expect(wrapper.get('.preview-img-inline').attributes('src')).toBe('http://local.invalid/uploaded.png')
      await wrapper.get('.action-btn.is-primary').trigger('click'); await tick(); expect(save).toHaveBeenCalledWith(expect.objectContaining({ image: 'http://local.invalid/uploaded.png' }))
    })
  }
})
