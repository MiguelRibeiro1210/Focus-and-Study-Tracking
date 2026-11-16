export function SettingsPage() {
  return <div className="settings-layout">
    <div className="settings-main-column">
      <section className="preferences-panel card-panel">
        <h2>The app adapts to you</h2>
        <div className="preference-row"><div><strong>Start the next session automatically</strong><small>Stay in control of when each session starts.</small></div><button type="button" className="toggle-switch" aria-label="Automatic next session setting unavailable" disabled><span /></button></div>
        <div className="preference-row theme-preference-row"><div><strong>Application Theme</strong><small>Your focus space, looking the way you like.</small></div><button type="button" className="outlined-control theme-select" disabled aria-label="Application theme: Light Theme">Light Theme <span aria-hidden="true">⌄</span></button></div>
      </section>

      <section className="saved-profiles card-panel">
        <div className="saved-profiles-heading"><div><h2>Timer profiles</h2><p>Timer profiles will be available here when profile settings are implemented.</p></div></div>
        <p className="future-empty-state">No timer profiles are configured.</p>
      </section>

      <section className="shortcuts-panel card-panel">
        <h2>Keyboard shortcuts</h2>
        <p className="future-empty-state">Shortcut settings are not available yet.</p>
      </section>
    </div>

    <aside className="settings-side-column">
      <section className="privacy-panel">
        <img src="/figma/lock.svg" width="24" height="24" alt="" />
        <h2>Your focus is personal.<br />Your data is, too.</h2>
        <p>Session data is handled by this app on your device.</p>
      </section>
      <section className="local-data-panel card-panel">
        <h2>Data controls</h2>
        <p className="future-empty-state">Export, storage, and history controls will appear here when available.</p>
      </section>
    </aside>
  </div>
}
