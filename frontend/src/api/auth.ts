import http from './http'
import type {
  LoginRequest,
  MeResponse,
  RegisterRequest,
  TokenResponse,
} from '@/types'

export const authApi = {
  register(body: RegisterRequest) {
    return http.post<MeResponse>('/auth/register', body)
  },
  login(body: LoginRequest) {
    return http.post<TokenResponse>('/auth/login', body)
  },
  refresh(refreshToken: string) {
    return http.post<TokenResponse>('/auth/refresh', { refreshToken })
  },
  me() {
    return http.get<MeResponse>('/auth/me')
  },
}
