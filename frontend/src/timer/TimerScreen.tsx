import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'
import type { SessionResponse } from '../api/sessionTypes'
import type { TimerAction, TimerController } from './useTimerSession'
import { focusHistory, overviewSummary } from '../pages/futureData'

interface TimerScreenProps {
  timer: TimerController
  onViewHistory?: () => void
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

function formatSummaryDuration(totalSeconds: number): string {
  const totalMinutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return hours > 0 ? `${hours} h ${String(minutes).padStart(2, '0')} min` : `${minutes} min`
}

function completedWorkIntervals(session: SessionResponse): number {
  const earlier = session.intervalType === 'WORK'
    ? Math.floor((session.intervalSequence - 1) / 2)
    : Math.floor(session.intervalSequence / 2)
  const currentEnded = session.intervalType === 'WORK'
    && (session.state === 'COMPLETED' || session.state === 'ABANDONED')
  return earlier + (currentEnded ? 1 : 0)
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

export function TimerScreen({ timer, onViewHistory }: TimerScreenProps) {
  const [confirmStop, setConfirmStop] = useState(false)
  const [durations, setDurations] = useState({ work: '', short: '', long: '' })
  const cancelStopRef = useRef<HTMLButtonElement>(null)
  const stopTriggerRef = useRef<HTMLButtonElement>(null)
  const session = timer.session
  const isRunning = session?.state === 'RUNNING'
  const isPaused = session?.state === 'PAUSED'
  const isWaiting = session?.state === 'WAITING_FOR_NEXT_INTERVAL'
  const configuredSeconds = session?.plannedDurationSeconds ?? 25 * 60
  const remaining = session ? timer.remainingSeconds : (timer.isLoading ? null : configuredSeconds)
  const progress = session && (isRunning || isPaused)
    ? Math.min(100, Math.max(0, ((session.plannedDurationSeconds - (remaining ?? 0)) / Math.max(1, session.plannedDurationSeconds)) * 100))
    : session?.state === 'COMPLETED' ? 100 : 0
  const ringStyle = { '--progress': `${progress}%` } as CSSProperties
  const dailyProgress = overviewSummary.dailyGoalSeconds > 0
    ? Math.min(100, Math.round((overviewSummary.focusSecondsToday / overviewSummary.dailyGoalSeconds) * 100))
    : 0
  const focusTimeDifferenceMinutes = Math.round((overviewSummary.focusSecondsToday - overviewSummary.focusSecondsYesterday) / 60)

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

  function actionButton(action: TimerAction, label: string, primary = false, accessibleName = label) {
    return <button className={`button ${primary ? 'button-primary' : 'button-secondary'}`} type="button"
      aria-label={accessibleName} disabled={timer.pendingAction !== null} onClick={() => void timer.runAction(action)}>
      {timer.pendingAction === action ? 'Please wait…' : label}
    </button>
  }

  function stopButton() {
    return <button ref={stopTriggerRef} className="button button-quiet" type="button" disabled={timer.pendingAction !== null} onClick={() => setConfirmStop(true)}>Stop</button>
  }

  function renderSessionActions() {
    if (timer.isLoading && !session) return <span className="loading-copy" role="status">Connecting to your local session…</span>
    if (!session || session.state === 'IDLE' || session.state === 'COMPLETED' || session.state === 'ABANDONED') {
      return actionButton('start', 'Start session', true, session ? 'Start a new session' : 'Start focus session')
    }
    if (session.state === 'RUNNING') return <>{actionButton('pause', 'Pause session', true)}{stopButton()}</>
    if (session.state === 'PAUSED') return <>{actionButton('resume', 'Resume session', true)}{session.intervalType !== 'WORK' && actionButton('skipBreak', 'Skip break')}{stopButton()}</>
    if (isWaiting) return <>{actionButton('startNextInterval', 'Start next interval', true)}{session.intervalType !== 'WORK' && session.intervalState === 'PENDING' && actionButton('skipBreak', 'Skip break')}{stopButton()}</>
    return null
  }

  return <div className="overview-layout">
    <section className="overview-stats" aria-label="Today's focus summary">
      <article className="stat-card"><p>Focus time today</p><strong>{formatSummaryDuration(overviewSummary.focusSecondsToday)}</strong><small>{focusTimeDifferenceMinutes >= 0 ? '+' : ''}{focusTimeDifferenceMinutes} min compared to yesterday</small></article>
      <article className="stat-card"><p>Working sessions completed</p><strong>{overviewSummary.completedSessionsToday}</strong><small>Sessions completed today</small></article>
      <article className="stat-card"><p>Distractions blocked</p><strong>{overviewSummary.distractionsBlockedToday}</strong><small>Blocked distractions today</small></article>
    </section>

    <div className="overview-columns">
      <section className="focus-panel" aria-label="Focus session setup">
        <div className="panel-title-row">
          <h2>Time to find your flow</h2>
          <span className="ready-badge">{timer.isLoading ? 'SYNCING' : statusLabel(session).toUpperCase()}</span>
        </div>
        <div className="focus-panel-body">
          <div className="timer-column">
            <div className="timer-art" style={ringStyle}>
              <img className="timer-dial" src="/figma/timer-dial.svg" width="236" height="236" alt="" />
              <span className="timer-stage">{session ? intervalLabel(session.intervalType).toUpperCase() : 'FOCUS SESSION'}</span>
              <span className="timer-display" role="timer" aria-label={`Time remaining ${formatTime(remaining)}`}>{formatTime(remaining)}</span>
              <span className="timer-caption">{timer.isLoading ? 'connecting to local session' : isRunning ? 'No rush. No distractions.' : isPaused ? 'Time paused' : isWaiting ? 'Ready when you are' : session ? statusLabel(session) : 'No rush. No distractions.'}</span>
            </div>
            {session && <div className="session-progress"><strong>{String(Math.max(1, Math.min(4, completedWorkIntervals(session) + (session.intervalType === 'WORK' ? 1 : 0)))).padStart(2, '0')}</strong><span>/ 04 until your long break</span></div>}
            {session && <span className="sr-only">{completedWorkIntervals(session)} work {completedWorkIntervals(session) === 1 ? 'interval' : 'intervals'} completed</span>}
            {session && <span className="sr-only">{intervalLabel(session.intervalType)}</span>}
            {!session && <span className="sr-only">Ready to focus</span>}
          </div>

          <div className="session-setup">
            <label className="field-label" htmlFor="project-placeholder">What will you work on?</label>
            <input className="project-field" id="project-placeholder" type="text" placeholder="Name your next project" />
            <p className="field-label">Session duration</p>
            <div className="duration-options" aria-label="Session duration in minutes">
              <label className="duration-field">Work<input className="duration-option" type="number" min="1" max="180" step="1" inputMode="numeric" aria-label="Work duration in minutes" value={durations.work} onChange={(event) => setDurations((current) => ({ ...current, work: event.target.value }))} /></label>
              <label className="duration-field">Short<input className="duration-option" type="number" min="1" max="60" step="1" inputMode="numeric" aria-label="Short break duration in minutes" value={durations.short} onChange={(event) => setDurations((current) => ({ ...current, short: event.target.value }))} /></label>
              <label className="duration-field">Long<input className="duration-option" type="number" min="1" max="120" step="1" inputMode="numeric" aria-label="Long break duration in minutes" value={durations.long} onChange={(event) => setDurations((current) => ({ ...current, long: event.target.value }))} /></label>
            </div>
            <div className="session-meta">
              <div><p className="field-label">Session controls</p><p className="profile-summary">Choose a session interval.</p></div>
            </div>
            <div className="overview-actions">{renderSessionActions()}</div>
            {timer.error && <div className="error-message" role="alert"><span>{timer.error}</span><button type="button" className="retry-button" onClick={timer.retry}>Retry sync</button></div>}
          </div>
        </div>
      </section>

      <aside className="daily-goal-panel">
        <h2>Focus is a habit.</h2><p className="goal-intro">And you’re making room for it.</p>
        <p className="goal-label">DAILY GOAL</p>
        <div className="goal-total"><strong>{formatSummaryDuration(overviewSummary.focusSecondsToday)}</strong><span>/ {formatSummaryDuration(overviewSummary.dailyGoalSeconds)}</span></div>
        <div className="goal-progress"><span style={{ width: `${dailyProgress}%` }} /></div>
        <p className="goal-complete">{dailyProgress}% complete</p>
        <p className="goal-remaining">{formatSummaryDuration(Math.max(0, overviewSummary.dailyGoalSeconds - overviewSummary.focusSecondsToday))} left to reach your goal.</p>
        <p className="goal-copy">Every session counts. Go at your own pace.</p>
        <p className="goal-footer">Taking a break is progress, too.</p>
      </aside>
    </div>

    <section className="recent-panel">
      <div className="recent-heading"><h2>Your progress today</h2><button className="text-action" type="button" onClick={onViewHistory}>View history <span aria-hidden="true">→</span></button></div>
      <div className="recent-table" role="table" aria-label="Recent focus sessions">
        <div className="recent-row recent-header" role="row"><span role="columnheader">SESSION</span><span role="columnheader">START</span><span role="columnheader">WORK</span><span role="columnheader">STATUS</span></div>
        {focusHistory.length === 0
          ? <div className="future-empty-state table-empty-state" role="row">Completed sessions will appear here when history is available.</div>
          : focusHistory.slice(0, 3).map((entry) => <div className="recent-row" role="row" key={entry.id}><span role="cell">{entry.projectName}</span><span role="cell">{entry.startedAt}</span><span role="cell">{Math.round(entry.durationSeconds / 60)} min</span><span role="cell">{entry.status}</span></div>)}
      </div>
    </section>

    {confirmStop && <div className="dialog-backdrop"><div className="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="stop-title" aria-describedby="stop-description" onKeyDown={handleDialogKeyDown}>
      <span className="dialog-icon" aria-hidden="true">Ⅱ</span><h2 id="stop-title">Stop this session?</h2>
      <p id="stop-description">This ends your current session. Its final status is based on the focus time recorded by FocusDesktop.</p>
      <div className="dialog-actions"><button ref={cancelStopRef} className="button button-secondary" type="button" onClick={() => { setConfirmStop(false); stopTriggerRef.current?.focus() }}>Keep going</button>
        <button className="button button-danger" type="button" disabled={timer.pendingAction !== null} onClick={() => { setConfirmStop(false); void timer.runAction('stop') }}>{timer.pendingAction === 'stop' ? 'Stopping…' : 'Stop session'}</button></div>
    </div></div>}
  </div>
}
