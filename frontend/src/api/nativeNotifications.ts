import {
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from '@tauri-apps/plugin-notification'
import { join, resourceDir } from '@tauri-apps/api/path'
import { isDesktopShell } from './nativeTransport'

export type NotificationPermission = 'granted' | 'denied' | 'unsupported'
export type NotificationIntervalType = 'WORK' | 'SHORT_BREAK' | 'LONG_BREAK'

export interface NotificationPermissionPort {
  ensurePermission(): Promise<NotificationPermission>
  isPermissionGranted(): Promise<boolean>
  send(title: string, body: string): Promise<void> | void
}

let permissionRequest: Promise<NotificationPermission> | undefined

const systemNotifications: NotificationPermissionPort = {
  ensurePermission() {
    if (permissionRequest) return permissionRequest
    permissionRequest = (async () => {
      if (isDesktopShell()) {
        if (await isPermissionGranted()) return 'granted'
        return await requestPermission() === 'granted' ? 'granted' : 'denied'
      }

      if (typeof Notification === 'undefined') return 'unsupported'
      if (Notification.permission === 'granted') return 'granted'
      if (Notification.permission === 'denied') return 'denied'
      return await Notification.requestPermission() === 'granted' ? 'granted' : 'denied'
    })().finally(() => { permissionRequest = undefined })
    return permissionRequest
  },
  async isPermissionGranted() {
    if (isDesktopShell()) return isPermissionGranted()
    return typeof Notification !== 'undefined' && Notification.permission === 'granted'
  },
  async send(title, body) {
    if (isDesktopShell()) {
      try {
        const icon = await join(await resourceDir(), 'notification-icon.png')
        await sendNotification({ title, body, icon })
      } catch {
        await sendNotification({ title, body })
      }
      return
    }
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      new Notification(title, { body, icon: '/focusdesktop-logo.png' })
    }
  },
}

export const notificationPermissionPort = systemNotifications

const intervalMessages: Record<NotificationIntervalType, { title: string; body: string }> = {
  WORK: {
    title: 'Focus interval complete',
    body: 'Your work interval is complete. Take a moment before the next interval.',
  },
  SHORT_BREAK: {
    title: 'Short break complete',
    body: 'Your short break is complete. Ready to return to focus?',
  },
  LONG_BREAK: {
    title: 'Long break complete',
    body: 'Your long break is complete. Return to focus when you are ready.',
  },
}

export async function sendIntervalCompletionNotification(
  intervalType: NotificationIntervalType,
  port: NotificationPermissionPort = systemNotifications,
): Promise<void> {
  if (!await port.isPermissionGranted()) return
  const message = intervalMessages[intervalType]
  await port.send(message.title, message.body)
}

export function sendIntervalCompletionNotificationIfEnabled(
  intervalType: NotificationIntervalType,
  isEnabled: () => boolean,
): Promise<void> {
  return isEnabled() ? sendIntervalCompletionNotification(intervalType) : Promise.resolve()
}
