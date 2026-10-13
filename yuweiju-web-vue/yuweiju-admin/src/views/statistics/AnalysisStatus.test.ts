import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalysisStatus from './AnalysisStatus.vue'
import type { AnalysisRun } from '../../api/modules/analysis'

const metrics = { modelVersion: 'new-heat', snapshotDate: '2026-06-02', sampleCount: 28, rmse: 4, mae: 3, rmseRatio: 0.5, maeRatio: 0.4, rmseThreshold: 0.3, maeThreshold: 0.2, rmsePass: false, maePass: false }
describe('statistics actual status template', () => {
  for (const [status, label, reason] of [
    ['COMPLETE', '完成', null], ['SKIPPED', '跳过', 'NO_USER_SNAPSHOTS'],
    ['PARTIAL', '部分成功', 'NO_MODEL_BASELINE'], ['FAILED', '失败', 'DEPENDENCY_FAILURE'],
  ] as const) {
    it(`shows ${status}, actual counts/version/window and failed effectiveness separately`, () => {
      const run: AnalysisRun = { message: 'response', status, snapshotDate: '2026-06-02', executed: status !== 'SKIPPED', executionMode: 'SYNCHRONOUS', heatModelTrained: status === 'COMPLETE',
        stages: [{ stage: 'prediction', status, reason, windowStart: '2026-06-02', windowEnd: '2026-06-08', outputs: { predictionCount: status === 'SKIPPED' ? 0 : 4, modelVersion: 'new-heat' } }] }
      const wrapper = mount(AnalysisStatus, { props: { run, metrics, loading: false, error: '' } })
      const text = wrapper.text()
      expect(text).toContain(`本次执行：${label}`)
      expect(text).toContain('2026-06-02 ~ 2026-06-08')
      expect(text).toContain('预测条数')
      expect(text).toContain('new-heat')
      expect(text).toContain('阈值未通过')
      expect(text).toContain('code1 不表示效果通过')
      if (reason) expect(text).toContain(reason)
      wrapper.unmount()
    })
  }
  it('shows synchronous pending and dependency error without an invented completion', () => {
    const wrapper = mount(AnalysisStatus, { props: { run: null, metrics: {}, loading: true, error: '网络不可用' } })
    expect(wrapper.text()).toContain('同步执行中')
    expect(wrapper.text()).toContain('网络不可用')
    expect(wrapper.text()).toContain('尚无有效效果指标')
    expect(wrapper.text()).not.toContain('本次执行：完成')
    wrapper.unmount()
  })
})
