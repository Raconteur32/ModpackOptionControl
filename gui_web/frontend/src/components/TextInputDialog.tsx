// TextInputDialog — saisie d'une valeur unique avec confirmation (remplace
// showTextInputDialog de dialogs.js legacy) : Create New Patch, Finalize
// amend/recomposition. La valeur non vide est exigée (parité legacy) ; le
// bouton Confirm est désactivé tant que l'input est vide.
//
// `onCancel` remplace la fermeture simple : pour les dialogs de finalize, le
// Cancel legacy discard la session entière (flow §12b/§12c) — il ne ferme pas
// juste la popup. Fermeture par backdrop/Escape = même chemin que Cancel.
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, Dialog, HStack, Input, Portal, Text } from '@chakra-ui/react'

export interface TextInputDialogProps {
  open: boolean
  onClose: () => void
  title: string
  label: string
  defaultValue?: string
  /** Ligne de contexte sous le titre (ex. "3 entries · Last patch: base"). */
  meta?: ReactNode
  confirmLabel: string
  onConfirm: (value: string) => void
  /** Si absent, Cancel = onClose simple. */
  onCancel?: () => void
}

export function TextInputDialog(props: TextInputDialogProps) {
  const [value, setValue] = useState(props.defaultValue ?? '')
  const inputRef = useRef<HTMLInputElement>(null)

  // Réinitialise la valeur à chaque ouverture (le composant reste monté).
  useEffect(() => {
    if (props.open) setValue(props.defaultValue ?? '')
  }, [props.open, props.defaultValue])

  const cancel = () => {
    if (props.onCancel) props.onCancel()
    props.onClose()
  }
  const confirm = () => {
    const v = value.trim()
    if (!v) return
    props.onConfirm(v)
    props.onClose()
  }

  return (
    <Dialog.Root
      open={props.open}
      onOpenChange={(d) => {
        if (!d.open) cancel()
      }}
      initialFocusEl={() => inputRef.current}
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content>
            <Dialog.Header>
              <Dialog.Title>{props.title}</Dialog.Title>
            </Dialog.Header>
            <Dialog.Body>
              {props.meta && (
                <Text fontSize="sm" color="fg.muted" mb="3">
                  {props.meta}
                </Text>
              )}
              <Text fontSize="sm" mb="1">
                {props.label}
              </Text>
              <Input
                ref={inputRef}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') confirm()
                }}
              />
            </Dialog.Body>
            <Dialog.Footer>
              <HStack gap="2">
                <Button variant="outline" onClick={cancel}>
                  Cancel
                </Button>
                <Button colorPalette="blue" disabled={!value.trim()} onClick={confirm}>
                  {props.confirmLabel}
                </Button>
              </HStack>
            </Dialog.Footer>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  )
}
