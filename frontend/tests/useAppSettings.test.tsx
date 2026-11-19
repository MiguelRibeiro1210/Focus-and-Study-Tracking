// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppSettings, SettingsApi } from '../src/api/settingsApi'
import type { NotificationPermissionPort } from '../src/api/nativeNotifications'
import { useAppSettings } from '../src/pages/useAppSettings'

afterEach(cleanup)

const settings: AppSettings = {
  defaultProfileId: 'classic',
  profiles: [{ id: 'classic', name: 'Classic', workSeconds: 1500, shortBreakSeconds: 300,
    longBreakSeconds: 900, intervalsBeforeLongBreak: 4, autoStartBreaks: false,
    autoStartWork: false, isDefault: true }],
  dailyGoalHours: 3, theme: 'LIGHT', notificationsEnabled: false, retentionDays: 30,
}

function makeApi(initial: AppSettings = settings): SettingsApi {
  let stored = initial
  return {
    getSettings: vi.fn(async () => stored),
    createProfile: vi.fn(), updateProfile: vi.fn(), setDefaultProfile: vi.fn(), deleteProfile: vi.fn(),
    updateApplicationSettings: vi.fn(async (input) => {
      stored = { ...stored, ...input }
      return stored
    }),
    clearSessionHistory: vi.fn(), cleanExpiredHistory: vi.fn(), deleteAllData: vi.fn(),
    exportAllData: vi.fn(), openDataFolder: vi.fn(),
  }
}

function Harness({ api, notifications }: { api: SettingsApi; notifications: NotificationPermissionPort }) {
  const controller = useAppSettings(api, notifications)
  return <>
    <output data-testid="enabled">{String(controller.settings?.notificationsEnabled ?? 'loading')}</output>
    <button type="button" onClick={() => void controller.setNotificationsEnabled(true)}>Enable</button>
    <button type="button" onClick={() => void controller.setNotificationsEnabled(false)}>Disable</button>
    {controller.notice && <p role="status">{controller.notice}</p>}
  </>
}

function permissionPort(result: 'granted' | 'denied' | 'unsupported'): NotificationPermissionPort {
  return {
    ensurePermission: vi.fn().mockResolvedValue(result),
    isPermissionGranted: vi.fn().mockResolvedValue(result === 'granted'),
    send: vi.fn(),
  }
}

describe('application notification preference', () => {
  it('asks on startup when notifications are saved on and persists denial as off', async () => {
    const api = makeApi({ ...settings, notificationsEnabled: true })
    const notifications = permissionPort('denied')
    render(<Harness api={api} notifications={notifications} />)

    await waitFor(() => expect(screen.getByTestId('enabled').textContent).toBe('false'))
    expect(notifications.ensurePermission).toHaveBeenCalledOnce()
    expect(api.updateApplicationSettings).toHaveBeenCalledWith(expect.objectContaining({ notificationsEnabled: false }))
    expect(screen.getByText(/permission was denied/).textContent).toContain('permission was denied')
  })

  it('retries permission on each activation after denial and saves enabled only after a grant', async () => {
    const user = userEvent.setup()
    const api = makeApi()
    const notifications = permissionPort('denied')
    render(<Harness api={api} notifications={notifications} />)

    await waitFor(() => expect(screen.getByTestId('enabled').textContent).toBe('false'))
    await user.click(screen.getByRole('button', { name: 'Enable' }))
    await waitFor(() => expect(notifications.ensurePermission).toHaveBeenCalledTimes(1))
    expect(screen.getByTestId('enabled').textContent).toBe('false')

    vi.mocked(notifications.ensurePermission).mockResolvedValue('granted')
    await user.click(screen.getByRole('button', { name: 'Enable' }))
    await waitFor(() => expect(screen.getByTestId('enabled').textContent).toBe('true'))
    expect(notifications.ensurePermission).toHaveBeenCalledTimes(2)
    expect(api.updateApplicationSettings).toHaveBeenLastCalledWith(expect.objectContaining({ notificationsEnabled: true }))
  })

  it('disables notifications without requesting permission again', async () => {
    const user = userEvent.setup()
    const api = makeApi({ ...settings, notificationsEnabled: true })
    const notifications = permissionPort('granted')
    render(<Harness api={api} notifications={notifications} />)
    await waitFor(() => expect(screen.getByTestId('enabled').textContent).toBe('true'))
    vi.mocked(notifications.ensurePermission).mockClear()

    await user.click(screen.getByRole('button', { name: 'Disable' }))

    await waitFor(() => expect(screen.getByTestId('enabled').textContent).toBe('false'))
    expect(notifications.ensurePermission).not.toHaveBeenCalled()
  })
})
