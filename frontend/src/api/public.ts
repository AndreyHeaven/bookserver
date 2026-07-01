import http from './http'
import type { PublicBookListDto } from '@/types'

export const publicApi = {
  getList(token: string) {
    return http.get<PublicBookListDto>(`/public/lists/${token}`)
  },
  qrUrl(token: string) {
    const base = import.meta.env.VITE_API_BASE_URL
    return `${base}/public/lists/${token}/qr.png`
  },
}
