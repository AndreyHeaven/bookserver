export interface GenreNodeDto {
  id: number
  code: string
  title: string
  metaSection: string | null
  bookCount: number
  children: GenreNodeDto[]
}

export interface GenreDetailsDto {
  id: number
  code: string
  title: string
  metaSection: string | null
  parentId: number | null
  parentTitle: string | null
  children: GenreNodeDto[]
  bookCount: number
}
