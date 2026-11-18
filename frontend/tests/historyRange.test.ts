import { describe, expect, it } from 'vitest'
import { createHistoryQuery, formatFocusDuration, validateLocalDateRange } from '../src/pages/historyRange'

describe('history query and display formatting', () => {
  it('builds inclusive seven and thirty local-calendar-day ranges', () => {
    const today = new Date(2026, 2, 1, 15, 30)
    expect(createHistoryQuery('7 days', undefined, 0, today)).toEqual({
      page: 0, size: 20, from: '2026-02-23', to: '2026-03-01',
    })
    expect(createHistoryQuery('30 days', 'COMPLETED', 2, today)).toEqual({
      page: 2, size: 20, from: '2026-01-31', to: '2026-03-01', status: 'COMPLETED',
    })
  })

  it('omits dates for all time and formats actual focus durations', () => {
    expect(createHistoryQuery('All time', undefined, 0, new Date(2026, 0, 1)))
      .toEqual({ page: 0, size: 20 })
    expect(formatFocusDuration(1500)).toBe('25 min')
    expect(formatFocusDuration(3660)).toBe('1 h 01 min')
    expect(formatFocusDuration(45)).toBe('45 sec')
  })

  it('validates paired inclusive custom dates and the 366-day analytics limit', () => {
    expect(validateLocalDateRange('', '2026-01-01')).toContain('both')
    expect(validateLocalDateRange('2026-02-02', '2026-02-01')).toContain('on or before')
    expect(validateLocalDateRange('2025-01-01', '2026-01-01')).toBeNull()
    expect(validateLocalDateRange('2024-12-31', '2026-01-01')).toContain('366 days')
  })
})
