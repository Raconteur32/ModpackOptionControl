import { IconButton, Menu, Portal } from '@chakra-ui/react'

export type RowAction = 'DEFAULT' | 'OVERRIDE' | 'IGNORE' | 'RESET'

// Menu d'action par ligne — le state-aware model complet arrive avec la
// migration ; le spike prouve le pattern (affordance [⋯] + menu).
export function RowMenu(props: { onAction: (action: RowAction) => void }) {
  return (
    <Menu.Root>
      <Menu.Trigger asChild>
        <IconButton
          aria-label="Actions"
          size="xs"
          variant="ghost"
          onClick={(e) => e.stopPropagation()}
        >
          ⋯
        </IconButton>
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content onClick={(e) => e.stopPropagation()}>
            {(['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET'] as const).map((a) => (
              <Menu.Item key={a} value={a} onSelect={() => props.onAction(a)}>
                {a}
              </Menu.Item>
            ))}
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  )
}

// Applique une action à une entrée (partagé staging / diff tree).
export async function applyAction(
  api: {
    draft: { add: Function; remove: Function }
    ignores: { add: Function }
  },
  action: RowAction,
  filePath: string,
  optionPath: string,
) {
  switch (action) {
    case 'DEFAULT':
    case 'OVERRIDE':
      return api.draft.add(filePath, optionPath, action)
    case 'IGNORE':
      return api.ignores.add(filePath, optionPath, 'SESSION')
    case 'RESET':
      return api.draft.remove(filePath, optionPath)
  }
}
