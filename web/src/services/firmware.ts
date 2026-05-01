import request from './request'

const FIRMWARE_BASE = '/file/firmware'

export interface FirmwareFile {
  fileName: string
  version?: string
  hash?: string
  size: number
  modifyTime: string | number | Date
  downloadUrl: string
}

export interface FirmwareListResponse {
  code: number
  message?: string
  files: FirmwareFile[]
  total: number
  latest?: FirmwareFile | null
}

export interface FirmwareUploadResponse {
  code: number
  message?: string
  fileName?: string
  originalName?: string
  downloadUrl?: string
  version?: string
  sha256?: string
  hash?: string
  size?: number
  uploadTime?: string
  data?: FirmwareFile
}

export function listFirmwareFiles() {
  return request.get(FIRMWARE_BASE + '/list') as unknown as Promise<FirmwareListResponse>
}

export function uploadFirmwareFile(
  file: File,
  version: string,
  onProgress?: (percent: number) => void,
) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('version', version)

  return request.post(FIRMWARE_BASE + '/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  }) as unknown as Promise<FirmwareUploadResponse>
}

export function deleteFirmwareFile(fileName: string) {
  return request.delete(FIRMWARE_BASE + '/' + encodeURIComponent(fileName)) as unknown as Promise<{
    code: number
    message?: string
  }>
}
