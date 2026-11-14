import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'
import type { SessionResponse } from '../api/sessionTypes'
import type { TimerAction, TimerController } from './useTimerSession'

interface TimerScreenProps {
  timer: TimerController
}

function intervalLabel(type: SessionResponse['intervalType']): string {
  switch (type) {
    case 'WORK': return 'Focus interval'
    case 'SHORT_BREAK': return 'Short break'
    case 'LONG_BREAK': return 'Long break'
  }
}

function formatTime(totalSeconds: number | null): string {
  if (totalSeconds === null) return '—:—'
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function completedWorkIntervals(session: SessionResponse): number {
  const earlierWorkIntervals = session.intervalType === 'WORK'
    ? Math.floor((session.intervalSequence - 1) / 2)
    : Math.floor(session.intervalSequence / 2)
  const currentWorkWasEnded = session.intervalType === 'WORK'
    && (session.state === 'COMPLETED' || session.state === 'ABANDONED')
  return earlierWorkIntervals + (currentWorkWasEnded ? 1 : 0)
}

function statusLabel(session: SessionResponse | null): string {
  if (!session) return 'Ready to focus'
  switch (session.state) {
    case 'RUNNING': return 'In progress'
    case 'PAUSED': return 'Paused'
    case 'WAITING_FOR_NEXT_INTERVAL': return 'Ready for next interval'
    case 'COMPLETED': return 'Session complete'
    case 'ABANDONED': return 'Session ended'
    case 'IDLE': return 'Ready to focus'
  }
}

export function TimerScreen({ timer }: TimerScreenProps) {
  const [confirmStop, setConfirmStop] = useState(false)
  const cancelStopRef = useRef<HTMLButtonElement>(null)
  const stopTriggerRef = useRef<HTMLButtonElement>(null)
  const session = timer.session
  const isRunning = session?.state === 'RUNNING'
  const isPaused = session?.state === 'PAUSED'
  const isWaiting = session?.state === 'WAITING_FOR_NEXT_INTERVAL'
  const progress = session && (isRunning || isPaused)
    ? Math.min(100, Math.max(0, ((session.plannedDurationSeconds - (timer.remainingSeconds ?? 0)) / Math.max(1, session.plannedDurationSeconds)) * 100))
    : session?.state === 'COMPLETED' ? 100 : 0
  const ringStyle = { '--progress': `${progress}%` } as CSSProperties

  useEffect(() => {
    if (confirmStop) cancelStopRef.current?.focus()
  }, [confirmStop])

  function handleDialogKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      setConfirmStop(false)
      stopTriggerRef.current?.focus()
    }
    if (event.key !== 'Tab') return
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'))
    const first = buttons[0]
    const last = buttons.at(-1)
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  function actionButton(action: TimerAction, label: string, primary = false) {
    return (
      <button
        className={primary ? 'button button-primary' : 'button button-secondary'}
        type="button"
        disabled={timer.pendingAction !== null}
        onClick={() => void timer.runAction(action)}
      >
        {timer.pendingAction === action ? 'Please wait…' : label}
      </button>
    )
  }

  function stopButton() {
    return <button ref={stopTriggerRef} className="button button-quiet" type="button" disabled={timer.pendingAction !== null} onClick={() => setConfirmStop(true)}>Stop</button>
  }

  function cancelStopConfirmation() {
    setConfirmStop(false)
    stopTriggerRef.current?.focus()
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#timer" aria-label="FocusDesktop timer home">
          <span className="brand-mark" aria-hidden="true">f</span>
          <span>focusdesktop</span>
        </a>
        <span className="local-status"><span className="status-dot" /> Local session</span>
      </header>

      <section className="timer-page" id="timer" aria-labelledby="page-title">
        <div className="page-intro">
          <p className="eyebrow">MAKE SPACE FOR WHAT MATTERS</p>
          <h1 id="page-title">One thing at a time.</h1>
          <p className="description">Settle in, find your rhythm, and let the next small step be enough.</p>
        </div>

        <section className="timer-card" aria-label="Focus timer">
          <div className="timer-card-heading">
            <div>
              <p className="interval-label">{session ? intervalLabel(session.intervalType) : 'Your focus session'}</p>
              <p className="session-status">{timer.isLoading ? 'Checking your session…' : statusLabel(session)}</p>
            </div>
            {session && <span className="sequence-badge">Interval {session.intervalSequence}</span>}
          </div>

          <div className="timer-display-wrap">
            <div className="timer-ring" style={ringStyle}>
              <div className="timer-ring-center">
                <span className="timer-display" role="timer" aria-label={`Time remaining ${formatTime(timer.remainingSeconds)}`}>
                  {formatTime(timer.remainingSeconds)}
                </span>
                <span className="timer-caption">
                  {isRunning && timer.remainingSeconds === 0 ? 'Waiting for next interval' : isRunning ? 'remaining' : isWaiting ? 'ready when you are' : session?.state === 'PAUSED' ? 'time paused' : session ? 'session time' : 'start whenever you’re ready'}
                </span>
              </div>
            </div>
          </div>

          {session && (
            <div className="session-progress" aria-label="Session progress">
              <span className="progress-indicator" aria-hidden="true">✳</span>
              <span>{completedWorkIntervals(session)} work {completedWorkIntervals(session) === 1 ? 'interval' : 'intervals'} completed</span>
            </div>
          )}

          <div className="timer-actions">
            {timer.isLoading && !session ? (
              <span className="loading-copy" role="status">Connecting to your local session…</span>
            ) : !session || session.state === 'IDLE' || session.state === 'COMPLETED' || session.state === 'ABANDONED' ? (
              actionButton('start', session ? 'Start a new session' : 'Start focus session', true)
            ) : session.state === 'RUNNING' ? (
              <>
                {actionButton('pause', 'Pause session', true)}
                {stopButton()}
              </>
            ) : session.state === 'PAUSED' ? (
              <>
                {actionButton('resume', 'Resume session', true)}
                {stopButton()}
              </>
            ) : isWaiting ? (
              <>
                {actionButton('startNextInterval', 'Start next interval', true)}
                {session.intervalType !== 'WORK' && session.intervalState === 'PENDING' && actionButton('skipBreak', 'Skip break')}
                {stopButton()}
              </>
            ) : null}
          </div>

          {timer.error && (
            <div className="error-message" role="alert">
              <span>{timer.error}</span>
              <button type="button" className="retry-button" onClick={timer.retry}>Retry sync</button>
            </div>
          )}
        </section>

        <p className="page-footnote">Your session stays on this device. The timer follows the local FocusDesktop service.</p>
      </section>

      {confirmStop && (
        <div className="dialog-backdrop">
          <div className="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="stop-title" aria-describedby="stop-description" onKeyDown={handleDialogKeyDown}>
            <span className="dialog-icon" aria-hidden="true">Ⅱ</span>
            <h2 id="stop-title">Stop this session?</h2>
            <p id="stop-description">This ends your current session. Its final status is based on the focus time recorded by FocusDesktop.</p>
            <div className="dialog-actions">
              <button ref={cancelStopRef} className="button button-secondary" type="button" onClick={cancelStopConfirmation}>Keep going</button>
              <button
                className="button button-danger"
                type="button"
                disabled={timer.pendingAction !== null}
                onClick={() => {
                  setConfirmStop(false)
                  void timer.runAction('stop')
                }}
              >
                {timer.pendingAction === 'stop' ? 'Stopping…' : 'Stop session'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
