import { describe, it, expect } from 'vitest'
import { money, formatDate, frequencyLabel, termLabel, todayISO, firstName } from './format'

describe('format helpers', () => {
  it('formats money in rupees', () => {
    expect(money(1250)).toContain('1,250')
    expect(money(null)).toContain('0')
  })

  it('formats an ISO date without time-zone shifts', () => {
    expect(formatDate('2026-11-20')).toBe('20 Nov 2026')
    expect(formatDate(null)).toBe('-')
  })

  it('labels premium frequencies', () => {
    expect(frequencyLabel('HALF_YEARLY')).toBe('Half-yearly')
    expect(frequencyLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE')
  })

  it('shows terms in years when possible', () => {
    expect(termLabel(12)).toBe('1 year')
    expect(termLabel(240)).toBe('20 years')
    expect(termLabel(18)).toBe('18 months')
  })

  it('returns today as YYYY-MM-DD', () => {
    expect(todayISO()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('picks the first name', () => {
    expect(firstName('Aarav Sharma')).toBe('Aarav')
    expect(firstName('')).toBe('there')
  })
})
