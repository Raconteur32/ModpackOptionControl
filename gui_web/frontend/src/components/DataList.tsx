// DataList — liste plate à sélection bulk (design D5), pour le staging et le
// patch history. Mêmes gestes que DataTree (click = select-only, ctrl =
// toggle, shift = range, Ctrl+A = toute la liste) et navigation clavier
// ↑/↓ + Home/End + Enter (activate). Contrôlée via `selection`
// (useBulkSelection).
import { useRef, type ReactNode } from 'react'
import { Box, Checkbox, HStack } from '@chakra-ui/react'
import { isSelectAllShortcut } from '../domain/selection'
import type { BulkSelection } from '../hooks/useBulkSelection'

export interface DataListProps<T> {
  items: T[]
  getId: (item: T) => string
  selection: BulkSelection
  renderRow: (item: T) => ReactNode
  /** Double-click / Enter sur une ligne. */
  onActivate?: (id: string) => void
  testId?: string
}

export function DataList<T>(props: DataListProps<T>) {
  const { selection } = props
  const rowRefs = useRef(new Map<string, HTMLDivElement>())
  const ids = props.items.map(props.getId)

  const focusRow = (id: string) => rowRefs.current.get(id)?.focus()

  const onRowKeyDown = (id: string, index: number, e: React.KeyboardEvent) => {
    if (isSelectAllShortcut(e)) {
      e.preventDefault()
      selection.setCheckedIds([...ids])
      return
    }
    if (e.key === 'ArrowDown' && index < ids.length - 1) { e.preventDefault(); focusRow(ids[index + 1]) }
    else if (e.key === 'ArrowUp' && index > 0) { e.preventDefault(); focusRow(ids[index - 1]) }
    else if (e.key === 'Home') { e.preventDefault(); focusRow(ids[0]) }
    else if (e.key === 'End') { e.preventDefault(); focusRow(ids[ids.length - 1]) }
    else if (e.key === 'Enter') { e.preventDefault(); props.onActivate?.(id) }
  }

  return (
    <Box data-testid={props.testId ?? 'data-list'} role="listbox" aria-multiselectable>
      {props.items.map((item, index) => {
        const id = props.getId(item)
        const checked = selection.checkedIds.includes(id)
        return (
          <HStack
            key={id}
            ref={(el: HTMLDivElement | null) => {
              if (el) rowRefs.current.set(id, el)
              else rowRefs.current.delete(id)
            }}
            role="option"
            aria-selected={checked}
            tabIndex={0}
            gap="2"
            px="2"
            py="1"
            borderRadius="sm"
            bg={checked ? 'bg.emphasized' : undefined}
            _hover={{ bg: checked ? 'bg.emphasized' : 'bg.subtle' }}
            onKeyDown={(e) => onRowKeyDown(id, index, e)}
            onDoubleClick={() => props.onActivate?.(id)}
          >
            <Checkbox.Root
              checked={checked}
              aria-label={`select ${id}`}
              onClickCapture={(e) => {
                e.preventDefault()
                e.stopPropagation()
                selection.clickSelect(id, { shiftKey: e.shiftKey, ctrlKey: e.ctrlKey, metaKey: e.metaKey }, ids)
              }}
            >
              <Checkbox.HiddenInput />
              <Checkbox.Control />
            </Checkbox.Root>
            {props.renderRow(item)}
          </HStack>
        )
      })}
    </Box>
  )
}
