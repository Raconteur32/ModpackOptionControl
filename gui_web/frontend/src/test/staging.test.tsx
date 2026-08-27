// Tests du panneau staging React (task 4.1) — parité avec staging.js legacy :
// titre, provenance, switching de mode par ligne et en bulk, retrait direct,
// boutons de session et dialogs. Interactions via user-event (machines Zag).
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { StagingPanel } from '../panels/StagingPanel'
import { resetStoreForTests, useStore } from '../store'
import { entryKey, type DraftEntry, type RecompState } from '../types'

const E1: DraftEntry = { filePath: 'config/app.json', optionPath: "$['a']", mode: 'OVERRIDE', kind: 'VALUE' }
const E2: DraftEntry = { filePath: 'config/app.json', optionPath: "$['b']", mode: 'DEFAULT', kind: 'VALUE' }

const AMEND: RecompState = { isAmend: true, rangeStart: 1, rangeEnd: 1, unresolvedConflicts: [] }
const RECOMP: RecompState = { isAmend: false, rangeStart: 0, rangeEnd: 1, unresolvedConflicts: [] }

function seed(data: Partial<ReturnType<typeof useStore.getState>['data']>) {
  useStore.setState((s) => ({ data: { ...s.data, ...data } }))
}

function stubActions() {
  const stubs = {
    setDraftEntriesMode: vi.fn(),
    removeDraftEntries: vi.fn(),
    createPatch: vi.fn(),
    startAmendFromStaging: vi.fn(),
    finalizeSession: vi.fn(),
    cancelSession: vi.fn(),
    requestFocus: vi.fn(),
  }
  useStore.setState(stubs)
  return stubs
}

function renderPanel() {
  return render(
    <ChakraProvider value={defaultSystem}>
      <StagingPanel />
    </ChakraProvider>,
  )
}

beforeEach(() => {
  resetStoreForTests()
})

describe('StagingPanel', () => {
  it('affiche le titre et l\'état vide sans entries', () => {
    renderPanel()
    expect(screen.getByText('STAGING — 0 entries')).toBeInTheDocument()
    expect(screen.getByText('No entries staged.')).toBeInTheDocument()
  })

  it('affiche les tags de provenance selon le mode (RECOMPOSITION vs AMEND)', () => {
    seed({
      recomp: RECOMP,
      draftEntries: [{ ...E1, source: 'base' }],
      patches: [{ name: 'base', entryCount: 1 }, { name: 'fix', entryCount: 2 }],
    })
    const { unmount } = renderPanel()
    expect(screen.getByText('[source: base]')).toBeInTheDocument()
    unmount()

    resetStoreForTests()
    seed({ recomp: AMEND, draftEntries: [{ ...E1, source: 'amend' }, { ...E2, source: 'fix' }], patches: [{ name: 'a', entryCount: 1 }, { name: 'fix', entryCount: 2 }] })
    renderPanel()
    expect(screen.getByText('[new]')).toBeInTheDocument()
    expect(screen.getByText('[patch]')).toBeInTheDocument()
  })

  it('le menu de mode d\'une ligne dispatche setDraftEntriesMode / RESET → removeDraftEntries', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ draftEntries: [E1] })
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'OVERRIDE' }))
    await user.click(await screen.findByRole('menuitem', { name: 'DEFAULT' }))
    expect(stubs.setDraftEntriesMode).toHaveBeenCalledWith(
      [{ filePath: E1.filePath, optionPath: E1.optionPath }],
      'DEFAULT',
    )

    await user.click(screen.getByRole('button', { name: 'OVERRIDE' }))
    await user.click(await screen.findByRole('menuitem', { name: 'RESET' }))
    expect(stubs.removeDraftEntries).toHaveBeenCalledWith([{ filePath: E1.filePath, optionPath: E1.optionPath }])
  })

  it('le bouton × retire l\'entry sans confirmation', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ draftEntries: [E1] })
    renderPanel()
    await user.click(screen.getByRole('button', { name: `remove ${E1.filePath} ${E1.optionPath}` }))
    expect(stubs.removeDraftEntries).toHaveBeenCalledWith([{ filePath: E1.filePath, optionPath: E1.optionPath }])
  })

  it('la bulk bar apparaît à ≥ 2 sélectionnées et applique le mode à tout le lot', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ draftEntries: [E1, E2] })
    renderPanel()
    expect(screen.queryByText(/selected/)).not.toBeInTheDocument()

    await user.click(screen.getByLabelText(`select ${entryKey(E1.filePath, E1.optionPath)}`))
    // 1 seule sélectionnée : pas de bulk bar (seuil legacy = 2)
    expect(screen.queryByText(/selected/)).not.toBeInTheDocument()
    // ctrl+click = toggle additif (click plain = select-only, flow §6)
    await user.keyboard('{Control>}')
    await user.click(screen.getByLabelText(`select ${entryKey(E2.filePath, E2.optionPath)}`))
    await user.keyboard('{/Control}')
    expect(screen.getByText('2 selected')).toBeInTheDocument()
    // Modes différents → état MIXED
    expect(screen.getByRole('button', { name: 'MIXED' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'MIXED' }))
    await user.click(await screen.findByRole('menuitem', { name: 'OVERRIDE' }))
    expect(stubs.setDraftEntriesMode).toHaveBeenCalledWith(
      [
        { filePath: E1.filePath, optionPath: E1.optionPath },
        { filePath: E2.filePath, optionPath: E2.optionPath },
      ],
      'OVERRIDE',
    )

    await user.click(screen.getByRole('button', { name: 'Remove' }))
    expect(stubs.removeDraftEntries).toHaveBeenCalledWith([
      { filePath: E1.filePath, optionPath: E1.optionPath },
      { filePath: E2.filePath, optionPath: E2.optionPath },
    ])
  })

  it('NEW_PATCH : Create désactivé sans entries, Amend désactivé sans patch existant', () => {
    seed({ draftEntries: [], patches: [] })
    renderPanel()
    expect(screen.getByRole('button', { name: 'Create New Patch' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Amend' })).toBeDisabled()
  })

  it('Create New Patch : saisie du nom puis confirmation', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ draftEntries: [E1], patches: [{ name: 'base', entryCount: 3 }] })
    renderPanel()
    await user.click(screen.getByRole('button', { name: 'Create New Patch' }))
    const input = await screen.findByRole('textbox')
    expect(screen.getByText(/1 entry · Last patch: base/)).toBeInTheDocument()
    await user.type(input, 'my-patch')
    await user.click(screen.getByRole('button', { name: 'Create' }))
    expect(stubs.createPatch).toHaveBeenCalledWith('my-patch')
  })

  it('AMEND : Finalize bloqué tant que des conflits sont non résolus', () => {
    seed({
      recomp: { ...AMEND, unresolvedConflicts: [{ filePath: 'f', optionPath: '$' }] },
      draftEntries: [E1],
      patches: [{ name: 'a', entryCount: 1 }, { name: 'fix', entryCount: 2 }],
    })
    renderPanel()
    expect(screen.getByRole('button', { name: 'Finalize' })).toBeDisabled()
    expect(screen.getByText(/⚠ 1 unresolved conflict/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()
  })

  it('AMEND : Finalize pré-rempli avec le nom du patch, Cancel du dialog discard la session', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ recomp: AMEND, draftEntries: [E1, E2], patches: [{ name: 'a', entryCount: 1 }, { name: 'fix', entryCount: 2 }] })
    renderPanel()
    await user.click(screen.getByRole('button', { name: 'Finalize' }))
    const input = (await screen.findByRole('textbox')) as HTMLInputElement
    expect(input.value).toBe('fix')
    expect(screen.getByText('2 entries (was 2)')).toBeInTheDocument()
    await user.clear(input)
    await user.type(input, 'fix-v2')
    await user.click(screen.getByRole('button', { name: 'Confirm Amend' }))
    expect(stubs.finalizeSession).toHaveBeenCalledWith('fix-v2')

    // Le Cancel du dialog finalize = discard de session (flow §12b)
    await user.click(screen.getByRole('button', { name: 'Finalize' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel' }))
    expect(stubs.cancelSession).toHaveBeenCalled()
  })

  it('Cancel session (bouton d\'action) demande confirmation avant discard', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ recomp: RECOMP, draftEntries: [E1], patches: [{ name: 'a', entryCount: 1 }, { name: 'b', entryCount: 2 }] })
    renderPanel()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(await screen.findByText(/will be discarded/)).toBeInTheDocument()
    expect(stubs.cancelSession).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Discard session' }))
    expect(stubs.cancelSession).toHaveBeenCalled()
  })

  it('clic sur le label d\'une entry demande le focus dans le diff (focusRequest)', async () => {
    const user = userEvent.setup()
    const stubs = stubActions()
    seed({ draftEntries: [E1] })
    renderPanel()
    await user.click(screen.getByText(`${E1.filePath} ${E1.optionPath}`))
    expect(stubs.requestFocus).toHaveBeenCalledWith(E1.filePath, E1.optionPath)
  })
})
