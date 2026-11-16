import { distractionRules } from './futureData'

export function DistractionsPage() {
  return <div className="distractions-page">
    <section className="boundaries-banner">
      <div><h2>Healthy boundaries for distractions.</h2><p>Set up rules for websites and applications.</p></div>
      <button type="button" className="button button-primary">＋ Add distraction</button>
    </section>

    <div className="distractions-layout">
      <section className="distraction-list-panel" aria-label="Protected distractions">
        <div className="distraction-tools">
          <div className="filter-tabs" role="group" aria-label="Filter distractions">
            <button className="filter-tab selected" type="button">All</button>
            <button className="filter-tab" type="button">Websites</button>
            <button className="filter-tab" type="button">Apps</button>
          </div>
          <input className="search-field" type="search" placeholder="Search name or domain..." aria-label="Search distractions" />
        </div>
        <div className="distraction-table" role="table" aria-label="Distraction rules">
          <div className="distraction-row distraction-table-head" role="row"><span role="columnheader">DISTRACTION</span><span role="columnheader">TYPE</span><span role="columnheader">BLOCK</span></div>
          {distractionRules.length === 0
            ? <div className="future-empty-state table-empty-state" role="row">No distraction rules have been added.</div>
            : distractionRules.map((item) => <div className="distraction-row" role="row" key={item.id}>
              <span className="distraction-name" role="cell"><span className="distraction-icon" aria-hidden="true">{item.kind === 'website' ? '◎' : '▣'}</span><span><strong>{item.name}</strong><small>{item.target}</small></span></span>
              <span className="distraction-type" role="cell">{item.kind === 'website' ? 'Website' : 'Application'}</span>
              <span className="distraction-toggle-cell" role="cell"><button type="button" className={`toggle-switch${item.enabled ? ' on' : ''}`} role="switch" aria-checked={item.enabled} aria-label={`Block ${item.name}`}><span /></button></span>
            </div>)}
        </div>
      </section>

      <aside className="distraction-side-column">
        <section className="profile-panel card-panel">
          <h2>Focus profiles</h2>
          <p className="muted-copy">Reusable distraction rule sets.</p>
          <p className="future-empty-state">No focus profiles are configured.</p>
          <button className="text-action manage-profiles" type="button">Manage profiles <span aria-hidden="true">→</span></button>
        </section>
        <section className="boundaries-info card-panel">
          <h2>Distraction controls</h2>
          <p className="future-empty-state">Blocking behavior can be configured here when it is implemented.</p>
        </section>
      </aside>
    </div>
  </div>
}
