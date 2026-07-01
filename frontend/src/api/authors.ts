import http from './http'
import type {
  AlphabetLetterDto,
  AuthorCardDto,
  AuthorDetailsDto,
  BookCardDto,
  BookSearchQuery,
  Page,
} from '@/types'

export interface AuthorSearchQuery {
  q?: string
  letter?: string
  page?: number
  size?: number
  sort?: string
}

export const authorsApi = {
  list(query: AuthorSearchQuery) {
    const params = new URLSearchParams()
    if (query.q) params.set('q', query.q)
    if (query.letter) params.set('letter', query.letter)
    if (query.page != null) params.set('page', String(query.page))
    if (query.size != null) params.set('size', String(query.size))
    if (query.sort) params.set('sort', query.sort)
    return http.get<Page<AuthorCardDto>>('/authors', { params })
  },
  alphabet() {
    return http.get<AlphabetLetterDto[]>('/authors/alphabet')
  },
  get(id: number) {
    return http.get<AuthorDetailsDto>(`/authors/${id}`)
  },
  books(id: number, query: BookSearchQuery) {
    const params = new URLSearchParams()
    if (query.q) params.set('q', query.q)
    if (query.lang) params.set('lang', query.lang)
    if (query.year_from != null) params.set('year_from', String(query.year_from))
    if (query.year_to != null) params.set('year_to', String(query.year_to))
    if (query.genre_id) {
      for (const g of query.genre_id) params.append('genre_id', String(g))
    }
    if (query.page != null) params.set('page', String(query.page))
    if (query.size != null) params.set('size', String(query.size))
    if (query.sort) params.set('sort', query.sort)
    return http.get<Page<BookCardDto>>(`/authors/${id}/books`, { params })
  },
}
