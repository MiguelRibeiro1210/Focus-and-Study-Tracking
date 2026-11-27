import { useEffect, useRef, useState } from 'react'
import type { BlockingRulesApi, BlockingStatus, WebsiteProtectionStatus, WebsiteRoutingMethod } from '../api/blockingRulesApi'
import { Modal } from '../components/Modal'

type Dialog = 'apps' | 'websites' | 'browser' | 'help' | null
export function BlockingSettings({ api, returnToSession }: { api: BlockingRulesApi; returnToSession?: () => void }) {
  const [apps, setApps] = useState<BlockingStatus | null>(null)
  const [web, setWeb] = useState<WebsiteProtectionStatus | null>(null)
  const [readError, setReadError] = useState(false)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<Dialog>(null)
  const [pending, setPending] = useState(false)
  const [route, setRoute] = useState<WebsiteRoutingMethod | null>(null)
  const [copied, setCopied] = useState(false)
  const changing = useRef(false)
  const revision = useRef(0)
  const active = useRef(true)
  const heading = useRef<HTMLHeadingElement>(null)
  const returning = Boolean(returnToSession)
  useEffect(() => { if (returning) heading.current?.focus() }, [returning])
  useEffect(() => {
    active.current = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      const ticket = ++revision.current
      if (!changing.current) {
        const values = await Promise.allSettled([api.status ? api.status() : Promise.reject(), api.websiteStatus ? api.websiteStatus() : Promise.reject()])
        if (active.current && ticket === revision.current && !changing.current) {
          setApps(values[0].status === 'fulfilled' ? values[0].value : null)
          setWeb(values[1].status === 'fulfilled' ? values[1].value : null)
          setReadError(values.some((value) => value.status === 'rejected'))
        }
      }
      if (active.current) timer = setTimeout(() => void refresh(), 2000)
    }
    void refresh()
    return () => { active.current = false; clearTimeout(timer) }
  }, [api])
  async function change(operation: () => Promise<void>) {
    if (changing.current) return
    changing.current = true; revision.current++; setPending(true); setError('')
    try { await operation(); if (active.current) setDialog(null) }
    catch (cause) { if (active.current) setError(cause instanceof Error ? cause.message : 'Could not save blocking settings. Try again.') }
    finally { changing.current = false; revision.current++; if (active.current) setPending(false) }
  }
  async function configureWeb(enabled: boolean) {
    if (!api.setWebsiteProtection) return
    const method = enabled ? route ?? web?.routingMethod : web?.routingMethod
    const value = await (method ? api.setWebsiteProtection(enabled, method) : api.setWebsiteProtection(enabled))
    if (active.current) { setWeb(value); setRoute(null) }
  }
  const appEnabled = apps?.applicationProtectionEnabled ?? false
  const appState = !apps ? 'Checking…' : !apps.monitorEnabled ? 'Desktop connection needed'
    : !appEnabled ? 'Off' : apps.applicationProtectionState === 'RESTORING' ? 'Restoring apps…'
      : ['UNAVAILABLE', 'DISABLED'].includes(apps.applicationDetection) || !['READY', 'ACTIVE'].includes(apps.applicationProtectionState ?? '') ? 'Setup needed' : 'On'
  const webState = !web ? 'Checking…' : web.runtimeEnabled === false ? 'Desktop connection needed' : !web.enabled ? 'Off'
    : web.braveLaunchPending ? 'Waiting for Brave to close…' : web.state === 'RESTORING' ? 'Restoring connection…'
      : web.state === 'VERIFIED' && web.routingVerified ? 'Connected' : web.state === 'READY' ? 'Browser setup needed' : 'Setup needed'
  const unavailable = readError ? 'Unavailable' : 'Checking…'
  const method = route ?? web?.routingMethod ?? 'SYSTEM_PROXY'
  function close() { if (!pending) { setDialog(null); setError('') } }
  return <section className="card-panel blocking-settings" aria-label="Blocking settings">
    <div className="blocking-settings-heading"><h2 tabIndex={-1} ref={heading}>Blocking</h2>
      {returnToSession && <button type="button" className="button button-secondary" onClick={returnToSession}>Back to session</button>}</div>
    <div className="preference-row"><div><strong>Block apps</strong><small role="status">{apps ? appState : unavailable}</small></div>
      <button type="button" className={`toggle-switch${appEnabled ? ' on' : ''}`} role="switch" aria-label="Block apps" aria-checked={appEnabled}
        disabled={pending || !apps || !api.setApplicationProtection || apps.applicationProtectionState === 'RESTORING' || (!appEnabled && !apps.monitorEnabled)}
        onClick={() => appEnabled ? void change(async () => { await api.setApplicationProtection!(false); if (active.current) setApps((value) => value ? { ...value, applicationProtectionEnabled: false } : value) }) : setDialog('apps')}><span /></button></div>
    <div className="preference-row"><div><strong>Block websites</strong><small role="status">{web ? webState : unavailable}</small></div>
      <button type="button" className={`toggle-switch${web?.enabled ? ' on' : ''}`} role="switch" aria-label="Block websites" aria-checked={web?.enabled ?? false}
        disabled={pending || !web || !api.setWebsiteProtection || web.state === 'RESTORING' || (!web.enabled && web.runtimeEnabled === false)}
        onClick={() => web?.enabled ? void change(() => configureWeb(false)) : setDialog('websites')}><span /></button></div>
    {web && (web.braveAvailable || web.routingMethod === 'BRAVE') && <div className="preference-row"><label htmlFor="blocking-browser"><strong>Browser connection</strong></label>
      <select id="blocking-browser" className="outlined-control" value={method} disabled={pending || web.braveLaunchPending || web.state === 'RESTORING'}
        onChange={(event) => setRoute(event.target.value as WebsiteRoutingMethod)}>
        <option value="SYSTEM_PROXY">System settings</option><option value="BRAVE" disabled={!web.braveAvailable}>Brave</option>
      </select></div>}
    <div className="blocking-settings-actions">
      {web?.enabled && route && route !== web.routingMethod && <button type="button" className="button button-secondary" disabled={pending || web.runtimeEnabled === false || web.state === 'RESTORING'}
        onClick={() => void change(() => configureWeb(true))}>Apply connection</button>}
      {web?.enabled && <button type="button" className="button button-secondary" disabled={pending || web.braveLaunchPending || !['READY', 'VERIFIED'].includes(web.state)}
        onClick={() => { setCopied(false); setDialog('browser') }}>Connect browser</button>}
      <button type="button" className="button button-quiet" onClick={() => setDialog('help')}>Setup help</button>
    </div>
    {error && !dialog && <p role="alert">{error}</p>}
    {dialog && <Modal title={dialog === 'apps' ? 'Turn on app blocking?' : dialog === 'websites' ? 'Turn on website blocking?' : dialog === 'browser' ? 'Connect your browser' : 'How blocking works'} onClose={close}>
      {dialog === 'apps' && <p>During focus time, FocusDesktop temporarily pauses the apps you marked Block. It resumes them when you pause, take a break or stop. Allow this?</p>}
      {dialog === 'websites' && <><p>During focus time, your selected websites and their subdomains are blocked. Pauses and breaks allow browsing.</p>
        <p>Your browser, and other apps using the configured proxy, send requests through FocusDesktop. HTTPS stays encrypted. No certificates, telemetry, cloud service or autonomous requests are used. Allow temporary connection changes with automatic restoration?</p></>}
      {dialog === 'browser' && (web?.routingMethod === 'BRAVE' ? <>
        <p>Your saved connection is reused across focus sessions and checked automatically when protection restarts with Brave open. You don’t need to close Brave for each session.</p>
        {web.verificationUrl && <p><a className="button button-secondary" href={web.verificationUrl} target="_blank" rel="noreferrer">Check connection</a></p>}
        {web.routingVerified && web.state === 'VERIFIED' ? <p>Brave is connected. You can start your focus session.</p> : <>
          <p>Not connected yet? Try the connection check in Brave first. After an interruption, Brave may take a few minutes to use the connection again.</p>
          <p>For first-time setup, or if Brave was reopened without its connection, save your work and choose Set up Brave. Then close all Brave windows within 60 seconds, including this app’s browser tab. We’ll reopen your existing profile. Nothing is force-closed.</p>
        </>}
      </> : <><p>Open this address in the browser you want to protect. A confirmation page means it is connected. Check each browser separately.</p>
        {web?.verificationUrl && <><label>Connection check<input readOnly aria-label="Browser connection check" value={web.verificationUrl} /></label>
          <button type="button" className="button button-secondary" onClick={() => { void navigator.clipboard.writeText(web.verificationUrl!).then(() => setCopied(true)).catch(() => setError('Copy the address from the field and open it in your browser.')) }}>{copied ? 'Copied' : 'Copy address'}</button></>}
      </>)}
      {dialog === 'help' && <><p>Mark distractions as Block, turn on the matching option here, then start a focus session. App blocking pauses selected apps; website blocking stops new requests to selected websites. Both release during pauses and breaks.</p>
        <p>Choose System settings for a supported system connection, or Brave for Linux desktops such as Hyprland. Brave setup keeps your profile and doesn’t edit system settings. Cached pages may still be visible.</p>
        <p>{web?.state === 'UNAVAILABLE' || web?.runtimeEnabled === false ? web.reason : apps?.applicationDetection === 'UNAVAILABLE' ? apps.applicationReason : 'Connect and check your browser before relying on website blocking.'}</p>
        {web?.runtimeEnabled === false && <p>For browser development, restart the backend with <code>./mvnw spring-boot:run -Dspring-boot.run.arguments=--focusdesktop.blocking.monitor-enabled=true</code>. The desktop app connects automatically.</p>}
        {web?.braveLaunchPending && <p>Close Brave within 60 seconds. If setup times out, retry after closing its background processes yourself.</p>}
      </>}
      {error && <p role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" className="button button-secondary" disabled={pending} onClick={close}>{dialog === 'help' ? 'Close' : 'Cancel'}</button>
        {dialog === 'apps' && <button type="button" className="button button-primary" disabled={pending} onClick={() => void change(async () => {
          const saved = await api.setApplicationProtection!(true); if (active.current) setApps((value) => value ? { ...value, applicationProtectionEnabled: saved.applicationsEnabled } : value)
        })}>{pending ? 'Saving…' : 'Allow app blocking'}</button>}
        {dialog === 'websites' && <button type="button" className="button button-primary" disabled={pending} onClick={() => void change(() => configureWeb(true))}>{pending ? 'Saving…' : 'Allow website blocking'}</button>}
        {dialog === 'browser' && web?.routingMethod === 'BRAVE' && !web.routingVerified && <button type="button" className="button button-primary" disabled={pending || !api.setupBrave || !web.braveAvailable || web.braveLaunchPending}
          onClick={() => void change(async () => { const value = await api.setupBrave!(); if (active.current) setWeb(value) })}>{pending ? 'Connecting…' : 'Set up Brave'}</button>}
      </div>
    </Modal>}
  </section>
}
