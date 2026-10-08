import { describe, expect, it } from 'vitest'
import { formatRiskLevel } from './order-risk'

describe('order risk display', () => {
  it('does not fabricate low risk for missing or unavailable results', () => {
    for (const level of [undefined, null, '', 'UNAVAILABLE']) {
      expect(formatRiskLevel(level)).toBe('风险暂不可用')
    }
    expect(formatRiskLevel('LOW')).toBe('LOW')
    expect(formatRiskLevel('HIGH')).toBe('HIGH')
  })
})
