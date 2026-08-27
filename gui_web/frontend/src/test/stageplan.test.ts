// Détection des warnings stage-over-ignore / replace (task 3.6) — port TS du
// plan de confirmation d'actions.js (flow §5).
import { describe, expect, it } from 'vitest'
import { planBulkStage, planStage, type StagePlanData } from '../domain/stagePlan'

const FILE = 'config/app.json'

const emptyData = (): StagePlanData => ({ draftEntries: [], ignores: [], recompIgnores: [] })

describe('planStage — stage-over-ignore warnings', () => {
  it('warns when staging over an active ignore rule', () => {
    const data = emptyData()
    data.ignores = [{ filePath: FILE, optionPath: "$['a']", kind: 'PERMANENT', targetValue: null }]
    const plan = planStage(data, FILE, "$['a']", 'DEFAULT')
    expect(plan.effects).toEqual([
      { title: `${FILE} › $['a']`, detail: 'Will be un-ignored (active Permanent ignore)' },
    ])
    expect(plan.existingIgnore?.kind).toBe('PERMANENT')
  })

  it('warns when staging over a recomposition ignore', () => {
    const data = emptyData()
    data.recompIgnores = [{ filePath: FILE, optionPath: "$['a']" }]
    const plan = planStage(data, FILE, "$['a']", 'OVERRIDE')
    expect(plan.effects).toEqual([
      { title: `${FILE} › $['a']`, detail: 'Will be un-ignored (recomposition ignore)' },
    ])
    expect(plan.hasRecompIgnore).toBe(true)
  })

  it('warns when a staged child entry would be replaced', () => {
    const data = emptyData()
    data.draftEntries = [{ filePath: FILE, optionPath: "$['a']['x']", mode: 'DEFAULT', kind: 'VALUE' }]
    const plan = planStage(data, FILE, "$['a']", 'OVERRIDE')
    expect(plan.effects[0].detail).toContain('Will replace child entry')
    expect(plan.toRemove).toHaveLength(1)
  })

  it('warns when a staged parent entry would be replaced', () => {
    const data = emptyData()
    data.draftEntries = [{ filePath: FILE, optionPath: "$['a']", mode: 'OVERRIDE', kind: 'VALUE' }]
    const plan = planStage(data, FILE, "$['a']['x']", 'DEFAULT')
    expect(plan.effects[0].detail).toContain('Will replace parent entry')
    expect(plan.toRemove).toHaveLength(1)
  })

  it('stays silent for an exact re-stage of the same path (mode switch)', () => {
    const data = emptyData()
    data.draftEntries = [{ filePath: FILE, optionPath: "$['a']", mode: 'DEFAULT', kind: 'VALUE' }]
    const plan = planStage(data, FILE, "$['a']", 'OVERRIDE')
    expect(plan.effects).toEqual([])
    expect(plan.toRemove).toEqual([])
  })
})

describe('planBulkStage', () => {
  it('de-duplicates entries to remove shared by two selected siblings', () => {
    const data = emptyData()
    // Les deux cibles sont descendants du même draft parent → un seul retrait.
    data.draftEntries = [{ filePath: FILE, optionPath: "$['a']", mode: 'DEFAULT', kind: 'VALUE' }]
    const plan = planBulkStage(data, [
      { filePath: FILE, optionPath: "$['a']['x']" },
      { filePath: FILE, optionPath: "$['a']['y']" },
    ], 'OVERRIDE')
    expect(plan.toRemove).toHaveLength(1)
    expect(plan.effects).toHaveLength(2)
  })
})
