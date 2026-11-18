// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppSettings } from '../src/api/settingsApi'
import { SettingsPage } from '../src/pages/SettingsPage'
import type { AppSettingsController } from '../src/pages/useAppSettings'

afterEach(cleanup)

const sampleSettings: AppSettings = {
  defaultProfileId: 'classic',
  profiles: [
    { id: 'classic', name: 'Classic', workSeconds: 1500, shortBreakSeconds: 300, longBreakSeconds: 900,
      intervalsBeforeLongBreak: 4, autoStartBreaks: false, autoStartWork: false, isDefault: true },
    { id: 'deep', name: 'Deep work', workSeconds: 3000, shortBreakSeconds: 600, longBreakSeconds: 1200,
      intervalsBeforeLongBreak: 4, autoStartBreaks: false, autoStartWork: false, isDefault: false },
  ],
  dailyGoalHours: 3, theme: 'LIGHT', notificationsEnabled: true, retentionDays: 30,
}

function makeController(overrides: Partial<AppSettingsController> = {}): AppSettingsController {
  return {
    settings: sampleSettings, loading: false, pending: false, error: null, notice: null,
    retry: vi.fn(), createProfile: vi.fn().mockResolvedValue(undefined),
    updateProfile: vi.fn().mockResolvedValue(undefined), setDefaultProfile: vi.fn().mockResolvedValue(undefined),
    deleteProfile: vi.fn().mockResolvedValue(undefined), updateApplicationSettings: vi.fn().mockResolvedValue(undefined),
    clearSessionHistory: vi.fn().mockResolvedValue(undefined), cleanExpiredHistory: vi.fn().mockResolvedValue(2),
    deleteAllData: vi.fn().mockResolvedValue(undefined), exportAllData: vi.fn().mockResolvedValue(true),
    openDataFolder: vi.fn().mockResolvedValue(undefined), ...overrides,
  }
}

describe('settings screen', () => {
  it('explains when settings failed to load and provides a retry action', async () => {
    const user = userEvent.setup()
    const retry = vi.fn()
    render(<SettingsPage controller={makeController({
      settings: null, loading: false, error: 'The local API request failed (401).', retry,
    })} />)

    expect(screen.getByRole('alert').textContent).toContain('actions are unavailable')
    const addProfile = screen.getByRole('button', { name: /Add profile/ })
    expect(addProfile.disabled).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('creates profiles, selects a default, and edits starter profiles', async () => {
    const user = userEvent.setup()
    const controller = makeController()
    render(<SettingsPage controller={controller} />)

    await user.click(screen.getByRole('button', { name: /Add profile/ }))
    await user.type(screen.getByRole('textbox', { name: 'Profile name' }), 'Exam routine')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(controller.createProfile).toHaveBeenCalledWith('Exam routine')

    await user.click(screen.getByRole('button', { name: 'Set default' }))
    expect(controller.setDefaultProfile).toHaveBeenCalledWith('deep')

    const classicRow = screen.getByText('Classic').closest('.saved-profile-row')!
    await user.click(within(classicRow).getByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByRole('spinbutton', { name: 'Work minutes' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Work minutes' }), '45')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(controller.updateProfile).toHaveBeenCalledWith('classic', expect.objectContaining({
      workSeconds: 2700, shortBreakSeconds: 300, longBreakSeconds: 900,
    }))
  })

  it('requires explicit confirmation for expired cleanup and full history clearing', async () => {
    const user = userEvent.setup()
    const controller = makeController()
    render(<SettingsPage controller={controller} />)

    await user.click(screen.getByRole('button', { name: /Clean expired history/ }))
    expect(controller.cleanExpiredHistory).not.toHaveBeenCalled()
    expect(screen.getByRole('alertdialog').textContent).toContain('older than the selected retention period')
    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(controller.cleanExpiredHistory).toHaveBeenCalledOnce()

    await user.click(screen.getByRole('button', { name: /Clear session history/ }))
    expect(controller.clearSessionHistory).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    expect(controller.clearSessionHistory).toHaveBeenCalledOnce()
  })

  it('requires the delete-all phrase and persists goal, theme, and retention settings', async () => {
    const user = userEvent.setup()
    const controller = makeController()
    render(<SettingsPage controller={controller} />)

    await user.clear(screen.getByRole('spinbutton', { name: 'Daily focus goal in hours' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Daily focus goal in hours' }), '5')
    await user.tab()
    expect(controller.updateApplicationSettings).toHaveBeenCalledWith(expect.objectContaining({ dailyGoalHours: 5 }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Application Theme' }), 'DARK')
    expect(controller.updateApplicationSettings).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'DARK' }))

    await user.click(screen.getByRole('button', { name: /^Delete all data/ }))
    const confirm = screen.getByRole('button', { name: 'Confirm' })
    expect(confirm.disabled).toBe(true)
    await user.type(screen.getByRole('textbox', { name: 'Type DELETE_ALL_DATA to confirm' }), 'DELETE_ALL_DATA')
    expect(confirm.disabled).toBe(false)
    await user.click(confirm)
    expect(controller.deleteAllData).toHaveBeenCalledOnce()
  })
})
