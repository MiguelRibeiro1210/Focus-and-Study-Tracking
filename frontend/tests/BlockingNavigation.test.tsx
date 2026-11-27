// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { FocusDesktopApp } from '../src/app/FocusDesktopApp'
import type { AppSettingsController } from '../src/pages/useAppSettings'
import type { TimerController } from '../src/timer/useTimerSession'
import { client, site, webReady } from './blockingFixtures'
vi.mock('../src/api/sessionApi', () => ({ sessionApi: { getSessionHistory: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, size: 3 }) } }))
afterEach(cleanup)
it('preserves the session draft through blocking Settings and rechecks instead of auto-starting', async () => {
  const user = userEvent.setup(), runAction = vi.fn().mockResolvedValue(undefined)
  const timer: TimerController = { session: null, remainingSeconds: null, isLoading: false, pendingAction: null, error: null, runAction, retry: vi.fn() }
  const settings: AppSettingsController = {
    settings: null, loading: false, pending: false, error: null, notice: null, retry: vi.fn(),
    createProfile: vi.fn(), updateProfile: vi.fn(), setDefaultProfile: vi.fn(), deleteProfile: vi.fn(), updateApplicationSettings: vi.fn(),
    setNotificationsEnabled: vi.fn(), clearSessionHistory: vi.fn(), cleanExpiredHistory: vi.fn(), deleteAllData: vi.fn(), exportAllData: vi.fn(), openDataFolder: vi.fn(),
  }
  const api = client([site], { websiteStatus: vi.fn().mockResolvedValue({ ...webReady, enabled: false }) })
  render(<FocusDesktopApp timer={timer} settings={settings} blockingApi={api} />)
  await user.type(screen.getByRole('textbox', { name: 'What will you work on?' }), 'Study')
  await user.type(screen.getByRole('spinbutton', { name: 'Work duration in minutes' }), '40')
  await user.click(screen.getByRole('button', { name: 'Start focus session' }))
  await user.click(await screen.findByRole('button', { name: 'Set up blocking' }))
  expect(screen.getByRole('region', { name: 'Blocking settings' })).toBeTruthy()
  expect(runAction).not.toHaveBeenCalled()
  vi.mocked(api.websiteStatus!).mockResolvedValue(webReady)
  await user.click(screen.getByRole('button', { name: 'Back to session' }))
  expect((screen.getByRole('textbox', { name: 'What will you work on?' }) as HTMLInputElement).value).toBe('Study')
  expect((screen.getByRole('spinbutton', { name: 'Work duration in minutes' }) as HTMLInputElement).value).toBe('40')
  expect(runAction).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Start focus session' }))
  expect(runAction).toHaveBeenCalledWith('start', 'Study', { workSeconds: 2400 })
})
