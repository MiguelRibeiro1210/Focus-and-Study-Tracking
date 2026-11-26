// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { WebsiteProtection } from '../src/pages/WebsiteProtection'
import type { BlockingRulesApi, WebsiteProtectionStatus } from '../src/api/blockingRulesApi'

afterEach(cleanup)
const disabled: WebsiteProtectionStatus = { enabled: false, state: 'DISABLED', routingVerified: false, verificationUrl: null, reason: 'Disabled.' }
function api(state = disabled, set = vi.fn()): BlockingRulesApi {
  return { list: vi.fn(), create: vi.fn(), patch: vi.fn(), delete: vi.fn(), websiteStatus: vi.fn().mockResolvedValue(state), setWebsiteProtection: set }
}
it('requires separate forwarding acknowledgement and preserves the setting after a failed save', async () => {
  const user = userEvent.setup()
  const save = vi.fn().mockRejectedValueOnce(new Error('Unable to save consent')).mockResolvedValueOnce({ ...disabled, enabled: true, state: 'UNAVAILABLE', reason: 'Permission unavailable.' })
  render(<WebsiteProtection api={api(disabled, save)} />)
  const enable = await screen.findByRole('button', { name: 'Enable website protection' })
  expect(enable.hasAttribute('disabled')).toBe(true)
  expect(screen.getByText(/no certificates, telemetry, cloud service or autonomous outbound requests/)).toBeTruthy()
  await user.click(screen.getByRole('checkbox', { name: /Allow forwarding requests from browsers and other apps/ }))
  await user.click(enable)
  expect((await screen.findByRole('alert')).textContent).toContain('Unable to save consent')
  expect((screen.getByRole('button', { name: 'Enable website protection' }) as HTMLButtonElement).disabled).toBe(false)
  await user.click(enable)
  expect(await screen.findByText('Permission unavailable.')).toBeTruthy()
  expect(screen.getByText(/Browser routing is not verified/)).toBeTruthy()
  expect(save).toHaveBeenCalledWith(true)
})
it('shows a browser verification address without claiming all browsers are protected', async () => {
  render(<WebsiteProtection api={api({ ...disabled, enabled: true, state: 'READY',
    verificationUrl: 'http://focusdesktop.invalid/verify/00000000-0000-0000-0000-000000000001', reason: 'Verify routing.' })} />)
  expect(((await screen.findByLabelText('Website verification address')) as HTMLInputElement).value).toContain('/verify/')
  expect(screen.getByText(/Browser routing is not verified/)).toBeTruthy()
  expect((screen.getByRole('button', { name: 'Disable website protection' }) as HTMLButtonElement).disabled).toBe(false)
})
