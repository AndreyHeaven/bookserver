export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type ImporterType = 'inpx-zip' | 'fb2-folder'
export type ArchiveImportMode = 'importAll' | 'skipByName' | 'skipByHash'
export type ImportJobMessageLevel = 'WARNING' | 'ERROR'

export interface ImportJobMessage {
  level: ImportJobMessageLevel
  message: string
}

export interface ImportJobDto {
  id: number
  importerType: string
  sourcePath: string
  status: JobStatus
  message: string | null
  messages: ImportJobMessage[]
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  totalCount: number
  processedCount: number
}

export interface ImportJobRequest {
  type: ImporterType
  sourcePath: string
  options?: {
    stopOnError?: boolean
    archiveImportMode?: ArchiveImportMode
  }
}

export type ConversionFormat = 'fb2' | 'epub' | 'mobi' | 'pdf' | 'azw3'

export interface ConversionJobDto {
  id: number
  bookFileId: number
  sourceFormat: string
  targetFormat: string
  status: JobStatus
  message: string | null
  outputBookFileId: number | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface ConversionJobRequest {
  bookFileId: number
  targetFormat: ConversionFormat
}
