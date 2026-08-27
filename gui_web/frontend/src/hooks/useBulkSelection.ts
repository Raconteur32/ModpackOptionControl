// Hook de sélection bulk partagé (design D5) — encapsule les gestes de
// multi-sélection (domain/selection.ts) : click plain = select-only,
// ctrl+click = toggle, shift+click = range dans le scope ordonné passé à
// l'appel, ancre persistée pour les chaînes de shift.
//
// L'état (checkedIds) vit chez l'appelant via ce hook ; DataTree/DataList le
// consomment en props contrôlées.
import { useCallback, useState } from 'react'
import { handleSelectClick, type SelectClickEvent } from '../domain/selection'

export interface BulkSelection {
  checkedIds: string[]
  /** Remplace la sélection (select-all scopé, purge, …). */
  setCheckedIds: (ids: string[]) => void
  anchor: string | null
  /**
   * Dispatch d'un click avec modificateurs sur une ligne.
   * `orderedKeys` = scope ordonné autorisé pour le shift-range (toute la liste
   * pour DataList, les siblings même-parent pour DataTree).
   */
  clickSelect: (key: string, event: SelectClickEvent, orderedKeys: string[]) => void
  clear: () => void
}

export function useBulkSelection(): BulkSelection {
  const [checkedIds, setCheckedIds] = useState<string[]>([])
  const [anchor, setAnchor] = useState<string | null>(null)

  const clickSelect = useCallback(
    (key: string, event: SelectClickEvent, orderedKeys: string[]) => {
      const set = new Set(checkedIds)
      const nextAnchor = handleSelectClick({ event, key, orderedKeys, selectedSet: set, anchor })
      setCheckedIds([...set])
      setAnchor(nextAnchor)
    },
    [checkedIds, anchor],
  )

  const clear = useCallback(() => {
    setCheckedIds([])
    setAnchor(null)
  }, [])

  return { checkedIds, setCheckedIds, anchor, clickSelect, clear }
}
