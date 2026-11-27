// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { BlockingSettings } from '../src/pages/BlockingSettings'
import { appsReady, client, webReady } from './blockingFixtures'
afterEach(cleanup)
it('keeps settings concise and asks for app consent before saving, with retry on failure', async () => {
  const user = userEvent.setup()
  const save = vi.fn().mockRejectedValueOnce(new Error('Could not save')).mockResolvedValueOnce({ applicationsEnabled: true })
  render(<BlockingSettings api={client([], { status: vi.fn().mockResolvedValue({ ...appsReady, applicationProtectionEnabled: false }), setApplicationProtection: save })} />)
  const toggle = await screen.findByRole('switch', { name: 'Block apps' })
  expect(screen.queryByText(/temporarily pauses/)).toBeNull()
  await user.click(toggle); const dialog = screen.getByRole('dialog')
  expect(dialog.textContent).toContain('temporarily pauses'); expect(save).not.toHaveBeenCalled()
  await user.click(within(dialog).getByRole('button', { name: 'Allow app blocking' }))
  expect((await screen.findByRole('alert')).textContent).toContain('Could not save'); expect(toggle.getAttribute('aria-checked')).toBe('false')
  await user.click(within(dialog).getByRole('button', { name: 'Allow app blocking' }))
  expect(toggle.getAttribute('aria-checked')).toBe('true'); expect(screen.queryByRole('dialog')).toBeNull()
})
it('explains forwarding in its consent dialog and confirms existing-profile Brave setup separately', async () => {
  const user = userEvent.setup()
  const off = { ...webReady, enabled: false, state: 'DISABLED', routingVerified: false, routingMethod: 'SYSTEM_PROXY', braveAvailable: true } as const
  const ready = { ...off, enabled: true, routingMethod: 'BRAVE', state: 'READY' } as const
  const save = vi.fn().mockResolvedValue(ready), launch = vi.fn().mockResolvedValue({ ...ready, braveLaunchPending: true })
  render(<BlockingSettings api={client([], { websiteStatus: vi.fn().mockResolvedValue(off), setWebsiteProtection: save, setupBrave: launch })} />)
  await user.selectOptions(await screen.findByLabelText('Browser connection'), 'BRAVE')
  expect(screen.queryByText(/HTTPS stays encrypted/)).toBeNull()
  await user.click(screen.getByRole('switch', { name: 'Block websites' }))
  expect(screen.getByRole('dialog').textContent).toContain('HTTPS stays encrypted'); expect(save).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Allow website blocking' })); expect(save).toHaveBeenCalledWith(true, 'BRAVE')
  await user.click(await screen.findByRole('button', { name: 'Connect browser' }))
  expect(screen.getByRole('dialog').textContent).toContain('close all Brave windows within 60 seconds'); expect(launch).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Set up Brave' })); expect(launch).toHaveBeenCalledOnce()
  expect(await screen.findByText('Waiting for Brave to close…')).toBeTruthy()
})
it('offers a non-disruptive connection check and never asks to restart a connected Brave instance', async () => {
  const user = userEvent.setup()
  const verificationUrl = 'http://focusdesktop.invalid/verify/00000000-0000-0000-0000-000000000001'
  const launch = vi.fn()
  render(<BlockingSettings api={client([], { websiteStatus: vi.fn().mockResolvedValue({ ...webReady, routingMethod: 'BRAVE', braveAvailable: true, verificationUrl }), setupBrave: launch })} />)
  await user.click(await screen.findByRole('button', { name: 'Connect browser' }))
  const dialog = screen.getByRole('dialog')
  const check = within(dialog).getByRole('link', { name: 'Check connection' })
  expect(check.getAttribute('href')).toBe(verificationUrl)
  expect(check.getAttribute('target')).toBe('_blank')
  expect(within(dialog).queryByRole('button', { name: 'Set up Brave' })).toBeNull()
  expect(dialog.textContent).toContain('Brave is connected')
  expect(dialog.textContent).not.toContain('close all Brave windows')
  expect(launch).not.toHaveBeenCalled()
})
it('keeps developer instructions in help and restores focus on Escape', async () => {
  const user = userEvent.setup()
  render(<BlockingSettings api={client([], { websiteStatus: vi.fn().mockResolvedValue({ ...webReady, enabled: false, runtimeEnabled: false, reason: 'Desktop connection needed.' }) })} />)
  expect((await screen.findByRole('switch', { name: 'Block websites' })).hasAttribute('disabled')).toBe(true)
  expect(screen.queryByText(/spring-boot.run.arguments/)).toBeNull()
  await user.click(screen.getByRole('button', { name: 'Setup help' })); expect(screen.getByRole('dialog').textContent).toContain('spring-boot.run.arguments')
  await user.keyboard('{Escape}'); expect(screen.queryByRole('dialog')).toBeNull(); expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Setup help' }))
})
it('reports failed checks as unavailable and keeps browser verification in a dialog', async () => {
  const user = userEvent.setup(), verificationUrl = 'http://focusdesktop.invalid/verify/00000000-0000-0000-0000-000000000001'
  render(<BlockingSettings api={client([], { status: vi.fn().mockRejectedValue(new Error()), websiteStatus: vi.fn().mockResolvedValue({ ...webReady, verificationUrl }) })} />)
  expect(await screen.findByText('Unavailable')).toBeTruthy(); expect(screen.getByRole('switch', { name: 'Block apps' }).hasAttribute('disabled')).toBe(true)
  expect(screen.queryByLabelText('Browser connection check')).toBeNull()
  await user.click(screen.getByRole('button', { name: 'Connect browser' })); expect((screen.getByLabelText('Browser connection check') as HTMLInputElement).value).toBe(verificationUrl)
})
