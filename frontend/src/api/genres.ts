import http from './http'
import type {
  BookCardDto,
  GenreDetailsDto,
  GenreNodeDto,
  Page,
} from '@/types'

export interface GenreBooksQuery {
  includeSubgenres?: boolean
  q?: string
  lang?: string
  year_from?: number
  year_to?: number
  page?: number
  size?: number
  sort?: string
}

export const genresApi = {
  tree() {
    return http.get<GenreNodeDto[]>('/genres/tree')
  },
  get(id: number) {
    return http.get<GenreDetailsDto>(`/genres/${id}`)
  },
  books(id: number, query: GenreBooksQuery) {
    const params = new URLSearchParams()
    if (query.includeSubgenres != null)
      params.set('includeSubgenres', String(query.includeSubgenres))
    if (query.q) params.set('q', query.q)
    if (query.lang) params.set('lang', query.lang)
    if (query.year_from != null) params.set('year_from', String(query.year_from))
    if (query.year_to != null) params.set('year_to', String(query.year_to))
    if (query.page != null) params.set('page', String(query.page))
    if (query.size != null) params.set('size', String(query.size))
    if (query.sort) params.set('sort', query.sort)
    return http.get<Page<BookCardDto>>(`/genres/${id}/books`, { params })
  },
}
