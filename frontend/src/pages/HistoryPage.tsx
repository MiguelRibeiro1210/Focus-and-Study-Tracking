import { focusHistory } from './futureData'

export function HistoryPage() {
  return <div className="history-page">
    <div className="history-toolbar">
      <div className="range-tabs" role="group" aria-label="History date range">
        <button className="range-tab" type="button" disabled>7 days</button>
        <button className="range-tab" type="button" disabled>30 days</button>
        <button className="range-tab" type="button" disabled>All time</button>
      </div>
      <div className="history-actions"><button className="outlined-control" type="button" disabled>Choose date range <span aria-hidden="true">⌄</span></button><button className="outlined-control" type="button" disabled>↓&nbsp; Export CSV</button></div>
    </div>

    <section className="history-stats" aria-label="Focus summary">
      {['Focus time', 'Sessions completed', 'Distractions blocked'].map((label) => <article className="stat-card" key={label}><p>{label}</p><span className="future-empty-state">No history yet</span></article>)}
    </section>

    <div className="history-content-grid">
      <section className="chart-panel card-panel" aria-label="Focus time chart">
        <div className="chart-heading"><h2>Your focus rhythm</h2><span>Focus time</span></div>
        <p className="future-empty-state chart-empty-state">A chart will appear when session history is available.</p>
      </section>
      <aside className="best-day-panel"><p className="best-day-label">FOCUS SUMMARY</p><h2>Your progress will appear here.</h2><p className="future-empty-state">There are no recorded sessions to summarize.</p></aside>
    </div>

    <section className="sessions-panel card-panel">
      <div className="sessions-panel-heading"><h2>Your sessions</h2><button type="button" className="outlined-control" disabled>All statuses <span aria-hidden="true">⌄</span></button></div>
      <div className="history-table" role="table" aria-label="Focus session history">
        <div className="history-row history-table-head" role="row"><span role="columnheader">PROJECT</span><span role="columnheader">DATE &amp; TIME</span><span role="columnheader">DURATION</span><span role="columnheader">BLOCKED</span><span role="columnheader">STATUS</span></div>
        {focusHistory.length === 0
          ? <div className="future-empty-state table-empty-state" role="row">Completed sessions will appear here when history is available.</div>
          : focusHistory.map((entry) => <div className="history-row" role="row" key={entry.id}><span role="cell">{entry.projectName}</span><span role="cell">{entry.startedAt}</span><span role="cell">{Math.round(entry.durationSeconds / 60)} min</span><span role="cell">{entry.blockedAttempts}</span><span role="cell">{entry.status}</span></div>)}
      </div>
    </section>
  </div>
}
