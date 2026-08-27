// Port TS des tests menuItemsFor/displayFor/resolveRowState de
// gui_web/src/test/js/dropdown-reset.test.js — données injectées au lieu de
// l'état global legacy.
import { describe, expect, it } from 'vitest'
import { displayFor, menuItemsFor, resolveRowState, type RowStateData } from '../domain/rowState'

const FILE = 'config/app.json'
const OPT = "$['a']"

const emptyData = (): RowStateData => ({ draftEntries: [], ignores: [], recompIgnores: [] })

describe('menuItemsFor (state → actions)', () => {
  it('offers the three staging actions when UNSTAGED', () => {
    expect(menuItemsFor('UNSTAGED')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE'])
  })
  it('adds RESET when staged', () => {
    expect(menuItemsFor('DEFAULTED')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET'])
    expect(menuItemsFor('OVERRIDDEN')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET'])
  })
  it('offers only RESET when IGNORED', () => {
    expect(menuItemsFor('IGNORED')).toEqual(['RESET'])
  })
})

describe('displayFor (state → button label)', () => {
  it('renders UNSTAGED empty', () => {
    expect(displayFor('UNSTAGED')).toEqual({ cls: '', label: '' })
  })
  it('keeps the legacy DEFAULT/OVERRIDE/IGNORE labels', () => {
    expect(displayFor('DEFAULTED').label).toBe('DEFAULT')
    expect(displayFor('OVERRIDDEN').label).toBe('OVERRIDE')
    expect(displayFor('IGNORED').label).toContain('IGNORE')
  })
})

describe('resolveRowState', () => {
  it('maps staged entries to DEFAULTED/OVERRIDDEN', () => {
    const data = emptyData()
    data.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT', kind: 'VALUE' }]
    expect(resolveRowState(data, FILE, OPT).state).toBe('DEFAULTED')
    data.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE', kind: 'VALUE' }]
    expect(resolveRowState(data, FILE, OPT).state).toBe('OVERRIDDEN')
  })

  it('maps ignore entries to IGNORED with their kind', () => {
    const data = emptyData()
    data.ignores = [{ filePath: FILE, optionPath: OPT, kind: 'SESSION', targetValue: null }]
    expect(resolveRowState(data, FILE, OPT)).toEqual({ state: 'IGNORED', ignoreKind: 'SESSION' })
  })

  it('applies VALUE ignores only when the current value matches targetValue', () => {
    const data = emptyData()
    data.ignores = [{ filePath: FILE, optionPath: OPT, kind: 'VALUE', targetValue: '1' }]
    expect(resolveRowState(data, FILE, OPT, 1).state).toBe('IGNORED') // numeric-aware "1" vs 1
    expect(resolveRowState(data, FILE, OPT, 2).state).toBe('UNSTAGED')
  })

  it('treats VALUE ignores conservatively when the value is unknown', () => {
    const data = emptyData()
    data.ignores = [{ filePath: FILE, optionPath: OPT, kind: 'VALUE', targetValue: '1' }]
    expect(resolveRowState(data, FILE, OPT).state).toBe('IGNORED') // newValue undefined (file tree)
  })

  it('maps recomposition ignores to IGNORED/RECOMP', () => {
    const data = emptyData()
    data.recompIgnores = [{ filePath: FILE, optionPath: OPT }]
    expect(resolveRowState(data, FILE, OPT)).toEqual({ state: 'IGNORED', ignoreKind: 'RECOMP' })
  })

  it('staged wins over ignored', () => {
    const data = emptyData()
    data.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE', kind: 'VALUE' }]
    data.recompIgnores = [{ filePath: FILE, optionPath: OPT }]
    expect(resolveRowState(data, FILE, OPT).state).toBe('OVERRIDDEN')
  })
})
