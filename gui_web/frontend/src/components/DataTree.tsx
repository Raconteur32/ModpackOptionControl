// DataTree — wrapper partagé autour du TreeView Chakra (design D5).
//
// Contrôlé via `selection` (useBulkSelection) : checkedIds/setCheckedIds
// appartiennent à l'appelant. Apporte :
//  - la checkbox de nœud au pattern officiel (NodeCheckbox + Checkmark +
//    useTreeViewNodeContext — le span nu ne rend rien) ;
//  - les gestes de sélection legacy (click = select-only, ctrl = toggle,
//    shift = range dans le scope de la ligne) interceptés en capture avant
//    Zag — la sélection est entièrement contrôlée, pas de cascade Zag ;
//  - le select-all scopé (spec gui_web) : sans sélection → enfants de la
//    racine (jamais la racine seule, sauf racine feuille) ; avec sélection →
//    scope de la sélection (même parent) ;
//  - la navigation clavier WAI-ARIA du TreeView (flèches/Home/End/typeahead).
import { useMemo, useState, type ReactNode } from 'react'
import {
  Box,
  Checkmark,
  createTreeCollection,
  TreeView,
  useTreeViewNodeContext,
} from '@chakra-ui/react'
import { isSelectAllShortcut, type SelectClickEvent } from '../domain/selection'
import type { BulkSelection } from '../hooks/useBulkSelection'

interface TreeItem<T> {
  id: string
  name: string
  node: T
  children?: TreeItem<T>[]
}

// Checkbox de nœud — pattern officiel Chakra (tree-view-checkbox). Le click
// est intercepté en capture : Zag est court-circuité, la sélection relève
// uniquement du contrôleur (gestes legacy, pas de cascade implicite).
function NodeCheck(props: { onCheckClick: (e: SelectClickEvent) => void }) {
  const nodeState = useTreeViewNodeContext()
  return (
    <TreeView.NodeCheckbox
      aria-label="check node"
      onClickCapture={(e) => {
        e.preventDefault()
        e.stopPropagation()
        props.onCheckClick({ shiftKey: e.shiftKey, ctrlKey: e.ctrlKey, metaKey: e.metaKey })
      }}
    >
      <Checkmark
        size="sm"
        bg={{ base: 'bg', _checked: 'colorPalette.solid', _indeterminate: 'colorPalette.solid' }}
        checked={nodeState.checked === true}
        indeterminate={nodeState.checked === 'indeterminate'}
      />
    </TreeView.NodeCheckbox>
  )
}

export interface DataTreeProps<T> {
  items: T[]
  getId: (node: T) => string
  getName: (node: T) => string
  getChildren: (node: T) => T[] | undefined
  selection: BulkSelection
  /** Scope ordonné d'une ligne pour le shift-range (défaut : siblings même parent). */
  getScope?: (id: string, ctx: DataTreeScopeCtx) => string[]
  /** Extras de ligne (badges, menu par ligne…) rendus après le label. */
  renderRowExtras?: (node: T) => ReactNode
  /** Colonne valeur, rendue entre label et extras (feuilles uniquement). */
  renderLeafValue?: (node: T) => ReactNode
  /** Double-click sur une ligne (ouvrir un fichier, …). */
  onActivate?: (id: string) => void
  /** Id de la racine logique pour le select-all par défaut ('$' pour le diff). */
  rootId?: string
  /** Override du scope de select-all (défaut : sémantique legacy, ci-dessus). */
  selectAllScope?: (checkedIds: string[], ctx: DataTreeScopeCtx & { rootId: string }) => string[]
  defaultExpandedIds?: string[]
  testId?: string
}

export interface DataTreeScopeCtx {
  parentOf: Map<string, string>
  childrenOf: Map<string, string[]>
}

export function DataTree<T>(props: DataTreeProps<T>) {
  const rootId = props.rootId ?? '$'
  const { selection } = props
  const [expanded, setExpanded] = useState<string[]>(props.defaultExpandedIds ?? [rootId])

  const { collection, ctx } = useMemo(() => {
    const toItems = (nodes: T[]): TreeItem<T>[] =>
      nodes.map((n) => {
        const children = props.getChildren(n)
        return { id: props.getId(n), name: props.getName(n), node: n, children: children ? toItems(children) : undefined }
      })
    const items = toItems(props.items)
    const parentOf = new Map<string, string>()
    const childrenOf = new Map<string, string[]>()
    const walk = (list: TreeItem<T>[], parent: string) => {
      childrenOf.set(parent, list.map((i) => i.id))
      for (const i of list) {
        if (i.id !== 'ROOT') parentOf.set(i.id, parent)
        if (i.children) walk(i.children, i.id)
      }
    }
    walk(items, 'ROOT')
    return {
      ctx: { parentOf, childrenOf },
      collection: createTreeCollection<TreeItem<T>>({
        rootNode: { id: 'ROOT', name: '', children: items } as TreeItem<T>,
        nodeToValue: (n) => n.id,
        nodeToString: (n) => n.name,
      }),
    }
  }, [props.items, props.getId, props.getName, props.getChildren])

  const scopeOf = (id: string): string[] =>
    props.getScope?.(id, ctx) ?? ctx.childrenOf.get(ctx.parentOf.get(id) ?? 'ROOT') ?? [id]

  // Select-all scopé (spec gui_web) — intercepté en capture avant le handler
  // Zag. Sans sélection : enfants de la racine (jamais la racine seule, sauf
  // racine feuille → depth-0). Avec sélection : scope de la sélection.
  const selectAllKeys = (): string[] => {
    if (props.selectAllScope) return props.selectAllScope(selection.checkedIds, { ...ctx, rootId })
    if (selection.checkedIds.length === 0) {
      if (ctx.childrenOf.has(rootId)) return ctx.childrenOf.get(rootId)!
      // Racine feuille (fichier supprimé, remplacement atomique) : depth-0.
      return ctx.childrenOf.get('ROOT')?.includes(rootId) ? [rootId] : []
    }
    return scopeOf(selection.checkedIds[0])
  }

  return (
    <Box
      onKeyDownCapture={(e) => {
        if (isSelectAllShortcut(e)) {
          e.preventDefault()
          e.stopPropagation()
          selection.setCheckedIds(selectAllKeys())
        }
      }}
    >
      <TreeView.Root
        collection={collection}
        size="sm"
        checkedValue={selection.checkedIds}
        onCheckedChange={(d) => selection.setCheckedIds(d.checkedValue)}
        expandedValue={expanded}
        onExpandedChange={(d) => setExpanded(d.expandedValue)}
      >
        <TreeView.Tree data-testid={props.testId ?? 'data-tree'}>
          <TreeView.Node
            render={({ node, nodeState }) =>
              nodeState.isBranch ? (
                <TreeView.BranchControl onDoubleClick={() => props.onActivate?.(node.id)}>
                  <NodeCheck onCheckClick={(e) => selection.clickSelect(node.id, e, scopeOf(node.id))} />
                  <TreeView.BranchText>{node.name}</TreeView.BranchText>
                  {props.renderRowExtras?.(node.node)}
                </TreeView.BranchControl>
              ) : (
                <TreeView.Item onDoubleClick={() => props.onActivate?.(node.id)}>
                  <NodeCheck onCheckClick={(e) => selection.clickSelect(node.id, e, scopeOf(node.id))} />
                  <TreeView.ItemText>{node.name}</TreeView.ItemText>
                  {props.renderLeafValue?.(node.node)}
                  {props.renderRowExtras?.(node.node)}
                </TreeView.Item>
              )
            }
          />
        </TreeView.Tree>
      </TreeView.Root>
    </Box>
  )
}
