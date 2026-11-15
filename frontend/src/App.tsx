import { useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { isDesktopShell } from './api/nativeTransport'
import './App.css'
import { useTimerSession } from './timer/useTimerSession'
import { TimerScreen } from './timer/TimerScreen'

interface BackendStatus { phase: string; message: string; diagnosticsPath?: string }

function BackendStartup() {
  const [status, setStatus] = useState<BackendStatus>({ phase: 'starting', message: 'Starting the local API…' })
  const [retrying, setRetrying] = useState(false)

  useEffect(() => {
    let active = true
    let unlisten: (() => void) | undefined
    void listen<BackendStatus>('backend-status', ({ payload }) => { if (active) setStatus(payload) })
      .then((stop) => { unlisten = stop })
    void invoke<BackendStatus>('backend_status').then((next) => { if (active) setStatus(next) })
    return () => { active = false; unlisten?.() }
  }, [])

  async function retry() {
    setRetrying(true)
    try {
      await invoke('retry_backend')
      setStatus({ phase: 'starting', message: 'Retrying the local API…' })
    } catch (error) {
      setStatus({ phase: 'failed', message: String(error) })
    } finally { setRetrying(false) }
  }

  if (status.phase === 'ready') return <TimerApplication />
  const failed = status.phase === 'failed'
  return <main className="startup-screen">
    <section className="startup-card" aria-live="polite">
      <div className={`startup-indicator ${failed ? 'failed' : ''}`} aria-hidden="true">{failed ? '!' : '◌'}</div>
      <h1>{failed ? 'FocusDesktop could not start' : 'Preparing FocusDesktop'}</h1>
      <p>{status.message}</p>
      {status.diagnosticsPath && <p className="startup-diagnostics">Diagnostics: <code>{status.diagnosticsPath}</code></p>}
      <div className="startup-actions">
        {failed && <button className="primary-button" disabled={retrying} onClick={() => void retry()}>{retrying ? 'Retrying…' : 'Retry'}</button>}
        <button className="secondary-button" onClick={() => void getCurrentWindow().close()}>Quit</button>
      </div>
    </section>
  </main>
}

function TimerApplication() {
  const timer = useTimerSession()
  return <TimerScreen timer={timer} />
}

function App() {
  return isDesktopShell() ? <BackendStartup /> : <TimerApplication />
}

export default App
