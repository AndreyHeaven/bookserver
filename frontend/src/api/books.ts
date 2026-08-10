import http from './http'
import type {
  BookDetailsDto,
  BookSearchQuery,
  BookSearchResponse,
  FacetCountsDto,
} from '@/types'

function buildParams(query: BookSearchQuery): URLSearchParams {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.lang) {
    for (const l of query.lang) params.append('lang', l)
  }
  if (query.year_from != null) params.set('year_from', String(query.year_from))
  if (query.year_to != null) params.set('year_to', String(query.year_to))
  if (query.author_id != null) params.set('author_id', String(query.author_id))
  if (query.genre_id) {
    for (const g of query.genre_id) params.append('genre_id', String(g))
  }
  if (query.include_subgenres != null) {
    params.set('include_subgenres', String(query.include_subgenres))
  }
  if (query.page != null) params.set('page', String(query.page))
  if (query.size != null) params.set('size', String(query.size))
  if (query.sort) params.set('sort', query.sort)
  return params
}

export const booksApi = {
  search(query: BookSearchQuery) {
    return http.get<BookSearchResponse>('/books', { params: buildParams(query) })
  },
  facets(query: BookSearchQuery) {
    return http.get<FacetCountsDto>('/books/facets', { params: buildParams(query) })
  },
  get(id: number) {
    return http.get<BookDetailsDto>(`/books/${id}`)
  },
  update(id: number, body: Pick<BookDetailsDto, 'title' | 'lang' | 'year' | 'annotation'> & { keywords: string | null }) {
    return http.put<BookDetailsDto>(`/books/${id}`, body)
  },
  downloadFile(bookId: number, fileId: number) {
    return http.get<Blob>(`/books/${bookId}/files/${fileId}`, { responseType: 'blob' })
  },
}
