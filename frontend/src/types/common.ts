export interface PageMetadata {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface Page<T> {
  content: T[]
  page: PageMetadata
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

export interface PersonBriefDto {
  id: number
  fullName: string
}
