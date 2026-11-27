import { useEffect, useRef, useState } from 'react'
import { TimerScreen } from './TimerScreen'
import type { TimerScreenProps } from './TimerScreen'
import type { BlockingRulesApi } from '../api/blockingRulesApi'
import type { TimerOverride } from '../api/sessionApi'
import { Modal } from '../components/Modal'
import { checkBlockingReadiness } from '../pages/blockingReadiness'
import type { BlockingGap } from '../pages/blockingReadiness'

interface Request { name: string; override?: TimerOverride }
export function SessionStartGuard({ api, onStart, onSetup, timerProps }: {
  api: BlockingRulesApi; onStart(name: string, override?: TimerOverride): Promise<void>; onSetup(): void
  timerProps: Omit<TimerScreenProps, 'onRequestStart' | 'checkingProtection'>
}) {
  const [checking, setChecking] = useState(false)
  const [warning, setWarning] = useState<{ request: Request; gaps: BlockingGap[] } | null>(null)
  const [error, setError] = useState('')
  const busy = useRef(false)
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  async function start(request: Request) {
    try { await onStart(request.name, request.override) }
    catch { if (active.current) setError('The session could not start. Please try again.') }
    finally { busy.current = false; if (active.current) setChecking(false) }
  }
  async function request(name: string, override?: TimerOverride) {
    if (busy.current) return
    busy.current = true; setChecking(true); setError('')
    const gaps = await checkBlockingReadiness(api)
    if (!active.current) return
    if (gaps.length) { setWarning({ request: { name, override }, gaps }); setChecking(false) }
    else await start({ name, override })
  }
  function dismiss() { setWarning(null); busy.current = false }
  return <><TimerScreen {...timerProps} onRequestStart={(name, override) => { void request(name, override) }} checkingProtection={checking} />
    {error && <p role="alert">{error}</p>}
    {warning && <Modal title="Some distractions aren’t blocked yet" alert onClose={dismiss}>
      {warning.gaps.map((gap) => <p key={gap.kind}>{gap.message}</p>)}
      <p>You can finish setup now or start this session as it is. Any blocking that is already working stays on.</p>
      <div className="dialog-actions">
        <button type="button" className="button button-primary" onClick={() => { dismiss(); onSetup() }}>Set up blocking</button>
        <button type="button" className="button button-secondary" onClick={dismiss}>Cancel</button>
        <button type="button" className="button button-secondary" onClick={() => {
          const value = warning.request; setWarning(null); setChecking(true); void start(value)
        }}>Start without blocking</button>
      </div>
    </Modal>}
  </>
}
