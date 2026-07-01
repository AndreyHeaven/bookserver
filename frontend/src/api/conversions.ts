import http from './http'
import type { ConversionJobDto, ConversionJobRequest, Page } from '@/types'

export const conversionsApi = {
  create(body: ConversionJobRequest) {
    return http.post<ConversionJobDto>('/conversions', body)
  },
  list(page = 0, size = 20) {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    return http.get<Page<ConversionJobDto>>('/conversions', { params })
  },
  get(id: number) {
    return http.get<ConversionJobDto>(`/conversions/${id}`)
  },
}
