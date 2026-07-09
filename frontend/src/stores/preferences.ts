import { defineStore } from 'pinia'

export type ViewMode = 'cards' | 'table'

const BOOKS_VIEW_MODE_KEY = 'booksViewMode'
const AUTHORS_VIEW_MODE_KEY = 'authorsViewMode'
const DEFAULT_VIEW_MODE: ViewMode = 'cards'

function readViewMode(key: string): ViewMode {
  const stored = localStorage.getItem(key)
  return stored === 'cards' || stored === 'table' ? stored : DEFAULT_VIEW_MODE
}

interface PreferencesState {
  booksViewMode: ViewMode
  authorsViewMode: ViewMode
}

export const usePreferencesStore = defineStore('preferences', {
  state: (): PreferencesState => ({
    booksViewMode: readViewMode(BOOKS_VIEW_MODE_KEY),
    authorsViewMode: readViewMode(AUTHORS_VIEW_MODE_KEY),
  }),
  actions: {
    setBooksViewMode(mode: ViewMode) {
      this.booksViewMode = mode
      localStorage.setItem(BOOKS_VIEW_MODE_KEY, mode)
    },
    setAuthorsViewMode(mode: ViewMode) {
      this.authorsViewMode = mode
      localStorage.setItem(AUTHORS_VIEW_MODE_KEY, mode)
    },
  },
})
