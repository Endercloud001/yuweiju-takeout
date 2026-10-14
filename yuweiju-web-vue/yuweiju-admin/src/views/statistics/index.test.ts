import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Statistics from './index.vue'
import { ApiError } from '../../api/http'
import type { AnalysisRun } from '../../api/modules/analysis'

const api = vi.hoisted(() => ({ train: vi.fn(), offline: vi.fn(), success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('echarts', () => ({ init: () => ({ setOption: vi.fn(), on: vi.fn(), resize: vi.fn(), dispose: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: api.success, warning: api.warning, error: api.error } }))
vi.mock('../../api/modules/analysis', () => ({
  runAnalysisTraining: api.train, getOfflineMetrics: api.offline,
  getHeatPredictionList: async () => ({ data: [] }), getUserClusterList: async () => ({ data: [] }),
}))
vi.mock('../../api/modules/dashboard', () => ({
  getTurnoverStatistics: async () => ({ data: null }), getUserStatistics: async () => ({ data: null }),
  getOrdersStatistics: async () => ({ data: null }), getTop10: async () => ({ data: null }), exportReport: vi.fn(),
}))
vi.mock('../../api/modules/order', () => ({
  getOrderRiskModels: async () => ({ data: [] }), getOrderRiskReplay: async () => ({ data: {} }), runOrderRiskTraining: vi.fn(),
}))
vi.mock('../../api/modules/dish', () => ({ getDishById: vi.fn() }))

describe('statistics route training result integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.offline.mockResolvedValue({ data: { rmsePass: false, maePass: false, modelVersion: 'current-new-model' } })
  })
  for (const status of ['COMPLETE', 'SKIPPED', 'PARTIAL', 'FAILED'] as const) {
    it(`renders ${status} from the actual button/API flow without converting code1 to effect success`, async () => {
      const run: AnalysisRun = { message: status, snapshotDate: '2026-06-02', status, executed: status !== 'SKIPPED', executionMode: 'SYNCHRONOUS', heatModelTrained: status === 'COMPLETE',
        stages: [{ stage: 'heatTraining', status, reason: status === 'SKIPPED' ? 'NO_USER_SNAPSHOTS' : status === 'FAILED' ? 'DEPENDENCY_FAILURE' : null,
          windowStart: '2026-03-04', windowEnd: '2026-05-26', outputs: { modelVersion: 'current-new-model', trainSampleCount: 300 } }] }
      if (status === 'SKIPPED' || status === 'FAILED') api.train.mockRejectedValue(new ApiError(status, { code: 0, msg: status, data: run }))
      else api.train.mockResolvedValue({ code: 1, data: run })
      const wrapper = mount(Statistics, { global: {
        directives: { loading: () => {} },
        stubs: { ElTooltip: { template: '<span><slot /></span>' }, ElDialog: true, ElDatePicker: true, ElTable: true, ElTableColumn: true, ElSelect: true, ElOption: true, ElTag: { template: '<span><slot /></span>' } },
      } })
      await flushPromises()
      await wrapper.get('.console-card button').trigger('click')
      await flushPromises()
      expect(api.train).toHaveBeenCalledOnce()
      const text = wrapper.get('.analysis-status').text()
      expect(text).toContain('current-new-model')
      expect(text).toContain('训练样本数：300')
      expect(text).toContain('2026-03-04 ~ 2026-05-26')
      expect(text).toContain('阈值未通过')
      if (status === 'SKIPPED') expect(text).toContain('无用户快照')
      if (status === 'FAILED') expect(text).toContain('依赖或持久化失败')
      expect(api.success).toHaveBeenCalledTimes(status === 'COMPLETE' ? 1 : 0)
      wrapper.unmount()
    })
  }
})
