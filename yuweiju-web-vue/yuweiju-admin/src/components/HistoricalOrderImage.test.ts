import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import HistoricalOrderImage from './HistoricalOrderImage.vue'
import placeholder from '../assets/reference_images/菜品图片缺失补位图.svg'

const historical = 'https://example.invalid/historical-A.png'
describe('historical order image display', () => {
  for (const image of [undefined, null, '', ' \t\n']) {
    it(`renders the local placeholder for missing image ${JSON.stringify(image)}`, async () => {
      const row = Object.freeze({ image })
      const wrapper = mount(HistoricalOrderImage, { props: { row, image, name: '历史菜品' } })
      expect(wrapper.get('img').attributes('src')).toBe(placeholder)
      await wrapper.get('img').trigger('error')
      expect(wrapper.get('img').attributes('src')).toBe(placeholder)
      expect(row.image).toBe(image)
      wrapper.unmount()
    })
  }
  it('retains exact historical URL and recovers from a failed image when source or row changes', async () => {
    const row = Object.freeze({ image: historical })
    const wrapper = mount(HistoricalOrderImage, { props: { row, image: historical, name: '历史菜品' } })
    expect(wrapper.get('img').attributes('src')).toBe(historical)
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe(placeholder)
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe(placeholder)
    await wrapper.setProps({ image: historical + '?new-source' })
    expect(wrapper.get('img').attributes('src')).toBe(historical + '?new-source')
    await wrapper.get('img').trigger('error')
    await wrapper.setProps({ row: { image: historical }, image: historical })
    expect(wrapper.get('img').attributes('src')).toBe(historical)
    expect(row.image).toBe(historical)
    wrapper.unmount()
  })
  it('isolates two rows with the same historical URL and ignores late old-source error events', async () => {
    const Rows = defineComponent({
      components: { HistoricalOrderImage },
      data: () => ({ rows: [{ image: historical }, { image: historical }] }),
      template: '<div><HistoricalOrderImage v-for="(row, i) in rows" :key="i" :row="row" :image="row.image" name="历史菜品" /></div>',
    })
    const wrapper = mount(Rows)
    const oldImage = wrapper.findAll('img')[0]!
    await oldImage.trigger('error')
    expect(wrapper.findAll('img').map(img => img.attributes('src'))).toEqual([placeholder, historical])
    wrapper.vm.rows[0]!.image = historical + '?recovered'
    await wrapper.vm.$nextTick()
    await oldImage.trigger('error')
    expect(wrapper.findAll('img').map(img => img.attributes('src'))).toEqual([historical + '?recovered', historical])
    wrapper.unmount()
  })
})
