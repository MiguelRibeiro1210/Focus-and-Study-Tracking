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
it('explains passive browser development and prevents pretending consent enables enforcement', async () => {
  const user = userEvent.setup()
  const save = vi.fn()
  render(<WebsiteProtection api={api({ ...disabled, runtimeEnabled: false, routingMethod: 'SYSTEM_PROXY', braveAvailable: true }, save)} />)
  expect(await screen.findByText(/Website blocking is unavailable in this backend mode/)).toBeTruthy()
  expect(screen.getByText(/spring-boot.run.arguments=--focusdesktop.blocking.monitor-enabled=true/)).toBeTruthy()
  await user.click(screen.getByRole('checkbox'))
  expect((screen.getByRole('button', { name: 'Enable website protection' }) as HTMLButtonElement).disabled).toBe(true)
  expect(save).not.toHaveBeenCalled()
})
it('switches an enabled rule setup to Brave and requests a voluntary existing-profile launch', async () => {
  const user = userEvent.setup()
  const initial: WebsiteProtectionStatus = { ...disabled, enabled: true, runtimeEnabled: true, routingMethod: 'SYSTEM_PROXY', state: 'UNAVAILABLE', braveAvailable: true }
  const ready: WebsiteProtectionStatus = { ...initial, routingMethod: 'BRAVE', state: 'READY', reason: 'Brave ready.' }
  const save = vi.fn().mockResolvedValue(ready)
  const launch = vi.fn().mockResolvedValue({ ...ready, braveLaunchPending: true, reason: 'Close all Brave windows within 60 seconds.' })
  render(<WebsiteProtection api={{ ...api(initial, save), setupBrave: launch }} />)
  await user.selectOptions(await screen.findByLabelText('Website connection method'), 'BRAVE')
  await user.click(screen.getByRole('button', { name: 'Apply connection method' }))
  expect(await screen.findByText('Brave ready.')).toBeTruthy()
  expect(save).toHaveBeenCalledWith(true, 'BRAVE')
  expect(screen.getByText(/nothing is force-closed/)).toBeTruthy()
  await user.click(screen.getByRole('button', { name: 'Set up Brave' }))
  expect(await screen.findByRole('button', { name: 'Waiting for Brave to close…' })).toBeTruthy()
  expect(launch).toHaveBeenCalledTimes(1)
  expect(screen.getByText(/Browser routing is not verified/)).toBeTruthy()
})
it('shows failed Brave setup without claiming the browser is protected', async () => {
  const user = userEvent.setup()
  const ready: WebsiteProtectionStatus = { ...disabled, enabled: true, runtimeEnabled: true, routingMethod: 'BRAVE', state: 'READY', braveAvailable: true }
  render(<WebsiteProtection api={{ ...api(ready), setupBrave: vi.fn().mockRejectedValue(new Error('Brave setup is unavailable')) }} />)
  await user.click(await screen.findByRole('button', { name: 'Set up Brave' }))
  expect((await screen.findByRole('alert')).textContent).toContain('Brave setup is unavailable')
  expect(screen.getByText(/Browser routing is not verified/)).toBeTruthy()
})
