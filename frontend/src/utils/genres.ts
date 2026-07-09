import type { GenreNodeDto } from '@/types'

export interface GenreOption {
  id: number
  title: string
  path: string
}

/**
 * Flattens the genre tree into a flat list of options with a breadcrumb-like
 * `path` describing the parent chain. Used by genre multi-select autocompletes.
 */
export function flattenGenres(nodes: GenreNodeDto[], parents: string[] = []): GenreOption[] {
  const result: GenreOption[] = []
  for (const node of nodes) {
    result.push({ id: node.id, title: node.title, path: parents.join(' / ') })
    if (node.children?.length) {
      result.push(...flattenGenres(node.children, [...parents, node.title]))
    }
  }
  return result
}
