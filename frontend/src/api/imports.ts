import http from './http'
import type { ImportJobDto, ImportJobRequest, Page } from '@/types'

export const importsApi = {
  create(body: ImportJobRequest) {
    return http.post<ImportJobDto>('/imports', body)
  },
  list(page = 0, size = 20) {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    return http.get<Page<ImportJobDto>>('/imports', { params })
  },
  get(id: number) {
    return http.get<ImportJobDto>(`/imports/${id}`)
  },
}
