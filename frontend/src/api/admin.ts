import http from './http'

export interface AdminUser {
  id: number
  username: string
  email: string | null
  enabled: boolean
  roles: string[]
  createdAt: string
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
  deleteUser(id: number) { return http.delete(`/admin/users/${id}`) },
  resetPassword(id: number) { return http.post<{ password: string }>(`/admin/users/${id}/reset-password`) },
  settings() { return http.get<{ registrationEnabled: boolean }>('/admin/site-settings') },
  updateSettings(registrationEnabled: boolean) {
    return http.put<{ registrationEnabled: boolean }>('/admin/site-settings', { registrationEnabled })
  },
}
