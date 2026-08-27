// DiffTreeView — composition diff-spécifique de DataTree (valeurs old→new
// leaf-only, badges de kind, menu par ligne). Présentationnel : les données
// sont passées en props, le fetch vit dans le parent.
import { Badge, Tag, Text } from '@chakra-ui/react'
import { DataTree } from '../components/DataTree'
import { RowMenu, applyAction } from '../components/RowMenu'
import { useBulkSelection } from '../hooks/useBulkSelection'
import { api } from '../api'
import type { DiffNode } from '../types'

const KIND_PALETTE: Record<string, string> = { NEW: 'green', CHANGED: 'yellow', DELETED: 'red' }

function fmt(v: unknown): string {
  if (v === undefined || v === null) return ''
  return typeof v === 'object' ? JSON.stringify(v) : String(v)
}

// Valeurs leaf-only (spec unify-root-option-node) : jamais sur les containers.
function ValueDiff({ diff }: { diff: DiffNode }) {
  if (diff.kind === 'NEW')
    return (
      <Text as="span" fontFamily="mono" fontSize="xs">
        {fmt(diff.newValue)}
      </Text>
    )
  if (diff.kind === 'DELETED')
    return (
      <Text as="span" fontFamily="mono" fontSize="xs" textDecoration="line-through">
        {fmt(diff.oldValue)}
      </Text>
    )
  return (
    <Text as="span" fontFamily="mono" fontSize="xs">
      <Text as="span" textDecoration="line-through" color="fg.muted">
        {fmt(diff.oldValue)}
      </Text>
      {' → '}
      {fmt(diff.newValue)}
    </Text>
  )
}

export function DiffTreeView(props: { filePath: string; tree: DiffNode[]; onRefresh?: () => void }) {
  const selection = useBulkSelection()

  return (
    <>
      <DataTree<DiffNode>
        items={props.tree}
        getId={(n) => n.path}
        getName={(n) => n.label}
        getChildren={(n) => (n.hasChildren ? n.children : undefined)}
        selection={selection}
        testId="diff-tree"
        renderLeafValue={(n) => <ValueDiff diff={n} />}
        renderRowExtras={(n) => (
          <>
            <Badge size="sm" colorPalette={KIND_PALETTE[n.kind] ?? 'gray'}>
              {n.kind}
            </Badge>
            {n.action && (
              <Tag.Root size="sm" colorPalette={n.action === 'OVERRIDE' ? 'purple' : 'blue'}>
                <Tag.Label>{n.action === 'IGNORE' ? 'IGNORED' : `${n.action}ED`}</Tag.Label>
              </Tag.Root>
            )}
            {n.unresolved && <Badge colorPalette="red">!</Badge>}
            <RowMenu
              onAction={async (a) => {
                await applyAction(api, a, props.filePath, n.path)
                props.onRefresh?.()
              }}
            />
          </>
        )}
      />
      {/* Compteur exposé pour les tests et le debug. */}
      <Text fontSize="xs" color="fg.muted" data-testid="checked-count">
        {selection.checkedIds.length} checked
      </Text>
    </>
  )
}
