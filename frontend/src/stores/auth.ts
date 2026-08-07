import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { LoginRequest, MeResponse, RegisterRequest } from '@/types'

const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

interface AuthState {
  user: MeResponse | null
  accessToken: string | null
  refreshToken: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    accessToken: localStorage.getItem(ACCESS_KEY),
    refreshToken: localStorage.getItem(REFRESH_KEY),
  }),
  getters: {
    isAuthenticated: (state): boolean => !!state.accessToken,
    isAdmin: (state): boolean => state.user?.roles.includes('ROLE_ADMIN') ?? false,
  },
  actions: {
    setTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      localStorage.setItem(ACCESS_KEY, accessToken)
      localStorage.setItem(REFRESH_KEY, refreshToken)
    },
    async login(body: LoginRequest) {
      const { data } = await authApi.login(body)
      this.setTokens(data.accessToken, data.refreshToken)
      await this.fetchMe()
    },
    async register(body: RegisterRequest) {
      await authApi.register(body)
      await this.login({ username: body.username, password: body.password })
    },
    async refresh(): Promise<boolean> {
      if (!this.refreshToken) return false
      try {
        const { data } = await authApi.refresh(this.refreshToken)
        this.setTokens(data.accessToken, data.refreshToken)
        return true
      } catch {
        return false
      }
    },
    async fetchMe() {
      const { data } = await authApi.me()
      this.user = data
    },
    logout() {
      this.user = null
      this.accessToken = null
      this.refreshToken = null
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
    },
  },
})
