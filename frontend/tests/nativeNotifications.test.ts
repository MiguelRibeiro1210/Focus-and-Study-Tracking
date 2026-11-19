import { describe, expect, it, vi } from 'vitest'

const notificationMocks = vi.hoisted(() => ({
  isPermissionGranted: vi.fn().mockResolvedValue(true),
  requestPermission: vi.fn().mockResolvedValue('granted'),
  sendNotification: vi.fn().mockResolvedValue(undefined),
  resourceDir: vi.fn().mockResolvedValue('/opt/focusdesktop/resources'),
  join: vi.fn((...parts: string[]) => parts.join('/')),
}))

vi.mock('@tauri-apps/plugin-notification', () => ({
  isPermissionGranted: notificationMocks.isPermissionGranted,
  requestPermission: notificationMocks.requestPermission,
  sendNotification: notificationMocks.sendNotification,
}))
vi.mock('@tauri-apps/api/path', () => ({
  resourceDir: notificationMocks.resourceDir,
  join: notificationMocks.join,
}))
vi.mock('../src/api/nativeTransport', () => ({ isDesktopShell: () => true }))

import {
  sendIntervalCompletionNotification,
  sendIntervalCompletionNotificationIfEnabled,
  type NotificationPermissionPort,
} from '../src/api/nativeNotifications'

describe('interval completion notifications', () => {
  it.each([
    ['WORK', 'Focus interval complete'],
    ['SHORT_BREAK', 'Short break complete'],
    ['LONG_BREAK', 'Long break complete'],
  ] as const)('uses the distinct message for %s', async (intervalType, title) => {
    const port: NotificationPermissionPort = {
      ensurePermission: vi.fn().mockResolvedValue('granted'),
      isPermissionGranted: vi.fn().mockResolvedValue(true),
      send: vi.fn(),
    }

    await sendIntervalCompletionNotification(intervalType, port)

    expect(port.send).toHaveBeenCalledWith(title, expect.any(String))
  })

  it('does not send when operating-system permission is no longer granted', async () => {
    const port: NotificationPermissionPort = {
      ensurePermission: vi.fn().mockResolvedValue('denied'),
      isPermissionGranted: vi.fn().mockResolvedValue(false),
      send: vi.fn(),
    }

    await sendIntervalCompletionNotification('WORK', port)

    expect(port.send).not.toHaveBeenCalled()
  })

  it('passes the packaged FocusDesktop logo as the native notification icon', async () => {
    notificationMocks.sendNotification.mockClear()

    await sendIntervalCompletionNotification('WORK')

    expect(notificationMocks.join).toHaveBeenCalledWith('/opt/focusdesktop/resources', 'notification-icon.png')
    expect(notificationMocks.sendNotification).toHaveBeenCalledWith({
      title: 'Focus interval complete',
      body: expect.any(String),
      icon: '/opt/focusdesktop/resources/notification-icon.png',
    })
  })

  it('suppresses delivery immediately when the saved preference is disabled', async () => {
    const enabled = vi.fn(() => false)

    await sendIntervalCompletionNotificationIfEnabled('WORK', enabled)

    expect(enabled).toHaveBeenCalledOnce()
  })
})
