import { useCallback, useEffect, useState } from 'react'
import {
  settingsApi,
  isAppSettings,
  type AppSettings,
  type ApplicationSettingsInput,
  type ExportFormat,
  type SettingsApi,
  type TimerProfileInput,
} from '../api/settingsApi'
import {
  notificationPermissionPort,
  type NotificationPermissionPort,
} from '../api/nativeNotifications'

export interface AppSettingsController {
  settings: AppSettings | null
  loading: boolean
  pending: boolean
  error: string | null
  notice: string | null
  retry: () => void
  createProfile: (name: string) => Promise<void>
  updateProfile: (id: string, profile: TimerProfileInput) => Promise<void>
  setDefaultProfile: (id: string) => Promise<void>
  deleteProfile: (id: string) => Promise<void>
  updateApplicationSettings: (input: ApplicationSettingsInput) => Promise<void>
  setNotificationsEnabled: (enabled: boolean) => Promise<void>
  clearSessionHistory: () => Promise<void>
  cleanExpiredHistory: () => Promise<number>
  deleteAllData: () => Promise<void>
  exportAllData: (format: ExportFormat) => Promise<boolean>
  openDataFolder: () => Promise<void>
}

export function useAppSettings(
  api: SettingsApi = settingsApi,
  notifications: NotificationPermissionPort = notificationPermissionPort,
): AppSettingsController {
  const [settings, setSettings] = useState<AppSettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const retry = useCallback(() => {
    setLoading(true)
    setReloadKey((value) => value + 1)
  }, [])

  const perform = useCallback(async <T,>(operation: () => Promise<T>, successMessage?: string): Promise<T> => {
    setPending(true)
    setError(null)
    setNotice(null)
    try {
      const result = await operation()
      if (isAppSettings(result)) setSettings(result)
      if (successMessage) setNotice(successMessage)
      return result
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The settings change could not be saved.')
      throw cause
    } finally {
      setPending(false)
    }
  }, [])

  const reloadSettings = useCallback(async () => {
    const value = await api.getSettings()
    setSettings(value)
  }, [api])

  useEffect(() => {
    let current = true
    void api.getSettings().then(async (value) => {
      if (!current) return
      setError(null)
      if (!value.notificationsEnabled) {
        setSettings(value)
        return
      }

      try {
        const permission = await notifications.ensurePermission()
        if (!current) return
        if (permission === 'denied') {
          const saved = await api.updateApplicationSettings({
            dailyGoalHours: value.dailyGoalHours,
            theme: value.theme,
            notificationsEnabled: false,
            retentionDays: value.retentionDays,
          })
          if (current) {
            setSettings(saved)
            setNotice('Notifications are off because operating-system permission was denied.')
          }
          return
        }
      } catch (cause) {
        if (current) setNotice(cause instanceof Error
          ? `Could not check notification permission: ${cause.message}`
          : 'Could not check notification permission.')
      }
      if (current) setSettings(value)
    }).catch((cause: unknown) => {
      if (current) setError(cause instanceof Error ? cause.message : 'Could not load settings.')
    }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [api, notifications, reloadKey])

  const setNotificationsEnabled = useCallback(async (enabled: boolean) => {
    if (!settings) return
    setPending(true)
    setError(null)
    setNotice(null)
    try {
      let permission: Awaited<ReturnType<NotificationPermissionPort['ensurePermission']>> = 'granted'
      if (enabled) permission = await notifications.ensurePermission()
      if (permission === 'unsupported') {
        setNotice('System notifications are unavailable in this environment.')
        return
      }
      const allowed = enabled && permission === 'granted'
      const saved = await api.updateApplicationSettings({
        dailyGoalHours: settings.dailyGoalHours,
        theme: settings.theme,
        notificationsEnabled: allowed,
        retentionDays: settings.retentionDays,
      })
      setSettings(saved)
      setNotice(enabled && !allowed
        ? 'Notifications are off because operating-system permission was denied.'
        : allowed ? 'Notifications enabled.' : 'Notifications disabled.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The notification preference could not be saved.')
      throw cause
    } finally {
      setPending(false)
    }
  }, [api, notifications, settings])

  return {
    settings, loading, pending, error, notice, retry,
    createProfile: async (name) => { await perform(async () => { const value = await api.createProfile(name); setSettings(value) }, 'Profile created.') },
    updateProfile: async (id, profile) => { await perform(async () => { const value = await api.updateProfile(id, profile); setSettings(value) }, 'Profile saved.') },
    setDefaultProfile: async (id) => { await perform(async () => { const value = await api.setDefaultProfile(id); setSettings(value) }, 'Default profile updated.') },
    deleteProfile: async (id) => { await perform(async () => { await api.deleteProfile(id); await reloadSettings() }, 'Profile deleted.') },
    updateApplicationSettings: async (input) => { await perform(async () => { const value = await api.updateApplicationSettings(input); setSettings(value) }, 'Preferences saved.') },
    setNotificationsEnabled,
    clearSessionHistory: async () => { await perform(() => api.clearSessionHistory(), 'Session history cleared.') },
    cleanExpiredHistory: () => perform(async () => {
      const deleted = await api.cleanExpiredHistory()
      setNotice(`${deleted} expired ${deleted === 1 ? 'session' : 'sessions'} removed.`)
      return deleted
    }),
    deleteAllData: async () => { await perform(async () => { await api.deleteAllData(); await reloadSettings() }, 'All personal data was deleted.') },
    exportAllData: (format) => perform(() => api.exportAllData(format), 'Data export saved.'),
    openDataFolder: () => perform(() => api.openDataFolder(), 'Opened the local data folder.'),
  }
}
