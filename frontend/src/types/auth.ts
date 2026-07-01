export interface MeResponse {
  id: number
  username: string
  email: string | null
  roles: string[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
}

export interface RegisterRequest {
  username: string
  email?: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}
