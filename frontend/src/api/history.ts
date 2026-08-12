import http from './http'
export interface HistoryEntry { id: number; bookId: number; title: string; coverUrl: string; viewedAt: string }
export interface HistoryPage { content: HistoryEntry[]; page: { totalElements: number; totalPages: number; number: number; size: number } }
export const historyApi = {
  list(page = 0, size = 20) { return http.get<HistoryPage>('/history', { params: { page, size, sort: 'viewedAt,desc' } }) },
  remove(id: number) { return http.delete(`/history/${id}`) },
  clear() { return http.delete('/history') },
}
