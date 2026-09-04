import http from './http'

export interface AdminUser {
  id: number
  username: string
  email: string | null
  enabled: boolean
  telegramUid: number | null
  roles: string[]
  createdAt: string
}

export interface SiteSettings {
  registrationEnabled: boolean
  historyRetentionDays: number
  historyMaxEntries: number
}

export interface Page<T> {
  content: T[]
  page: {
    totalElements: number
    totalPages: number
    number: number
    size: number
  }
}

export const adminApi = {
  users(q: string, page: number, size = 20) {
    return http.get<Page<AdminUser>>('/admin/users', { params: { q, page, size, sort: 'username,asc' } })
  },
  setEnabled(id: number, enabled: boolean) {
    return http.patch<AdminUser>(`/admin/users/${id}/enabled`, null, { params: { enabled } })
  },
  setTelegramUid(id: number, telegramUid: number | null) {
    return http.put<AdminUser>(`/admin/users/${id}/telegram-uid`, { telegramUid })
  },
  deleteUser(id: number) { return http.delete(`/admin/users/${id}`) },
  resetPassword(id: number) { return http.post<{ password: string }>(`/admin/users/${id}/reset-password`) },
  settings() { return http.get<SiteSettings>('/admin/site-settings') },
  updateSettings(settings: SiteSettings) {
    return http.put<SiteSettings>('/admin/site-settings', settings)
  },
  userHistory(id: number, page: number, size = 20) {
    return http.get<Page<{ id: number; bookId: number; title: string; coverUrl: string; viewedAt: string }>>(
      `/admin/users/${id}/history`, { params: { page, size, sort: 'viewedAt,desc' } },
    )
  },
}
