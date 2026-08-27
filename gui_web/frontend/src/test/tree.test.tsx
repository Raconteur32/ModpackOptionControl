import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { DiffTreeView } from '../panels/DiffTreeView'
import type { DiffNode } from '../types'

// Arbre représentatif : racine '$' + 2 branches + 1 leaf (miroir du fake)
const TREE: DiffNode[] = [
  {
    path: '$',
    label: '$',
    kind: 'CHANGED',
    hasChildren: true,
    children: [
      {
        path: "$['graphics']",
        label: 'graphics',
        kind: 'CHANGED',
        hasChildren: true,
        children: [
          {
            path: "$['graphics']['maxFps']",
            label: 'maxFps',
            kind: 'CHANGED',
            oldValue: 60,
            newValue: 120,
            hasChildren: false,
            children: [],
          },
          {
            path: "$['graphics']['vsync']",
            label: 'vsync',
            kind: 'CHANGED',
            oldValue: false,
            newValue: true,
            hasChildren: false,
            children: [],
          },
        ],
      },
      {
        path: "$['sound']",
        label: 'sound',
        kind: 'CHANGED',
        hasChildren: true,
        children: [
          {
            path: "$['sound']['master']",
            label: 'master',
            kind: 'CHANGED',
            oldValue: 1,
            newValue: 0.8,
            hasChildren: false,
            children: [],
          },
        ],
      },
      {
        path: "$['playerName']",
        label: 'playerName',
        kind: 'NEW',
        newValue: 'Steve',
        hasChildren: false,
        children: [],
      },
    ],
  },
]

function renderTree() {
  return render(
    <ChakraProvider value={defaultSystem}>
      <DiffTreeView filePath="config/app.json" tree={TREE} />
    </ChakraProvider>,
  )
}

// Cible focusable/clavier d'un nœud : data-part=item pour les feuilles,
// data-part=branch-control pour les branches (le div data-part=branch porte
// role=treeitem mais n'a pas de tabindex — c'est le control qui prend le focus).
const controlOf = (label: string) =>
  screen.getByText(label).closest('[data-part=branch-control],[data-part=item]') as HTMLElement

const focusedValue = () => (document.activeElement as HTMLElement)?.getAttribute?.('data-value')

// NB: user-event pour TOUTES les interactions Zag (clics ET clavier) —
// fireEvent.keyDown/click ne déclenche pas les handlers des machines Zag en
// jsdom. Notre propre handler Ctrl+A (capture phase) reçoit les vrais events
// de user-event sans problème.

describe('DiffTree — interaction model (spike validation)', () => {
  it('renders root children visible, grandchildren collapsed by default', () => {
    renderTree()
    // '$' est expanded par défaut : ses 3 enfants visibles, pas les petits-enfants.
    // Zag garde les branches collapsées dans le DOM (attribut hidden) — on
    // asserte la visibilité, pas la présence.
    expect(screen.getByText('graphics')).toBeVisible()
    expect(screen.getByText('sound')).toBeVisible()
    expect(screen.getByText('playerName')).toBeVisible()
    expect(screen.getByText('maxFps')).not.toBeVisible()
  })

  it('arrow keys move focus, ArrowRight expands a branch', async () => {
    const user = userEvent.setup()
    renderTree()
    controlOf('$').focus()
    expect(focusedValue()).toBe('$')

    await user.keyboard('{ArrowDown}')
    expect(focusedValue()).toBe("$['graphics']")

    // ArrowRight expande graphics → maxFps devient visible (l'attribut hidden
    // est retiré de façon asynchrone — animation du collapsible)
    await user.keyboard('{ArrowRight}')
    await waitFor(() => expect(screen.getByText('maxFps')).toBeVisible())

    // ArrowDown navigue dans les enfants expandus
    await user.keyboard('{ArrowDown}')
    expect(focusedValue()).toBe("$['graphics']['maxFps']")
  })

  it('typeahead jumps to matching node', async () => {
    const user = userEvent.setup()
    renderTree()
    controlOf('$').focus()
    await user.keyboard('s')
    expect(focusedValue()).toBe("$['sound']")
  })

  it('Ctrl+A with nothing checked checks the root children, never the root', async () => {
    const user = userEvent.setup()
    renderTree()
    controlOf('$').focus()
    await user.keyboard('{Control>}a{/Control}')
    // enfants de '$' : graphics, sound, playerName — '$' lui-même exclu
    expect(screen.getByTestId('checked-count')).toHaveTextContent('3 checked')
  })

  it('Ctrl+A with a checked row extends to its scope (same parent)', async () => {
    const user = userEvent.setup()
    renderTree()
    // Expand graphics, check maxFps, puis Ctrl+A → scope = enfants de graphics
    controlOf('graphics').focus()
    await user.keyboard('{ArrowRight}')
    await waitFor(() => expect(screen.getByText('maxFps')).toBeVisible())

    const maxFps = controlOf('maxFps')
    await user.click(maxFps.querySelector('[data-part="node-checkbox"]') as HTMLElement)
    expect(screen.getByTestId('checked-count')).toHaveTextContent('1 checked')

    await user.keyboard('{Control>}a{/Control}')
    // scope de maxFps = {maxFps, vsync}
    expect(screen.getByTestId('checked-count')).toHaveTextContent('2 checked')
  })
})
