import http from './http'
import type { BookListDto, Page, ShareLinkDto } from '@/types'

export interface ListRequest {
  title: string
  description?: string
}

export const listsApi = {
  create(body: ListRequest) {
    return http.post<BookListDto>('/lists', body)
  },
  list(page = 0, size = 20) {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    return http.get<Page<BookListDto>>('/lists', { params })
  },
  get(id: number) {
    return http.get<BookListDto>(`/lists/${id}`)
  },
  update(id: number, body: ListRequest) {
    return http.put<BookListDto>(`/lists/${id}`, body)
  },
  remove(id: number) {
    return http.delete<void>(`/lists/${id}`)
  },
  addItem(id: number, bookId: number, position?: number) {
    return http.post<BookListDto>(`/lists/${id}/items`, { bookId, position })
  },
  removeItem(id: number, bookId: number) {
    return http.delete<BookListDto>(`/lists/${id}/items/${bookId}`)
  },
  reorder(id: number, bookIds: number[]) {
    return http.put<BookListDto>(`/lists/${id}/items/order`, bookIds)
  },
  share(id: number, regenerate = false) {
    return http.post<ShareLinkDto>(`/lists/${id}/share`, null, {
      params: { regenerate },
    })
  },
  revokeShare(id: number) {
    return http.delete<void>(`/lists/${id}/share`)
  },
}
