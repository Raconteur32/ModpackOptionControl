// Port TS de gui_web/src/test/js/select-all.test.js — le module legacy
// selection.js vit encore (panneaux legacy), celui-ci couvre le port TS.
import { describe, expect, it } from 'vitest'
import { selectAllKeys, type SelectionIndex } from '../domain/selection'

const FILE = 'config/app.json'

// Index de sélection d'un arbre racine '$' avec deux options changées :
//   scope ROOT::FILE -> [FILE::$]            (rows depth-0 : la racine seule)
//   scope FILE::$    -> [FILE::$['a'], FILE::$['b']]
function containerRootIndex(): SelectionIndex {
  const rootKey = `${FILE}::$`
  const childKeys = [`${FILE}::$['a']`, `${FILE}::$['b']`]
  return {
    scopeOf: new Map([
      [rootKey, `ROOT::${FILE}`],
      [childKeys[0], rootKey],
      [childKeys[1], rootKey],
    ]),
    siblingsByScope: new Map([
      [`ROOT::${FILE}`, [rootKey]],
      [rootKey, childKeys],
    ]),
  }
}

describe('selectAllKeys (Ctrl/Cmd+A scoping)', () => {
  it("without a selection, targets the root node's children — never the root alone", () => {
    const idx = containerRootIndex()
    expect(selectAllKeys(idx, FILE, null)).toEqual([`${FILE}::$['a']`, `${FILE}::$['b']`])
  })

  it("with a selection, keeps the selection's scope", () => {
    const idx = containerRootIndex()
    expect(selectAllKeys(idx, FILE, `${FILE}::$['a']`)).toEqual([`${FILE}::$['a']`, `${FILE}::$['b']`])
  })

  it('with the root selected, the scope is the depth-0 rows (the root alone)', () => {
    const idx = containerRootIndex()
    expect(selectAllKeys(idx, FILE, `${FILE}::$`)).toEqual([`${FILE}::$`])
  })

  it('falls back to the depth-0 scope when the root is a leaf (deleted file)', () => {
    const rootKey = `${FILE}::`
    const idx: SelectionIndex = {
      scopeOf: new Map([[rootKey, `ROOT::${FILE}`]]),
      siblingsByScope: new Map([[`ROOT::${FILE}`, [rootKey]]]),
    }
    expect(selectAllKeys(idx, FILE, null)).toEqual([rootKey])
  })
})
