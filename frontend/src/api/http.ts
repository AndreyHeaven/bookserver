import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL

export const http: AxiosInstance = axios.create({
  baseURL,
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore()
  if (auth.accessToken && config.headers) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let refreshPromise: Promise<boolean> | null = null

http.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error) => {
    const auth = useAuthStore()
    const original = error.config as RetriableConfig | undefined

    if (
      error.response?.status === 401 &&
      original &&
      !original._retry &&
      auth.refreshToken
    ) {
      original._retry = true
      try {
        if (!refreshPromise) {
          refreshPromise = auth.refresh()
        }
        const ok = await refreshPromise
        refreshPromise = null
        if (ok) {
          if (original.headers) {
            original.headers.Authorization = `Bearer ${auth.accessToken}`
          }
          return http(original)
        }
      } catch {
        refreshPromise = null
      }
      auth.logout()
      await router.push('/login')
    }
    return Promise.reject(error)
  },
)

export default http
