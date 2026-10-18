import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SetmealEditor from './add.vue'
import { getCategoryList } from '../../api/modules/category'
import { getDishPage } from '../../api/modules/dish'
import { editSetmeal, getSetmealById } from '../../api/modules/setmeal'
import { getDefaultImages } from '../../api/modules/common'
import type { DishItem } from '../../types/dish'
import type { SetmealItem } from '../../types/setmeal'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { id: '920001' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../../api/modules/category', () => ({ getCategoryList: vi.fn() }))
vi.mock('../../api/modules/dish', () => ({ getDishPage: vi.fn() }))
vi.mock('../../api/modules/setmeal', () => ({
  getSetmealById: vi.fn(), editSetmeal: vi.fn(), addSetmeal: vi.fn(),
}))
vi.mock('../../api/modules/common', () => ({ getDefaultImages: vi.fn() }))
vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })

const detail: SetmealItem = {
  id: 920001, name: '原套餐', categoryId: 900001, price: 28, status: 1,
  image: 'http://local.invalid/original.png',
  setmealDishes: [
    { dishId: 910001, name: '保留菜品', price: 8, copies: 3 },
    { dishId: 910002, name: '其他菜品', price: 9, copies: 2 },
  ],
}
// The catalog options intentionally have no copies field, as in the real API.
const dishes: DishItem[] = [
  { id: 910001, name: '保留菜品', categoryId: 900002, price: 8, status: 1 },
  { id: 910002, name: '其他菜品', categoryId: 900002, price: 9, status: 1 },
  { id: 910003, name: '新增菜品', categoryId: 900003, price: 10, status: 1 },
]
let wrapper: ReturnType<typeof mount> | undefined
async function tick() { await flushPromises(); await nextTick() }
async function openEditor() {
  wrapper = mount(SetmealEditor, { attachTo: document.body, global: { plugins: [ElementPlus] } })
  await tick()
  expect(getSetmealById).toHaveBeenCalledWith(detail.id)
  expect(wrapper.get('.name-input input').element).toHaveProperty('value', detail.name)
  return wrapper
}
async function click(selector: string) {
  const button = document.querySelector<HTMLButtonElement>(selector)
  expect(button, selector).not.toBeNull()
  button!.click()
  await tick()
}
function dishCheckbox(name: string) {
  const row = Array.from(document.querySelectorAll('.picker-list .el-table__body tr'))
    .find(item => item.textContent?.includes(name))
  expect(row, name).toBeDefined()
  const checkbox = row!.querySelector<HTMLInputElement>('input[type=checkbox]')
  expect(checkbox).not.toBeNull()
  return checkbox!
}
async function selectDish(name: string) { dishCheckbox(name).click(); await tick() }
async function removeDish(name: string) {
  const card = Array.from(document.querySelectorAll('.picked-card'))
    .find(item => item.textContent?.includes(name))
  expect(card, name).toBeDefined()
  card!.querySelector<HTMLButtonElement>('.picked-remove')!.click()
  await tick()
}
async function save(expected: Array<{ dishId: number; copies: number }>) {
  await click('.action-btn.is-primary')
  expect(editSetmeal).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
    id: detail.id, setmealDishes: expected,
  }))
  expect(detail.setmealDishes?.map(({ copies }) => copies)).toEqual([3, 2])
}

beforeEach(() => {
  vi.mocked(getSetmealById).mockResolvedValue({ code: 1, msg: 'success', data: detail })
  vi.mocked(editSetmeal).mockResolvedValue({ code: 1, msg: 'success', data: '' })
  vi.mocked(getCategoryList).mockImplementation(async type => ({
    code: 1, msg: 'success', data: type === 2
      ? [{ id: 900001, name: '套餐分类', type: 2, sort: 1, status: 1 }]
      : [
          { id: 900002, name: '原菜分类', type: 1, sort: 1, status: 1 },
          { id: 900003, name: '新菜分类', type: 1, sort: 2, status: 1 },
        ],
  }))
  vi.mocked(getDishPage).mockImplementation(async query => ({
    code: 1, msg: 'success', data: {
      total: dishes.filter(d => d.categoryId === query.categoryId).length,
      records: dishes.filter(d => d.categoryId === query.categoryId),
    },
  }))
})
afterEach(() => {
  wrapper?.unmount(); wrapper = undefined
  ElMessage.closeAll()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('setmeal editor preserves selected dish copies (#56/F2)', () => {
  it('saves the loaded copies3 when only the name changes through the real form', async () => {
    const editor = await openEditor()
    await editor.get('.name-input input').setValue('只改套餐名称')
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910002, copies: 2 }])
    expect(editSetmeal).toHaveBeenCalledWith(expect.objectContaining({ name: '只改套餐名称', image: detail.image }))
  })

  it('saves the loaded copies3 when only the image changes through the real picker', async () => {
    const image = { name: 'replacement.png', url: 'http://local.invalid/replacement.png', objectKey: 'setmeal/replacement.png', scene: 'setmeal', size: 68 }
    vi.mocked(getDefaultImages).mockResolvedValue({ code: 1, msg: 'success', data: [image] })
    const editor = await openEditor()
    await editor.findAll('button').find(button => button.text().includes('从默认图片库选择图片'))!.trigger('click')
    await tick()
    await click('.picker-card')
    await click('.default-image-picker-dialog .picker-btn.is-confirm')
    expect(editor.get('.preview-img-inline').attributes('src')).toBe(image.url)
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910002, copies: 2 }])
    expect(editSetmeal).toHaveBeenCalledWith(expect.objectContaining({ name: detail.name, image: image.url }))
  })

  it('retains copies across repeated drawer open and confirm with catalog options lacking copies', async () => {
    await openEditor()
    for (let repeat = 0; repeat < 2; repeat++) {
      await click('.add-dish-btn')
      expect(dishCheckbox('保留菜品').checked).toBe(true)
      expect(dishCheckbox('其他菜品').checked).toBe(true)
      await click('.dish-picker-dialog .picker-btn.is-confirm')
    }
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910002, copies: 2 }])
  })

  it('retains copies3 while removing another dish, changing categories and adding a new dish as1', async () => {
    await openEditor()
    await click('.add-dish-btn')
    await removeDish('其他菜品')
    expect(dishCheckbox('其他菜品').checked).toBe(false)
    const categories = Array.from(document.querySelectorAll<HTMLButtonElement>('.sidebar-item'))
    categories.find(button => button.textContent === '新菜分类')!.click(); await tick()
    await selectDish('新增菜品')
    categories.find(button => button.textContent === '原菜分类')!.click(); await tick()
    expect(dishCheckbox('保留菜品').checked).toBe(true)
    expect(dishCheckbox('其他菜品').checked).toBe(false)
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910003, copies: 1 }])
  })

  it('defaults a removed then newly reselected dish to1 while retaining the other dish copies', async () => {
    await openEditor()
    await click('.add-dish-btn')
    await removeDish('保留菜品')
    expect(dishCheckbox('保留菜品').checked).toBe(false)
    await selectDish('保留菜品')
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await click('.add-dish-btn')
    expect(dishCheckbox('保留菜品').checked).toBe(true)
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await save([{ dishId: 910002, copies: 2 }, { dishId: 910001, copies: 1 }])
  })

  it('preserves a retained dish when table selection changes and defaults a rechecked dish to1', async () => {
    await openEditor()
    await click('.add-dish-btn')
    await selectDish('其他菜品')
    expect(dishCheckbox('其他菜品').checked).toBe(false)
    await selectDish('其他菜品')
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910002, copies: 1 }])
  })

  it('defaults to1 when reselected after confirming removal and reopening the drawer', async () => {
    await openEditor()
    await click('.add-dish-btn')
    await removeDish('保留菜品')
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    expect(wrapper!.findAll('.summary-chip').map(chip => chip.text())).toEqual(['其他菜品'])
    await click('.add-dish-btn')
    expect(dishCheckbox('保留菜品').checked).toBe(false)
    await selectDish('保留菜品')
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await save([{ dishId: 910002, copies: 2 }, { dishId: 910001, copies: 1 }])
  })

  it('keeps the committed copies when a drawer removal is canceled', async () => {
    await openEditor()
    await click('.add-dish-btn')
    await selectDish('保留菜品')
    expect(dishCheckbox('保留菜品').checked).toBe(false)
    await click('.dish-picker-dialog .picker-btn.is-cancel')
    await click('.add-dish-btn')
    expect(dishCheckbox('保留菜品').checked).toBe(true)
    await click('.dish-picker-dialog .picker-btn.is-confirm')
    await save([{ dishId: 910001, copies: 3 }, { dishId: 910002, copies: 2 }])
  })
})
