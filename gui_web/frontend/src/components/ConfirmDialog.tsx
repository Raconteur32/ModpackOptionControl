// ConfirmDialog — wrapper unique de confirmation (design D5), remplaçant les
// quatre helpers de dialogs.js legacy (showConfirmDialog, showTextInputDialog,
// showDraftConflictDialog, showDangerConfirmDialog) côté React : un tableau
// d'actions au lieu de callbacks nommés, et initialFocus sur Cancel pour les
// flux destructifs (le geste sûr est le défaut clavier).
import { useRef, type ReactNode } from 'react'
import { Button, Dialog, HStack, Portal } from '@chakra-ui/react'

export interface ConfirmAction {
  label: string
  colorPalette?: string
  variant?: 'solid' | 'outline' | 'ghost'
  onClick: () => void
}

export interface ConfirmDialogProps {
  open: boolean
  onClose: () => void
  title: ReactNode
  body: ReactNode
  actions: ConfirmAction[]
  /** Flux destructif : le focus initial va sur Cancel (pas sur l'action). */
  destructive?: boolean
}

export function ConfirmDialog(props: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null)

  return (
    <Dialog.Root
      open={props.open}
      onOpenChange={(d) => {
        if (!d.open) props.onClose()
      }}
      initialFocusEl={props.destructive ? () => cancelRef.current : undefined}
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content>
            <Dialog.Header>
              <Dialog.Title>{props.title}</Dialog.Title>
            </Dialog.Header>
            <Dialog.Body>{props.body}</Dialog.Body>
            <Dialog.Footer>
              <HStack gap="2">
                <Button ref={cancelRef} variant="outline" onClick={props.onClose}>
                  Cancel
                </Button>
                {props.actions.map((a) => (
                  <Button
                    key={a.label}
                    colorPalette={a.colorPalette}
                    variant={a.variant ?? 'solid'}
                    onClick={() => {
                      props.onClose()
                      a.onClick()
                    }}
                  >
                    {a.label}
                  </Button>
                ))}
              </HStack>
            </Dialog.Footer>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
