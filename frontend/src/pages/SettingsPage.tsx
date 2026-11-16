import { useState } from 'react'

const profiles = [
  { name: 'Classic', detail: '25 min focus · 5 min short · 15 min long', current: true },
  { name: 'Deep work', detail: '50 min focus · 10 min short · 20 min long' },
  { name: 'Quick session', detail: '15 min focus · 3 min short · 10 min long' },
]

export function SettingsPage() {
  const [notificationsEnabled, setNotificationsEnabled] = useState(true)
  const [autoStartEnabled, setAutoStartEnabled] = useState(false)
  const [theme, setTheme] = useState('Light Theme')

  return <div className="settings-layout">
    <div className="settings-main-column">
      <section className="saved-profiles card-panel">
        <div className="saved-profiles-heading"><div><h2>Saved timer profiles</h2><p>Choose a rhythm, then use it on Overview.</p></div><button type="button" className="outlined-control add-profile">＋ Add profile</button></div>
        <div className="profile-list">{profiles.map((profile) => <div className="saved-profile-row" key={profile.name}>
          <div><strong>{profile.name}</strong><small>{profile.detail}</small></div>
          <button type="button" className={`profile-action${profile.current ? ' default' : ''}`}>{profile.current ? 'DEFAULT' : 'Set default'}</button>
        </div>)}</div>
        <div className="daily-goal-setting"><div><strong>Daily goal</strong><small>A long break after every 4 focus sessions.</small></div><select className="outlined-control daily-goal-select" aria-label="Daily goal" defaultValue="3 hours"><option>1 hour</option><option>2 hours</option><option>3 hours</option><option>4 hours</option></select></div>
      </section>

      <section className="preferences-panel card-panel">
        <h2>The app adapts to you</h2>
        <div className="preference-row"><div><strong>Gentle notifications</strong><small>Get notified when a session or break ends.</small></div><button type="button" className={`toggle-switch${notificationsEnabled ? ' on' : ''}`} role="switch" aria-checked={notificationsEnabled} aria-label="Gentle notifications" onClick={() => setNotificationsEnabled((enabled) => !enabled)}><span /></button></div>
        <div className="preference-row"><div><strong>Start the next session automatically</strong><small>Stay in control of when each session starts.</small></div><button type="button" className={`toggle-switch${autoStartEnabled ? ' on' : ''}`} role="switch" aria-checked={autoStartEnabled} aria-label="Start next session automatically" onClick={() => setAutoStartEnabled((enabled) => !enabled)}><span /></button></div>
        <div className="preference-row theme-preference-row"><div><strong>Application Theme</strong><small>Your focus space, looking the way you like.</small></div><select className="outlined-control theme-select" aria-label="Application Theme" value={theme} onChange={(event) => setTheme(event.target.value)}><option>Light Theme</option><option>Dark Theme</option><option>System Theme</option></select></div>
      </section>

      <section className="shortcuts-panel card-panel">
        <h2>Fewer clicks, more focus</h2>
        <div className="shortcut-row"><span>Start or pause session</span><kbd>Space</kbd></div>
        <div className="shortcut-row"><span>Exit full screen</span><kbd>Esc</kbd></div>
        <p>Your preferences are saved automatically.</p>
      </section>
    </div>

    <aside className="settings-side-column">
      <section className="privacy-panel">
        <img src="/figma/lock.svg" width="24" height="24" alt="" />
        <h2>Your focus is personal.<br />Your data is, too.</h2>
        <ul><li>Data stored only on this computer</li><li>No account or cloud sync</li><li>No data collected for advertising</li></ul>
        <p>Privacy is not an option. It’s the foundation.</p>
      </section>
      <section className="local-data-panel card-panel">
        <h2>Your data, in your hands</h2>
        <p className="storage-label">LOCAL STORAGE</p><strong className="storage-size">2.4 MB</strong><p className="muted-copy">History and preferences on this device.</p>
        <button type="button" className="outlined-control full-control">Open data folder&nbsp; ↗</button>
        <div className="data-action"><strong>Take your progress with you.</strong><button type="button" className="outlined-control full-control">↓&nbsp; Export all data</button></div>
        <button type="button" className="clear-history"><strong>Clear session history</strong><small>Delete records. Keep your preferences.</small></button>
      </section>
    </aside>
  </div>
}
