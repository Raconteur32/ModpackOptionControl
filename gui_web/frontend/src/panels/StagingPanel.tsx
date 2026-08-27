// StagingPanel (flow §11, task 4.1 — panneau pilote de la migration) :
// liste des entries stagées (DataList), switching de mode par ligne et en
// bulk, provenance AMEND/RECOMPOSITION, retrait direct (× / Remove / RESET),
// boutons de session (Create New Patch / Amend / Finalize / Cancel) et leurs
// dialogs. Remplace staging.js legacy, supprimé à cette task.
import { useState } from 'react'
import { Badge, Box, Button, HStack, Menu, Portal, Text, VStack } from '@chakra-ui/react'
import { amendTargetName, currentMode, useStore } from '../store'
import { entryKey, type DraftEntry, type Mode } from '../types'
import { DataList } from '../components/DataList'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TextInputDialog } from '../components/TextInputDialog'
import { useStoreBulkSelection } from '../hooks/useStoreSelection'

type Target = { filePath: string; optionPath: string }

function targetFromKey(key: string): Target {
  const idx = key.lastIndexOf('::')
  return { filePath: key.slice(0, idx), optionPath: key.slice(idx + 2) }
}

// Provenance (flow §11 "En mode AMEND") : [patch]/[new] en AMEND (seules
// sources possibles : le patch amendé et l'amend dir), nom du patch source en
// RECOMPOSITION.
function sourceTag(entry: DraftEntry, mode: Mode): string | null {
  if (!entry.source) return null
  if (mode === 'AMEND') return entry.source === 'amend' ? '[new]' : '[patch]'
  return `[source: ${entry.source}]`
}

// Menu de mode partagé (ligne + bulk bar) : DEFAULT / OVERRIDE / RESET.
// RESET sur une entry stagée = retrait direct, sans confirmation.
function ModeMenu(props: { label: string; onSelect: (choice: 'DEFAULT' | 'OVERRIDE' | 'RESET') => void }) {
  return (
    <Menu.Root onSelect={(d) => props.onSelect(d.value as 'DEFAULT' | 'OVERRIDE' | 'RESET')}>
      <Menu.Trigger asChild>
        <Button size="xs" variant="outline">
          {props.label}
        </Button>
      </Menu.Trigger>
      <Portal>
        <Menu.Positioner>
          <Menu.Content>
            <Menu.Item value="DEFAULT">DEFAULT</Menu.Item>
            <Menu.Item value="OVERRIDE">OVERRIDE</Menu.Item>
            <Menu.Separator />
            <Menu.Item value="RESET">RESET</Menu.Item>
          </Menu.Content>
        </Menu.Positioner>
      </Portal>
    </Menu.Root>
  )
}

export function StagingPanel() {
  const entries = useStore((s) => s.data.draftEntries)
  const patches = useStore((s) => s.data.patches)
  const recomp = useStore((s) => s.data.recomp)
  const mode = useStore((s) => currentMode(s.data))
  const amendTarget = useStore((s) => amendTargetName(s.data))
  const requestFocus = useStore((s) => s.requestFocus)
  const setDraftEntriesMode = useStore((s) => s.setDraftEntriesMode)
  const removeDraftEntries = useStore((s) => s.removeDraftEntries)
  const createPatch = useStore((s) => s.createPatch)
  const startAmendFromStaging = useStore((s) => s.startAmendFromStaging)
  const finalizeSession = useStore((s) => s.finalizeSession)
  const cancelSession = useStore((s) => s.cancelSession)
  const selection = useStoreBulkSelection('selectedStaging', 'stagingAnchor')

  const [dialog, setDialog] = useState<'create' | 'finalize' | 'cancel' | null>(null)

  const unresolvedCount = recomp?.unresolvedConflicts?.length ?? 0
  const n = entries.length
  const title =
    `STAGING — ${n} ${n === 1 ? 'entry' : 'entries'}` +
    (unresolvedCount > 0 ? ` · ⚠ ${unresolvedCount} unresolved ${unresolvedCount === 1 ? 'conflict' : 'conflicts'}` : '')

  // --- Bulk bar (≥ 2 sélectionnées) ---
  const selectedTargets = selection.checkedIds.map(targetFromKey)
  const selectedModes = selection.checkedIds.map(
    (k) => entries.find((e) => entryKey(e.filePath, e.optionPath) === k)?.mode ?? null,
  )
  const bulkLabel =
    selectedModes.length > 0 && selectedModes.every((m) => m === selectedModes[0])
      ? (selectedModes[0] ?? 'MIXED')
      : 'MIXED'
  const onBulkSelect = (choice: 'DEFAULT' | 'OVERRIDE' | 'RESET') => {
    if (choice === 'RESET') void removeDraftEntries(selectedTargets)
    else void setDraftEntriesMode(selectedTargets, choice)
  }

  // --- Boutons d'action selon le mode (flow §11/§12) ---
  const inSession = mode !== 'NEW_PATCH'
  const hasEntries = n > 0
  const amendDisabledReason = !(patches.length > 0)
    ? 'Amend requires at least one existing patch'
    : !hasEntries
      ? 'Amend from staging requires at least one staged entry'
      : ''

  // --- Dialogs finalize : contenu selon AMEND / RECOMPOSITION ---
  const rangeNames =
    recomp && recomp.rangeStart != null && recomp.rangeEnd != null
      ? patches.slice(recomp.rangeStart, recomp.rangeEnd + 1).map((p) => p.name)
      : []
  const previousCount =
    mode === 'AMEND' && recomp?.rangeStart != null ? (patches[recomp.rangeStart]?.entryCount ?? 0) : 0
  const finalizeProps =
    mode === 'AMEND'
      ? {
          title: `Amend ${amendTarget ?? ''}`,
          label: 'Rename (optional):',
          defaultValue: amendTarget ?? '',
          meta: `${n} ${n === 1 ? 'entry' : 'entries'} (was ${previousCount})`,
          confirmLabel: 'Confirm Amend',
        }
      : {
          title: `Recompose ${rangeNames.join(' + ')}`,
          label: 'Name:',
          defaultValue: '',
          meta: `${n} ${n === 1 ? 'entry' : 'entries'} · Replaces: ${rangeNames.join(', ')}`,
          confirmLabel: 'Confirm',
        }
  const lastPatch = patches.length > 0 ? patches[patches.length - 1].name : null

  return (
    <VStack
      h="100%"
      align="stretch"
      gap="0"
      // Clic sur le vide du panneau = désélection (parité legacy).
      onClick={(e) => {
        if (e.target === e.currentTarget) selection.clear()
      }}
    >
      <HStack px="2" py="1" justify="space-between" flexShrink={0}>
        <Text fontWeight="bold" fontSize="sm">
          {title}
        </Text>
        <HStack gap="2">
          {inSession ? (
            <>
              <Button
                size="xs"
                colorPalette="blue"
                disabled={unresolvedCount > 0}
                title={unresolvedCount > 0 ? 'Resolve all unresolved conflicts before finalizing' : ''}
                onClick={() => setDialog('finalize')}
              >
                Finalize
              </Button>
              <Button size="xs" variant="outline" onClick={() => setDialog('cancel')}>
                Cancel
              </Button>
            </>
          ) : (
            <>
              <Button
                size="xs"
                colorPalette="blue"
                disabled={!hasEntries}
                onClick={() => setDialog('create')}
              >
                Create New Patch
              </Button>
              <Button
                size="xs"
                variant="outline"
                disabled={!!amendDisabledReason}
                title={amendDisabledReason}
                onClick={() => void startAmendFromStaging()}
              >
                Amend
              </Button>
            </>
          )}
        </HStack>
      </HStack>

      {selection.checkedIds.length >= 2 && (
        <HStack px="2" py="1" bg="bg.subtle" gap="2" flexShrink={0}>
          <Text fontSize="sm">{selection.checkedIds.length} selected</Text>
          <ModeMenu label={bulkLabel ?? 'MIXED'} onSelect={onBulkSelect} />
          <Button size="xs" variant="outline" onClick={() => void removeDraftEntries(selectedTargets)}>
            Remove
          </Button>
        </HStack>
      )}

      <Box flex="1" overflowY="auto" px="1" minH="0">
        {entries.length === 0 ? (
          <Text color="fg.muted" fontSize="sm" p="2">
            No entries staged.
          </Text>
        ) : (
          <DataList
            items={entries}
            getId={(e) => entryKey(e.filePath, e.optionPath)}
            selection={selection}
            testId="staging-list"
            renderRow={(e) => {
              const target = { filePath: e.filePath, optionPath: e.optionPath }
              const tag = sourceTag(e, mode)
              return (
                <HStack flex="1" justify="space-between" gap="2">
                  <Text
                    flex="1"
                    fontSize="sm"
                    fontFamily="mono"
                    cursor="pointer"
                    truncate
                    onClick={() => requestFocus(e.filePath, e.optionPath)}
                  >
                    {e.filePath} {e.optionPath}
                    {tag && (
                      <Badge ml="2" size="sm" variant="subtle">
                        {tag}
                      </Badge>
                    )}
                  </Text>
                  <ModeMenu
                    label={e.mode}
                    onSelect={(choice) => {
                      if (choice === 'RESET') void removeDraftEntries([target])
                      else void setDraftEntriesMode([target], choice)
                    }}
                  />
                  <Button
                    size="xs"
                    variant="ghost"
                    aria-label={`remove ${e.filePath} ${e.optionPath}`}
                    onClick={() => void removeDraftEntries([target])}
                  >
                    ×
                  </Button>
                </HStack>
              )
            }}
          />
        )}
      </Box>

      <TextInputDialog
        open={dialog === 'create'}
        onClose={() => setDialog(null)}
        title="Create new patch"
        label="Name:"
        meta={`${n} ${n === 1 ? 'entry' : 'entries'}${lastPatch ? ` · Last patch: ${lastPatch}` : ''}`}
        confirmLabel="Create"
        onConfirm={(name) => void createPatch(name)}
      />
      <TextInputDialog
        open={dialog === 'finalize'}
        onClose={() => setDialog(null)}
        title={finalizeProps.title}
        label={finalizeProps.label}
        defaultValue={finalizeProps.defaultValue}
        meta={finalizeProps.meta}
        confirmLabel={finalizeProps.confirmLabel}
        onConfirm={(name) => void finalizeSession(name)}
        // flow §12b/§12c : le Cancel du dialog finalize discard la session.
        onCancel={() => void cancelSession()}
      />
      <ConfirmDialog
        open={dialog === 'cancel'}
        onClose={() => setDialog(null)}
        title="Cancel session"
        body={
          <>
            <Text fontWeight="semibold">{mode === 'AMEND' ? 'Amend' : 'Recomposition'}</Text>
            <Text fontSize="sm">The current session and its staged entries will be discarded.</Text>
          </>
        }
        destructive
        actions={[
          {
            label: 'Discard session',
            colorPalette: 'red',
            onClick: () => void cancelSession(),
          },
        ]}
      />
    </VStack>
  )
}
