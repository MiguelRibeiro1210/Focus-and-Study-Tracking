import { isDesktopShell, nativeFetch, openNativeDataFolder, saveNativeDataExport } from './nativeTransport'

export type ApplicationTheme = 'LIGHT' | 'DARK' | 'SYSTEM'

export interface TimerProfile {
  id: string
  name: string
  workSeconds: number
  shortBreakSeconds: number
  longBreakSeconds: number
  intervalsBeforeLongBreak: number
  autoStartBreaks: boolean
  autoStartWork: boolean
  isDefault: boolean
}

export interface AppSettings {
  defaultProfileId: string
  profiles: TimerProfile[]
  dailyGoalHours: number
  theme: ApplicationTheme
  notificationsEnabled: boolean
  retentionDays: number | null
}

export interface TimerProfileInput {
  name: string
  workSeconds: number
  shortBreakSeconds: number
  longBreakSeconds: number
  intervalsBeforeLongBreak: number
  autoStartBreaks: boolean
  autoStartWork: boolean
}

export interface ApplicationSettingsInput {
  dailyGoalHours: number
  theme: ApplicationTheme
  notificationsEnabled: boolean
  retentionDays: number | null
}

export type ExportFormat = 'json' | 'csv-zip'

export interface SettingsApi {
  getSettings(): Promise<AppSettings>
  createProfile(name: string): Promise<AppSettings>
  updateProfile(id: string, profile: TimerProfileInput): Promise<AppSettings>
  setDefaultProfile(id: string): Promise<AppSettings>
  deleteProfile(id: string): Promise<AppSettings | null>
  updateApplicationSettings(input: ApplicationSettingsInput): Promise<AppSettings>
  clearSessionHistory(): Promise<void>
  cleanExpiredHistory(): Promise<number>
  deleteAllData(): Promise<void>
  exportAllData(format: ExportFormat): Promise<boolean>
  openDataFolder(): Promise<void>
}

export function isAppSettings(value: unknown): value is AppSettings {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<AppSettings>
  return typeof candidate.defaultProfileId === 'string'
    && Array.isArray(candidate.profiles)
    && candidate.profiles.every((profile) => typeof profile.id === 'string'
      && typeof profile.name === 'string'
      && Number.isInteger(profile.workSeconds)
      && Number.isInteger(profile.shortBreakSeconds)
      && Number.isInteger(profile.longBreakSeconds)
      && Number.isInteger(profile.intervalsBeforeLongBreak)
      && typeof profile.autoStartBreaks === 'boolean'
      && typeof profile.autoStartWork === 'boolean'
      && typeof profile.isDefault === 'boolean')
    && Number.isInteger(candidate.dailyGoalHours)
    && ['LIGHT', 'DARK', 'SYSTEM'].includes(String(candidate.theme))
    && typeof candidate.notificationsEnabled === 'boolean'
    && (candidate.retentionDays === null || Number.isInteger(candidate.retentionDays))
}

export function createSettingsApi(fetcher?: typeof fetch, basePath = '/api/v1'): SettingsApi {
  const requestFetch = fetcher ?? (isDesktopShell() ? nativeFetch : fetch)

  async function request(path: string, init: RequestInit = {}): Promise<unknown> {
    const method = init.method ?? 'GET'
    let response: Response
    try {
      response = await requestFetch(`${basePath}${path}`, {
        ...init,
        headers: { Accept: 'application/json', ...init.headers },
      })
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'The local API request failed.'
      throw new Error(`${method} ${path} failed: ${message}`, { cause })
    }
    if (response.status === 204) return undefined
    let body: unknown
    try { body = await response.json() } catch (cause) {
      throw new Error(response.ok
        ? `${method} ${path} returned invalid JSON.`
        : `${method} ${path} failed with HTTP ${response.status}.`, { cause })
    }
    if (!response.ok) {
      const error = body as { message?: unknown } | null
      const message = typeof error?.message === 'string' ? error.message : 'The local API request failed.'
      throw new Error(`${message} (HTTP ${response.status}: ${method} ${path})`)
    }
    return body
  }

  async function settings(path: string, init: RequestInit = {}): Promise<AppSettings> {
    const response = await request(path, init)
    if (!isAppSettings(response)) throw new Error('The local API returned invalid settings.')
    return response
  }

  async function exportAllData(format: ExportFormat): Promise<boolean> {
    if (isDesktopShell()) return saveNativeDataExport(format)
    const response = await requestFetch(`${basePath}/settings/export?format=${format}`, {
      method: 'GET', headers: { Accept: format === 'json' ? 'application/json' : 'application/zip' },
    })
    if (!response.ok) throw new Error(`The export failed (${response.status}).`)
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = format === 'json' ? 'focusdesktop-export.json' : 'focusdesktop-export.zip'
    link.click()
    URL.revokeObjectURL(link.href)
    return true
  }

  return {
    getSettings: () => settings('/settings'),
    createProfile: (name) => settings('/settings/profiles', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }),
    }),
    updateProfile: (id, profile) => settings(`/settings/profiles/${encodeURIComponent(id)}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profile),
    }),
    setDefaultProfile: (id) => settings('/settings/profiles/default', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profileId: id }),
    }),
    deleteProfile: async (id) => {
      await request(`/settings/profiles/${encodeURIComponent(id)}`, { method: 'DELETE' })
      return null
    },
    updateApplicationSettings: (input) => settings('/settings/application', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }),
    clearSessionHistory: async () => { await request('/sessions/history', { method: 'DELETE' }) },
    cleanExpiredHistory: async () => {
      const result = await request('/sessions/history/expired', { method: 'DELETE' }) as { deletedSessions?: unknown }
      if (!Number.isInteger(result?.deletedSessions)) throw new Error('The API returned an invalid cleanup result.')
      return result.deletedSessions as number
    },
    deleteAllData: async () => {
      await request('/settings/data', {
        method: 'DELETE', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ confirmation: 'DELETE_ALL_DATA' }),
      })
    },
    exportAllData,
    openDataFolder: async () => {
      if (!isDesktopShell()) throw new Error('The data folder is available in the desktop app.')
      await openNativeDataFolder()
    },
  }
}

export const settingsApi = createSettingsApi()
