import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { createDistractionLogApi } from '../api/distractionLogApi'
import type { DistractionLogApi, DistractionLogEntry, DistractionLogPage, DistractionLogQuery } from '../api/distractionLogApi'
import { targetLabel } from '../api/blockingRulesApi'

export function DistractionHistory({ api: suppliedApi }: { api?: DistractionLogApi }) {
  const [api] = useState(() => suppliedApi ?? createDistractionLogApi())
  const [query, setQuery] = useState<DistractionLogQuery>({ page: 0, size: 20 })
  const [draft, setDraft] = useState({ from: '', to: '' })
  const [validation, setValidation] = useState('')
  const [revision, setRevision] = useState(0)
  const key = JSON.stringify([query, revision])
  const [loaded, setLoaded] = useState<{ key: string; data: DistractionLogPage | null; error: string } | null>(null)
  useEffect(() => {
    let active = true
    api.list(query).then((data) => { if (active) setLoaded({ key, data, error: '' }) })
      .catch((cause: unknown) => { if (active) setLoaded({ key, data: null, error: cause instanceof Error ? cause.message : 'Distraction history could not be loaded.' }) })
    return () => { active = false }
  }, [api, key, query])
  const loading = loaded?.key !== key
  const data = !loading ? loaded?.data : null
  const error = !loading ? loaded?.error : ''
  function apply(event: FormEvent) {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) { setValidation('Start date must not be after end date.'); return }
    setValidation(''); setQuery((previous) => ({ ...previous, from: draft.from || undefined, to: draft.to || undefined, page: 0 }))
  }
  return <section className="card-panel distraction-history" aria-labelledby="distraction-history-title" aria-busy={loading}>
    <div className="distraction-history-heading"><div><h2 id="distraction-history-title">Distraction history</h2>
      <p>Local observations and confirmed blocking outcomes.</p></div>
      <button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Refresh history</button></div>
    <form className="distraction-history-filters" onSubmit={apply}>
      <label>From<input type="date" aria-label="Distraction history from" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} /></label>
      <label>To<input type="date" aria-label="Distraction history to" value={draft.to} onChange={(event) => setDraft({ ...draft, to: event.target.value })} /></label>
      <button type="submit" disabled={loading}>Apply dates</button>
      {query.sessionId && <><span>Showing one session.</span><button type="button" disabled={loading}
        onClick={() => setQuery((previous) => ({ ...previous, sessionId: undefined, page: 0 }))}>Show all sessions</button></>}
    </form>
    {validation && <p role="alert">{validation}</p>}
    {loading && <p role="status">Loading distraction history…</p>}
    {error && <p role="alert">{error} <button type="button" onClick={() => setRevision((value) => value + 1)}>Retry history</button></p>}
    {data && <>
      {data.items.length === 0 ? <p>No distraction observations match these filters.</p>
        : <div className="distraction-history-scroll"><table><caption className="sr-only">Distraction observations</caption>
          <thead><tr><th>When</th><th>Observed target</th><th>Action / outcome</th><th>Session</th></tr></thead>
          <tbody>{data.items.map((entry) => <tr key={entry.detectionId}>
            <td><time dateTime={entry.occurredAt}>{new Date(entry.occurredAt).toLocaleString()}</time></td>
            <td><strong>{entry.host ?? entry.application?.processName ?? entry.application?.executablePath}</strong><small>Rule target: {targetLabel(entry.target)}</small></td>
            <td><strong>{outcomeLabel(entry)}</strong>{entry.failureReason && <small>{entry.failureReason}</small>}</td>
            <td><button type="button" disabled={loading || query.sessionId === entry.sessionId}
              onClick={() => setQuery((previous) => ({ ...previous, sessionId: entry.sessionId, page: 0 }))}>Only this session</button></td>
          </tr>)}</tbody></table></div>}
      <div className="distraction-history-pagination"><span>{data.total} observation{data.total === 1 ? '' : 's'} · Page {data.page + 1} of {Math.max(1, Math.ceil(data.total / data.size))}</span>
        <button type="button" disabled={loading || query.page === 0} onClick={() => setQuery((previous) => ({ ...previous, page: previous.page - 1 }))}>Previous observations</button>
        <button type="button" disabled={loading || (query.page + 1) * query.size >= data.total} onClick={() => setQuery((previous) => ({ ...previous, page: previous.page + 1 }))}>Next observations</button></div>
    </>}
  </section>
}
function outcomeLabel(entry: DistractionLogEntry) {
  if (entry.action === 'LOG') return 'Logged'
  if (entry.action === 'WARN') return 'Warning'
  return entry.outcome === 'BLOCKED' ? 'Blocked' : entry.outcome === 'FAILED' ? 'Blocking failed' : 'Awaiting confirmation'
}
