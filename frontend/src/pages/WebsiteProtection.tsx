import { useEffect, useRef, useState } from 'react'
import type { BlockingRulesApi, WebsiteProtectionStatus } from '../api/blockingRulesApi'

export function WebsiteProtection({ api }: { api: BlockingRulesApi }) {
  const [status, setStatus] = useState<WebsiteProtectionStatus | null>(null)
  const [acknowledged, setAcknowledged] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  const revision = useRef(0)
  const configuring = useRef(false)
  useEffect(() => {
    if (!api.websiteStatus) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      const ticket = ++revision.current
      try {
        if (configuring.current) return
        const value = await api.websiteStatus!()
        if (active && ticket === revision.current && !configuring.current) setStatus(value)
      } catch (cause) {
        if (active && ticket === revision.current && !configuring.current) setError(message(cause))
      } finally { if (active) timer = setTimeout(() => void refresh(), 2000) }
    }
    void refresh()
    return () => { active = false; clearTimeout(timer) }
  }, [api])
  async function configure(enabled: boolean) {
    if (!api.setWebsiteProtection) return
    configuring.current = true; revision.current++; setPending(true); setError('')
    try { setStatus(await api.setWebsiteProtection(enabled)); setAcknowledged(false); setCopied(false) }
    catch (cause) { setError(message(cause)) }
    finally { configuring.current = false; revision.current++; setPending(false) }
  }
  async function copyAddress() {
    try { await navigator.clipboard.writeText(status!.verificationUrl!); setCopied(true) }
    catch { setError('Copy the verification address from the field and paste it into your browser.') }
  }
  return <section className="profile-panel card-panel" aria-label="Website protection">
    <h2>Website protection</h2>
    {!api.websiteStatus ? <p>Website protection is not connected.</p>
      : !status ? <p role="status">Checking website protection…</p>
        : <>
          <p role="status">{status.reason}</p>
          {status.routingVerified ? <p>A browser has verified routing through FocusDesktop. Verify each other browser you use.</p>
            : status.enabled && <p>Browser routing is not verified. Saved website rules alone do not establish protection.</p>}
          <p>Matching websites and subdomains are blocked during running focus intervals. Pauses and breaks allow browsing.</p>
          <p>Allowed browser traffic passes through a local proxy. HTTPS stays encrypted; no certificates, telemetry or cloud service are used. Blocked HTTPS pages show a browser connection error.</p>
          {status.state === 'RESTORING' && <p role="alert">Restoring network settings. Keep FocusDesktop open.</p>}
          {!status.enabled && <label className="protection-consent"><input type="checkbox" checked={acknowledged} disabled={pending}
            onChange={(event) => setAcknowledged(event.target.checked)} />Allow browser-requested forwarding and temporary proxy settings with automatic restoration.</label>}
          {api.setWebsiteProtection && <button type="button" className="button button-primary"
            disabled={pending || (!status.enabled && !acknowledged)} onClick={() => void configure(!status.enabled)}>
            {pending ? 'Updating website protection…' : status.enabled ? 'Disable website protection' : 'Enable website protection'}</button>}
          {status.enabled && status.verificationUrl && <div className="protection-consent">
            <label>Website verification address<input aria-label="Website verification address" readOnly value={status.verificationUrl} /></label>
            <button type="button" onClick={() => void copyAddress()}>{copied ? 'Address copied' : 'Copy verification address'}</button>
            <p>Paste this address into each browser. Use system proxy settings in the browser; restart it if needed. A verification page confirms routing. Custom proxies, managed policies and unsupported desktops may prevent protection.</p>
          </div>}
        </>}
    {error && <p role="alert">{error}</p>}
  </section>
}
function message(cause: unknown) { return cause instanceof Error ? cause.message : 'Website protection could not be updated.' }
