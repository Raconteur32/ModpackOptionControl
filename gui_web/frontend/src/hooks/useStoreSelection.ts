// Adaptateur BulkSelection adossé au store (design D3/D4) : la sélection d'un
// panneau React vit dans la slice ui (source de vérité unique, purge par
// existence au reload), pas dans un état local de composant. DataTree/DataList
// consomment l'interface BulkSelection sans savoir où vit l'état.
import { useCallback } from 'react'
import { handleSelectClick, type SelectClickEvent } from '../domain/selection'
import { useStore, type UiSlice } from '../store'
import type { BulkSelection } from './useBulkSelection'

// Champs ui portant une sélection bulk (Set de clés + ancre).
type SetField = { [K in keyof UiSlice]: UiSlice[K] extends Set<string> ? K : never }[keyof UiSlice]
type AnchorField = { [K in keyof UiSlice]: UiSlice[K] extends string | null ? K : never }[keyof UiSlice]

export function useStoreBulkSelection(setField: SetField, anchorField: AnchorField): BulkSelection {
  const selected = useStore((s) => s.ui[setField])
  const anchor = useStore((s) => s.ui[anchorField])
  const setUi = useStore((s) => s.setUi)

  const setCheckedIds = useCallback(
    (ids: string[]) => setUi(setField, new Set(ids)),
    [setField, setUi],
  )

  const clickSelect = useCallback(
    (key: string, event: SelectClickEvent, orderedKeys: string[]) => {
      const set = new Set(selected)
      const nextAnchor = handleSelectClick({ event, key, orderedKeys, selectedSet: set, anchor })
      setUi(setField, set)
      setUi(anchorField, nextAnchor)
    },
    [selected, anchor, setField, anchorField, setUi],
  )

  const clear = useCallback(() => {
    setUi(setField, new Set())
    setUi(anchorField, null)
  }, [setField, anchorField, setUi])

  return { checkedIds: [...selected], setCheckedIds, anchor, clickSelect, clear }
}
