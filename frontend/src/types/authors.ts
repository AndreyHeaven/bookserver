export interface AuthorCardDto {
  id: number
  lastName: string | null
  firstName: string | null
  middleName: string | null
  fullName: string
  bookCount: number
}

export type AuthorDetailsDto = AuthorCardDto

export interface AlphabetLetterDto {
  letter: string
  count: number
}
