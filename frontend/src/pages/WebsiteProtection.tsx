import { useEffect, useRef, useState } from 'react'
import type { BlockingRulesApi, WebsiteProtectionStatus, WebsiteRoutingMethod } from '../api/blockingRulesApi'

export function WebsiteProtection({ api }: { api: BlockingRulesApi }) {
  const [status, setStatus] = useState<WebsiteProtectionStatus | null>(null)
  const [routingChoice, setRoutingChoice] = useState<WebsiteRoutingMethod | null>(null)
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
        if (active && ticket === revision.current && !configuring.current) { setStatus(value) }
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
    try {
      const method = enabled ? routingChoice ?? status?.routingMethod : status?.routingMethod
      setStatus(await (method ? api.setWebsiteProtection(enabled, method) : api.setWebsiteProtection(enabled)))
      setAcknowledged(false); setCopied(false); setRoutingChoice(null)
    }
    catch (cause) { setError(message(cause)) }
    finally { configuring.current = false; revision.current++; setPending(false) }
  }
  async function setupBrave() {
    if (!api.setupBrave) return
    configuring.current = true; revision.current++; setPending(true); setError('')
    try { setStatus(await api.setupBrave()) }
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
          {status.runtimeEnabled === false && <div className="website-runtime-help" role="alert"><p>Website blocking is unavailable in this backend mode. Saving a rule or enabling consent cannot block websites yet.</p>
            <p>Use <code>npm run tauri:dev</code>, or restart your separate development backend with:</p>
            <code>./mvnw spring-boot:run -Dspring-boot.run.arguments=--focusdesktop.blocking.monitor-enabled=true</code></div>}
          {(status.braveAvailable || status.routingMethod === 'BRAVE') && <label className="website-routing-choice">Connection method<select aria-label="Website connection method"
            disabled={pending || status.braveLaunchPending || status.state === 'RESTORING'} value={routingChoice ?? status.routingMethod ?? 'SYSTEM_PROXY'}
            onChange={(event) => setRoutingChoice(event.target.value as WebsiteRoutingMethod)}>
            <option value="SYSTEM_PROXY">System proxy (GNOME on Linux)</option><option value="BRAVE" disabled={!status.braveAvailable}>Brave (Linux, including Hyprland)</option>
          </select></label>}
          {status.enabled && routingChoice && routingChoice !== status.routingMethod && <button type="button"
            disabled={pending || status.runtimeEnabled === false || status.state === 'RESTORING'} onClick={() => void configure(true)}>Apply connection method</button>}
          {status.routingMethod === 'BRAVE' && <p>Brave setup uses your existing profile without editing system settings or profile files. Other browsers are outside this routing method. After FocusDesktop closes, Brave can browse directly; set up Brave again after restarting protection.</p>}
          {status.routingVerified ? <p>{status.routingMethod === 'BRAVE' ? 'Browser routing through FocusDesktop is verified. Only the Brave instance opened with this setup is covered.' : 'A browser has verified routing through FocusDesktop. Verify each other browser you use.'}</p>
            : status.enabled && <p>Browser routing is not verified. Saved website rules alone do not establish protection.</p>}
          <p>Matching websites and subdomains are blocked during running focus intervals. Pauses and breaks allow browsing.</p>
          <p>Requests from browsers and other apps using FocusDesktop’s configured proxy pass through it only when requested by those clients. HTTPS stays encrypted; no certificates, telemetry, cloud service or autonomous outbound requests are used. Blocked HTTPS pages show a browser connection error.</p>
          {status.state === 'RESTORING' && <p role="alert">Restoring network settings. Keep FocusDesktop open.</p>}
          {!status.enabled && <label className="protection-consent"><input type="checkbox" checked={acknowledged} disabled={pending}
            onChange={(event) => setAcknowledged(event.target.checked)} />Allow forwarding requests from browsers and other apps using FocusDesktop’s proxy through Brave setup or temporary system proxy settings, with automatic restoration.</label>}
          {api.setWebsiteProtection && <button type="button" className="button button-primary"
            disabled={pending || status.state === 'RESTORING' || (!status.enabled && (!acknowledged || status.runtimeEnabled === false))} onClick={() => void configure(!status.enabled)}>
            {pending ? 'Updating website protection…' : status.enabled ? 'Disable website protection' : 'Enable website protection'}</button>}
          {status.enabled && status.routingMethod === 'BRAVE' && api.setupBrave && <div className="protection-consent">
            <p>Choose Set up Brave, then close all Brave windows within 60 seconds, including this UI if opened in Brave. FocusDesktop will reopen your existing profile. Save your work first; nothing is force-closed.</p>
            <button type="button" disabled={pending || status.braveLaunchPending || status.runtimeEnabled === false || !status.braveAvailable || !['READY', 'VERIFIED'].includes(status.state)}
              onClick={() => void setupBrave()}>{status.braveLaunchPending ? 'Waiting for Brave to close…' : 'Set up Brave'}</button>
          </div>}
          {status.enabled && status.verificationUrl && <div className="protection-consent">
            <label>Website verification address<input aria-label="Website verification address" readOnly value={status.verificationUrl} /></label>
            <button type="button" onClick={() => void copyAddress()}>{copied ? 'Address copied' : 'Copy verification address'}</button>
            <p>{status.routingMethod === 'BRAVE' ? 'The Brave setup opens this address automatically. A verification page confirms that Brave is routing through FocusDesktop.' : 'Paste this address into each browser. Use system proxy settings in the browser; restart it if needed. A verification page confirms routing.'} Custom proxies, managed policies and unsupported installations may prevent protection.</p>
          </div>}
        </>}
    {error && <p role="alert">{error}</p>}
  </section>
}
function message(cause: unknown) { return cause instanceof Error ? cause.message : 'Website protection could not be updated.' }
