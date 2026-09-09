import { describe, expect, it } from 'vitest'
import { formatCurrency, formatDateTime } from '../utils/format'

describe('format', () => {
  it('formats currency', () => {
    expect(formatCurrency(12)).toContain('12')
  })

  it('formats datetime', () => {
    expect(formatDateTime('2026-01-02 03:04:05')).toContain('2026')
  })
})
