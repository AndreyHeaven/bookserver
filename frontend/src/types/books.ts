import type { PersonBriefDto } from './common'

export interface BookCardDto {
  id: number
  title: string
  authors: PersonBriefDto[]
  year: number | null
  lang: string
  fileType: string
  hasFiles: boolean
  coverUrl: string | null
}

export interface GenreBriefDto {
  id: number
  code: string
  title: string
  path: string[]
}

export interface BookSeriesDto {
  id: number
  title: string
  sequenceNumber: number | null
}

export interface BookFileDto {
  id: number
  format: string
  sizeBytes: number | null
  downloadUrl: string
}

export interface BookDetailsDto {
  id: number
  title: string
  authors: PersonBriefDto[]
  translators: PersonBriefDto[]
  year: number | null
  lang: string
  fileType: string
  fileSize: number | null
  annotation: string | null
  genres: GenreBriefDto[]
  series: BookSeriesDto[]
  files: BookFileDto[]
  coverUrl: string | null
}

export interface FacetValue {
  value: string
  count: number
}

export interface FacetYear {
  value: number
  count: number
}

export interface FacetGenre {
  id: number
  code: string
  title: string
  count: number
}

export interface FacetCountsDto {
  langs: FacetValue[]
  years: FacetYear[]
  genres: FacetGenre[]
}

export interface BookSearchResponse {
  content: BookCardDto[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  facets: FacetCountsDto
}

export interface BookSearchQuery {
  q?: string
  lang?: string[]
  year_from?: number
  year_to?: number
  genre_id?: number[]
  author_id?: number
  page?: number
  size?: number
  sort?: string
}
