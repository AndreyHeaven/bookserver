import type { PersonBriefDto } from './common'

export interface BookListItemDto {
  bookId: number
  title: string
  authors: PersonBriefDto[]
  year: number | null
  position: number
}

export interface BookListDto {
  id: number
  title: string
  description: string | null
  createdAt: string
  items: BookListItemDto[]
}

export interface PublicBookListDto {
  title: string
  description: string | null
  items: BookListItemDto[]
}

export interface ShareLinkDto {
  token: string
  publicUrl: string
  qrPngBase64: string
}
