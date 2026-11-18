import { useEffect, useMemo, useState } from 'react'
import { sessionApi, type SessionApi } from '../api/sessionApi'
import type { AnalyticsSummary, SessionHistoryItem, SessionHistoryStatus } from '../api/sessionTypes'
import { createHistoryQuery, formatFocusDuration, formatHistoryDateTime, getHistoryDateRange, validateLocalDateRange, type HistoryRange, type LocalDateRange } from './historyRange'
import { formatDashboardDuration, mapAnalyticsSummary } from './historyDashboard'
import { buildHistoryCsv, saveHistoryCsv } from './historyCsv'
import { useSessionHistory } from './useSessionHistory'

const PAGE_SIZE = 20
const RANGES: HistoryRange[] = ['7 days', '30 days', 'All time']

interface HistoryPageProps {
  api?: Pick<SessionApi, 'getSessionHistory' | 'getAnalyticsSummary'>
}

export function HistoryPage({ api = sessionApi }: HistoryPageProps) {
  const [range, setRange] = useState<HistoryRange>('7 days')
  const [customRange, setCustomRange] = useState<LocalDateRange | null>(null)
  const [datePickerOpen, setDatePickerOpen] = useState(false)
  const [draftRange, setDraftRange] = useState<LocalDateRange>({ from: '', to: '' })
  const [dateRangeError, setDateRangeError] = useState('')
  const [status, setStatus] = useState<SessionHistoryStatus | ''>('')
  const [historyNotice, setHistoryNotice] = useState('')
  const [page, setPage] = useState(0)
  const [today] = useState(() => new Date())
  const query = useMemo(() => {
    const preset = createHistoryQuery(range, status || undefined, page, today)
    if (!customRange) return preset
    return { ...preset, from: customRange.from, to: customRange.to }
  }, [range, customRange, status, page, today])
  const history = useSessionHistory(query, api)
  const result = history.data
  const summaryQuery = useMemo(() => customRange ?? getHistoryDateRange(range, today), [customRange, range, today])
  const summaryKey = JSON.stringify([summaryQuery?.from, summaryQuery?.to])
  const [dashboardState, setDashboardState] = useState<{ key: string; summary: AnalyticsSummary | null; error: boolean } | null>(null)
  useEffect(() => {
    let current = true
    void api.getAnalyticsSummary(summaryQuery ?? {}).then((summary) => {
      if (current) setDashboardState({ key: summaryKey, summary, error: false })
    }).catch(() => {
      if (current) setDashboardState({ key: summaryKey, summary: null, error: true })
    })
    return () => { current = false }
  }, [api, summaryKey, summaryQuery])
  const dashboardSummary = dashboardState?.key === summaryKey ? dashboardState.summary : null
  const dashboardLoading = dashboardState?.key !== summaryKey
  const dashboardError = dashboardState?.key === summaryKey && dashboardState.error
  const dashboard = useMemo(() => dashboardSummary ? mapAnalyticsSummary(dashboardSummary) : {
    focusSeconds: 0, completedSessions: 0, distractionsBlocked: 0, week: [], bestDay: null,
  }, [dashboardSummary])
  const pageCount = result ? Math.ceil(result.total / PAGE_SIZE) : 0
  const [historyProbe, setHistoryProbe] = useState<{ key: string; hasAny: boolean } | null>(null)
  const shouldProbeHistory = !history.loading && !history.error && result?.total === 0
    && !(range === 'All time' && !status)
  const probeKey = JSON.stringify([range, status, query.from, query.to])
  useEffect(() => {
    if (!shouldProbeHistory) return
    let current = true
    void api.getSessionHistory({ page: 0, size: 1 }).then((allHistory) => {
      if (current) setHistoryProbe({ key: probeKey, hasAny: allHistory.total > 0 })
    }).catch(() => {
      if (current) setHistoryProbe({ key: probeKey, hasAny: true })
    })
    return () => { current = false }
  }, [api, shouldProbeHistory, probeKey, result])
  const hasAnyHistory = range === 'All time' && !status
    ? false
    : historyProbe?.key === probeKey ? historyProbe.hasAny : null

  function selectRange(next: HistoryRange) {
    setRange(next)
    setCustomRange(null)
    setPage(0)
    setHistoryNotice('')
    setDatePickerOpen(false)
  }

  function openDatePicker() {
    const dates = customRange ?? getHistoryDateRange(range, today) ?? getHistoryDateRange('7 days', today)!
    setDraftRange(dates)
    setDateRangeError('')
    setDatePickerOpen((open) => !open)
  }

  function applyDateRange() {
    const error = validateLocalDateRange(draftRange.from, draftRange.to)
    if (error) {
      setDateRangeError(error)
      return
    }
    setCustomRange(draftRange)
    setPage(0)
    setHistoryNotice('')
    setDatePickerOpen(false)
  }

  async function exportCsv() {
    setHistoryNotice('')
    try {
      const items: SessionHistoryItem[] = []
      let exportPage = 0
      let total = 0
      do {
        const response = await api.getSessionHistory({
          ...(customRange ?? getHistoryDateRange(range, today) ?? {}),
          status: status || undefined,
          page: exportPage,
          size: 100,
        })
        items.push(...response.items)
        total = response.total
        exportPage += 1
      } while (items.length < total)
      if (items.length === 0) {
        setHistoryNotice('No sessions available to export.')
        return
      }
      if (await saveHistoryCsv(buildHistoryCsv(items))) setHistoryNotice('History exported as CSV.')
    } catch {
      setHistoryNotice('Could not export session history. Please retry.')
    }
  }

  return <div className="history-page">
    <div className="history-toolbar">
      <div className="range-tabs" role="group" aria-label="History date range">
        {RANGES.map((option) => <button className={`range-tab${!customRange && range === option ? ' selected' : ''}`} type="button"
          key={option} aria-pressed={!customRange && range === option} onClick={() => selectRange(option)}>{option}</button>)}
      </div>
      <div className="history-export-tools">
        <button className="outlined-control history-date-range" type="button" aria-expanded={datePickerOpen}
          aria-controls="history-date-range-picker" onClick={openDatePicker}>Choose date range <span aria-hidden="true">⌄</span></button>
        <button className="outlined-control" type="button" onClick={exportCsv}>↓&nbsp; Export CSV</button>
      </div>
    </div>
    {datePickerOpen && <form id="history-date-range-picker" className="history-date-range-picker card-panel"
      onSubmit={(event) => { event.preventDefault(); applyDateRange() }}>
      <label>Start date<input aria-label="Start date" type="date" value={draftRange.from}
        onChange={(event) => { setDraftRange((current) => ({ ...current, from: event.target.value })); setDateRangeError('') }} /></label>
      <label>End date<input aria-label="End date" type="date" value={draftRange.to}
        onChange={(event) => { setDraftRange((current) => ({ ...current, to: event.target.value })); setDateRangeError('') }} /></label>
      {dateRangeError && <p className="history-date-range-error" role="alert">{dateRangeError}</p>}
      <div className="history-date-range-actions">
        <button className="outlined-control" type="button" onClick={() => { setDatePickerOpen(false); setDateRangeError('') }}>Cancel</button>
        <button className="primary-control" type="submit">Apply</button>
      </div>
    </form>}
    {historyNotice && <p className="history-notice" role="status">{historyNotice}</p>}

    <section className="history-dashboard" aria-label="History summary">
      <div className="history-stat-grid">
        <article className="history-stat-card card-panel">
          <span>FOCUS TIME</span>
          <strong>{dashboardLoading || dashboardError ? '—' : formatDashboardDuration(dashboard.focusSeconds)}</strong>
          <small>{dashboardError ? 'Could not load summary' : customRange ? 'Across the selected dates' : range === 'All time' ? 'Across all recorded sessions' : `Across the last ${range.toLowerCase()}`}</small>
        </article>
        <article className="history-stat-card card-panel">
          <span>SESSIONS COMPLETED</span>
          <strong>{dashboardLoading || dashboardError ? '—' : dashboard.completedSessions}</strong>
          <small>Completed focus sessions in this range</small>
        </article>
        <article className="history-stat-card card-panel">
          <span>DISTRACTIONS BLOCKED</span>
          <strong>{dashboard.distractionsBlocked}</strong>
          <small>Not tracked yet</small>
        </article>
      </div>

      <div className="history-insights-grid">
        <section className="history-rhythm card-panel" aria-labelledby="rhythm-heading">
          <div className="history-insight-heading"><h2 id="rhythm-heading">Your rhythm {customRange ? 'in this range' : 'this week'}</h2><span>Focus time</span></div>
          {dashboardLoading || dashboardError ? <p className="history-chart-empty" role="status">{dashboardLoading ? 'Loading focus time…' : 'Focus time could not be loaded.'}</p> : <div className="history-chart" role="img" aria-label={`${customRange ? 'Selected-range' : 'Weekly'} focus time: ${dashboard.week.map((day) => `${new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(day.date)} ${formatDashboardDuration(day.focusSeconds)}`).join(', ')}`}>
            <div className="chart-axis"><span>{formatDashboardDuration(Math.max(7200, ...dashboard.week.map((day) => day.focusSeconds)))}</span><span>{formatDashboardDuration(Math.max(3600, ...dashboard.week.map((day) => day.focusSeconds)) / 2)}</span><span>0</span></div>
            <div className="chart-bars" style={{ gridTemplateColumns: `repeat(${Math.max(dashboard.week.length, 1)}, minmax(0, 1fr))` }}>{dashboard.week.map((day) => {
              const max = Math.max(7200, ...dashboard.week.map((entry) => entry.focusSeconds))
              const height = day.focusSeconds ? Math.max(6, day.focusSeconds / max * 100) : 0
              const todayBar = day.date.toDateString() === today.toDateString()
              return <div className="chart-day" key={day.date.toISOString()}>
                <div className="chart-track"><span className={`chart-bar${todayBar ? ' today' : ''}`} style={{ height: `${height}%` }} /></div>
                <small>{new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(day.date)}, {day.date.getDate()}</small>
              </div>
            })}</div>
          </div>}
          {!dashboardLoading && !dashboardError && dashboard.week.every((day) => day.focusSeconds === 0) && <p className="history-chart-empty">No focus sessions recorded {customRange ? 'in this range' : 'this week'}.</p>}
        </section>
        <aside className="history-best-day" aria-label="Best day">
          <span>YOUR BEST DAY</span>
          {dashboardLoading ? <p className="history-best-empty">Loading…</p> : dashboardError ? <p className="history-best-empty">Best day could not be loaded.</p> : dashboard.bestDay ? <>
            <h2>{dashboard.bestDay.date.toDateString() === today.toDateString() ? 'Today, you made room for what matters.' : new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'short', day: 'numeric' }).format(dashboard.bestDay.date)}</h2>
            <strong>{formatDashboardDuration(dashboard.bestDay.focusSeconds)}</strong>
            <small>of focus · {dashboard.bestDay.sessionCount} {dashboard.bestDay.sessionCount === 1 ? 'session' : 'sessions'}</small>
            <p>Consistency, not perfection.</p>
          </> : <p className="history-best-empty">Your best day will appear when you record a focus session.</p>}
        </aside>
      </div>
    </section>

    <section className="sessions-panel card-panel" aria-labelledby="history-heading">
      <div className="sessions-panel-heading"><h2 id="history-heading">Your sessions</h2>
        <label className="history-filter">Status
          <select aria-label="Session status" value={status} onChange={(event) => { setStatus(event.target.value as SessionHistoryStatus | ''); setPage(0) }}>
            <option value="">All statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="ABANDONED">Abandoned</option>
          </select>
        </label>
      </div>
      {history.loading && <p className="history-state" role="status">Loading session history…</p>}
      {history.error && <div className="history-error" role="alert"><span>{history.error}</span>
        <button type="button" className="retry-button" onClick={history.retry}>Retry</button></div>}

      {!history.loading && !history.error && result && result.items.length === 0
        && <p className="history-state" role="status">{hasAnyHistory === null
          ? 'Checking for recorded sessions…'
          : hasAnyHistory ? 'No sessions match the selected range and status.' : 'No sessions recorded yet.'}</p>}

      {!history.loading && !history.error && result && result.items.length > 0 && <>
        <div className="history-table" role="table" aria-label="Focus session history">
          <div className="history-row history-table-head" role="row">
            <span role="columnheader">PROJECT</span><span role="columnheader">DATE &amp; TIME</span>
            <span role="columnheader">FOCUS TIME</span><span role="columnheader">STATUS</span>
          </div>
          {result.items.map((item) => <div className="history-row" role="row" key={item.sessionId}>
            <span role="cell">{item.projectName}</span>
            <span role="cell">{formatHistoryDateTime(item.startedAt)}</span>
            <span role="cell">{formatFocusDuration(item.actualFocusDurationSeconds)}</span>
            <span role="cell"><span className={`status-pill status-${item.status.toLowerCase()}`}>
              {item.status === 'COMPLETED' ? 'Completed' : 'Abandoned'}
            </span></span>
          </div>)}
        </div>
        <div className="table-footer">
          <span>Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, result.total)} of {result.total} sessions</span>
          <div className="pagination" aria-label="Session history pagination">
            <button type="button" aria-label="Previous page" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
            <span aria-live="polite">Page {page + 1} of {pageCount}</span>
            <button type="button" aria-label="Next page" disabled={page + 1 >= pageCount} onClick={() => setPage((value) => value + 1)}>Next</button>
          </div>
        </div>
      </>}
    </section>
  </div>
}
