import { describe, expect, it, vi } from 'vitest'
import { createSettingsApi, type AppSettings } from '../src/api/settingsApi'

const settings: AppSettings = {
  defaultProfileId: 'classic',
  profiles: [{ id: 'classic', name: 'Classic', workSeconds: 1500, shortBreakSeconds: 300,
    longBreakSeconds: 900, intervalsBeforeLongBreak: 4, autoStartBreaks: false,
    autoStartWork: false, isDefault: true }],
  dailyGoalHours: 3, theme: 'LIGHT', notificationsEnabled: true, retentionDays: null,
}

describe('settings REST client', () => {
  it('loads settings and uses the expected write methods for profiles and preferences', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(Response.json(settings))
      .mockResolvedValueOnce(Response.json(settings, { status: 201 }))
      .mockResolvedValueOnce(Response.json(settings))
      .mockResolvedValueOnce(Response.json(settings))
      .mockResolvedValueOnce(Response.json(settings))
    const api = createSettingsApi(fetcher)

    await expect(api.getSettings()).resolves.toEqual(settings)
    await api.createProfile('Exam')
    await api.updateProfile('profile/id', { name: 'Exam', workSeconds: 2700, shortBreakSeconds: 420,
      longBreakSeconds: 900, intervalsBeforeLongBreak: 4, autoStartBreaks: true, autoStartWork: false })
    await api.setDefaultProfile('profile/id')
    await api.updateApplicationSettings({ dailyGoalHours: 5, theme: 'DARK', notificationsEnabled: false, retentionDays: 90 })

    expect(fetcher.mock.calls.map(([url, init]) => [url, (init as RequestInit).method ?? 'GET'])).toEqual([
      ['/api/v1/settings', 'GET'],
      ['/api/v1/settings/profiles', 'POST'],
      ['/api/v1/settings/profiles/profile%2Fid', 'PUT'],
      ['/api/v1/settings/profiles/default', 'PUT'],
      ['/api/v1/settings/application', 'PUT'],
    ])
    expect(JSON.parse((fetcher.mock.calls[2][1] as RequestInit).body as string)).toMatchObject({ workSeconds: 2700 })
  })

  it('separates full history clearing, retention cleanup and delete-all confirmation', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(Response.json({ deletedSessions: 2 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const api = createSettingsApi(fetcher)

    await api.clearSessionHistory()
    await expect(api.cleanExpiredHistory()).resolves.toBe(2)
    await api.deleteAllData()

    expect(fetcher.mock.calls.map(([url, init]) => [url, (init as RequestInit).method])).toEqual([
      ['/api/v1/sessions/history', 'DELETE'],
      ['/api/v1/sessions/history/expired', 'DELETE'],
      ['/api/v1/settings/data', 'DELETE'],
    ])
    expect(JSON.parse((fetcher.mock.calls[2][1] as RequestInit).body as string)).toEqual({ confirmation: 'DELETE_ALL_DATA' })
  })

  it('rejects malformed settings responses and exposes server validation messages', async () => {
    const malformed = createSettingsApi(vi.fn().mockResolvedValue(Response.json({ profiles: [] })))
    await expect(malformed.getSettings()).rejects.toThrow('invalid settings')

    const rejected = createSettingsApi(vi.fn().mockResolvedValue(Response.json({ message: 'Invalid retention days' }, { status: 400 })))
    await expect(rejected.getSettings()).rejects.toThrow('Invalid retention days (HTTP 400: GET /settings)')
  })
})
