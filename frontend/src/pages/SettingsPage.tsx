import { useEffect, useState, type FormEvent } from 'react'
import { isDesktopShell } from '../api/nativeTransport'
import type { ApplicationTheme, ExportFormat, TimerProfile } from '../api/settingsApi'
import type { AppSettingsController } from './useAppSettings'

interface SettingsPageProps { controller: AppSettingsController }
type Confirmation = { kind: 'clear' | 'expired' | 'all' | 'profile'; profileId?: string } | null

interface ProfileDraft {
  name: string
  workMinutes: string
  shortMinutes: string
  longMinutes: string
  intervalsBeforeLongBreak: string
  autoStartBreaks: boolean
  autoStartWork: boolean
}

const emptyDraft: ProfileDraft = {
  name: '', workMinutes: '25', shortMinutes: '5', longMinutes: '15',
  intervalsBeforeLongBreak: '4', autoStartBreaks: false, autoStartWork: false,
}

function profileDraft(profile: TimerProfile): ProfileDraft {
  return {
    name: profile.name,
    workMinutes: String(profile.workSeconds / 60),
    shortMinutes: String(profile.shortBreakSeconds / 60),
    longMinutes: String(profile.longBreakSeconds / 60),
    intervalsBeforeLongBreak: String(profile.intervalsBeforeLongBreak),
    autoStartBreaks: profile.autoStartBreaks,
    autoStartWork: profile.autoStartWork,
  }
}

function describeProfile(profile: TimerProfile): string {
  return `${profile.workSeconds / 60} min focus · ${profile.shortBreakSeconds / 60} min short · ${profile.longBreakSeconds / 60} min long`
}

function wholeNumber(value: string, minimum: number, maximum: number): number | null {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= minimum && parsed <= maximum ? parsed : null
}

export function SettingsPage({ controller }: SettingsPageProps) {
  const { settings, loading, pending, error, notice, retry } = controller
  const [profileDialog, setProfileDialog] = useState<TimerProfile | 'new' | null>(null)
  const [draft, setDraft] = useState<ProfileDraft>(emptyDraft)
  const [confirmation, setConfirmation] = useState<Confirmation>(null)
  const [deleteAllPhrase, setDeleteAllPhrase] = useState('')
  const [exportFormat, setExportFormat] = useState<ExportFormat>('json')
  const [dailyGoalInput, setDailyGoalInput] = useState('3')
  const [retentionInput, setRetentionInput] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  useEffect(() => {
    if (!settings) return
    setDailyGoalInput(String(settings.dailyGoalHours))
    setRetentionInput(settings.retentionDays === null ? '' : String(settings.retentionDays))
  }, [settings?.dailyGoalHours, settings?.retentionDays])

  const defaultProfile = settings?.profiles.find((profile) => profile.id === settings.defaultProfileId)

  async function updatePreferences(overrides: Partial<{
    dailyGoalHours: number
    theme: ApplicationTheme
    notificationsEnabled: boolean
    retentionDays: number | null
  }>): Promise<boolean> {
    if (!settings) return false
    try {
      await controller.updateApplicationSettings({
        dailyGoalHours: settings.dailyGoalHours,
        theme: settings.theme,
        notificationsEnabled: settings.notificationsEnabled,
        retentionDays: settings.retentionDays,
        ...overrides,
      })
      setLocalError(null)
      return true
    } catch { /* The shared settings controller exposes the API error. */ return false }
  }

  async function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profileDialog) return
    const name = draft.name.trim()
    if (!name || name.length > 60) {
      setLocalError('Profile names must contain between 1 and 60 characters.')
      return
    }
    try {
      if (profileDialog === 'new') {
        await controller.createProfile(name)
      } else {
        const work = wholeNumber(draft.workMinutes, 1, 180)
        const shortBreak = wholeNumber(draft.shortMinutes, 1, 60)
        const longBreak = wholeNumber(draft.longMinutes, 1, 120)
        const intervals = wholeNumber(draft.intervalsBeforeLongBreak, 1, 12)
        if (work === null || shortBreak === null || longBreak === null || intervals === null) {
          setLocalError('Enter valid whole-number durations and an interval count within the shown limits.')
          return
        }
        if (longBreak < shortBreak) {
          setLocalError('The long break must be at least as long as the short break.')
          return
        }
        await controller.updateProfile(profileDialog.id, {
          name,
          workSeconds: work * 60,
          shortBreakSeconds: shortBreak * 60,
          longBreakSeconds: longBreak * 60,
          intervalsBeforeLongBreak: intervals,
          autoStartBreaks: draft.autoStartBreaks,
          autoStartWork: draft.autoStartWork,
        })
      }
      setProfileDialog(null)
      setLocalError(null)
    } catch { /* The shared settings controller exposes the API error. */ }
  }

  async function confirmAction() {
    if (!confirmation) return
    try {
      if (confirmation.kind === 'clear') await controller.clearSessionHistory()
      if (confirmation.kind === 'expired') await controller.cleanExpiredHistory()
      if (confirmation.kind === 'all') await controller.deleteAllData()
      if (confirmation.kind === 'profile' && confirmation.profileId) await controller.deleteProfile(confirmation.profileId)
      setConfirmation(null)
      setDeleteAllPhrase('')
    } catch { /* Keep the dialog open so the user can see and retry an error. */ }
  }

  async function exportData() {
    try { await controller.exportAllData(exportFormat) } catch { /* Shared error state is displayed below. */ }
  }

  return <div className="settings-layout">
    {error && !settings && <div className="settings-load-error" role="alert">
      <span>Settings could not load, so actions are unavailable. {error}</span>
      <button type="button" className="retry-button" onClick={retry}>Retry</button>
    </div>}
    <div className="settings-main-column">
      <section className="saved-profiles card-panel">
        <div className="saved-profiles-heading">
          <div><h2>Saved timer profiles</h2><p>Choose a rhythm, then use it on Overview.</p></div>
          <button type="button" className="outlined-control add-profile" disabled={!settings || pending}
            onClick={() => { setDraft({ ...emptyDraft, name: '', ...(defaultProfile ? {
              workMinutes: String(defaultProfile.workSeconds / 60),
              shortMinutes: String(defaultProfile.shortBreakSeconds / 60),
              longMinutes: String(defaultProfile.longBreakSeconds / 60),
              intervalsBeforeLongBreak: String(defaultProfile.intervalsBeforeLongBreak),
              autoStartBreaks: defaultProfile.autoStartBreaks,
              autoStartWork: defaultProfile.autoStartWork,
            } : {}) }); setProfileDialog('new') }}>＋ Add profile</button>
        </div>
        {loading && <p role="status" className="muted-copy">Loading your settings…</p>}
        {!loading && settings && <div className="profile-list">{settings.profiles.map((profile) => <div className="saved-profile-row" key={profile.id}>
          <div><strong>{profile.name}</strong><small>{describeProfile(profile)} · {profile.intervalsBeforeLongBreak} work intervals before long break</small></div>
          <div className="profile-row-actions">
            {profile.isDefault
              ? <span className="profile-action default">DEFAULT</span>
              : <button type="button" className="profile-action" disabled={pending} onClick={() => void controller.setDefaultProfile(profile.id)}>Set default</button>}
            <button type="button" className="profile-link" disabled={pending} onClick={() => { setDraft(profileDraft(profile)); setProfileDialog(profile) }}>Edit</button>
            <button type="button" className="profile-link danger-link" disabled={pending || profile.isDefault || settings.profiles.length <= 1}
              onClick={() => setConfirmation({ kind: 'profile', profileId: profile.id })}>Delete</button>
          </div>
        </div>)}</div>}
        <div className="daily-goal-setting">
          <div><strong>Daily goal</strong><small>Count your focus time toward this daily target.</small></div>
          <label className="visually-hidden" htmlFor="daily-goal-hours">Daily focus goal in hours</label>
          <input id="daily-goal-hours" className="outlined-control daily-goal-select" type="number" min="1" max="24" step="1"
            inputMode="numeric" value={dailyGoalInput} disabled={!settings || pending}
            onChange={(event) => setDailyGoalInput(event.target.value)}
            onBlur={() => {
              const value = wholeNumber(dailyGoalInput, 1, 24)
              if (value === null) setLocalError('Daily goal must be a whole number from 1 to 24 hours.')
              else if (settings && value !== settings.dailyGoalHours) { setLocalError(null); void updatePreferences({ dailyGoalHours: value }) }
            }} />
        </div>
      </section>

      <section className="preferences-panel card-panel">
        <h2>The app adapts to you</h2>
        <div className="preference-row"><div><strong>Gentle notifications</strong><small>Get a system notification when each work or break interval ends.</small></div>
          <button type="button" className={`toggle-switch${settings?.notificationsEnabled ? ' on' : ''}`} role="switch"
            aria-checked={settings?.notificationsEnabled ?? false} aria-label="Gentle notifications" disabled={!settings || pending}
            onClick={() => void controller.setNotificationsEnabled(!settings?.notificationsEnabled).catch(() => {})}><span /></button></div>
        <div className="preference-row theme-preference-row"><div><strong>Application Theme</strong><small>Your focus space, looking the way you like.</small></div>
          <select className="outlined-control theme-select" aria-label="Application Theme" disabled={!settings || pending}
            value={settings?.theme ?? 'LIGHT'} onChange={(event) => {
              const theme = event.target.value as ApplicationTheme
              const previous = settings?.theme ?? 'LIGHT'
              document.documentElement.dataset.theme = theme === 'SYSTEM'
                ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'DARK' : 'LIGHT') : theme
              void updatePreferences({ theme }).then((saved) => {
                if (!saved) document.documentElement.dataset.theme = previous === 'SYSTEM'
                  ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'DARK' : 'LIGHT') : previous
              })
            }}>
            <option value="LIGHT">Light Theme</option><option value="DARK">Dark Theme</option><option value="SYSTEM">System Theme</option>
          </select></div>
        <div className="preference-row retention-row"><div><strong>Keep session history</strong><small>Choose how long terminal sessions remain before you clean them up.</small></div>
          <div className="retention-control"><input aria-label="Retention days" className="outlined-control" type="number" min="7" max="3650" step="1"
            value={retentionInput} placeholder="Keep forever" disabled={!settings || pending || settings?.retentionDays === null}
            onChange={(event) => setRetentionInput(event.target.value)}
            onBlur={() => {
              if (!settings || settings.retentionDays === null) return
              const value = wholeNumber(retentionInput, 7, 3650)
              if (value === null) setLocalError('Retention must be 7–3650 whole days, or Keep forever.')
              else if (value !== settings.retentionDays) { setLocalError(null); void updatePreferences({ retentionDays: value }) }
            }} />
            <label><input type="checkbox" checked={settings?.retentionDays === null} disabled={!settings || pending}
              onChange={(event) => { setRetentionInput(''); void updatePreferences({ retentionDays: event.target.checked ? null : 7 }) }} /> Keep forever</label>
          </div></div>
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
        <p className="storage-label">LOCAL STORAGE</p><strong className="storage-size">On this computer</strong><p className="muted-copy">History and preferences stay on this device.</p>
        <button type="button" className="outlined-control full-control" disabled={!isDesktopShell() || pending}
          title={!isDesktopShell() ? 'Available in the desktop app.' : undefined} onClick={() => void controller.openDataFolder()}>Open data folder&nbsp; ↗</button>
        <div className="data-action"><strong>Take your progress with you.</strong>
          <div className="export-controls"><label className="visually-hidden" htmlFor="export-format">Export format</label>
            <select id="export-format" className="outlined-control" value={exportFormat} onChange={(event) => setExportFormat(event.target.value as ExportFormat)}>
              <option value="json">JSON file</option><option value="csv-zip">CSV ZIP archive</option>
            </select>
            <button type="button" className="outlined-control full-control" disabled={!settings || pending} onClick={() => void exportData()}>↓&nbsp; Export all data</button>
          </div>
        </div>
        <button type="button" className="clear-history cleanup-expired" disabled={!settings || pending || settings?.retentionDays === null}
          onClick={() => setConfirmation({ kind: 'expired' })}><strong>Clean expired history</strong><small>Remove terminal sessions older than your retention period.</small></button>
        <button type="button" className="clear-history" disabled={!settings || pending}
          onClick={() => setConfirmation({ kind: 'clear' })}><strong>Clear session history</strong><small>Delete all completed and abandoned sessions.</small></button>
        <button type="button" className="clear-history delete-all-action" disabled={!settings || pending}
          onClick={() => setConfirmation({ kind: 'all' })}><strong>Delete all data</strong><small>Remove all personal data and restore default settings.</small></button>
      </section>
    </aside>

    {(localError || (error && settings)) && <p className="settings-feedback settings-error" role="alert">{localError ?? error}</p>}
    {!error && !localError && notice && <p className="settings-feedback" role="status">{notice}</p>}

    {profileDialog && <div className="dialog-backdrop"><form className="confirm-dialog profile-dialog" role="dialog" aria-modal="true"
      aria-labelledby="profile-dialog-title" onSubmit={(event) => void submitProfile(event)}>
      <h2 id="profile-dialog-title">{profileDialog === 'new' ? 'Add timer profile' : `Edit ${profileDialog.name}`}</h2>
      <label className="profile-form-field">Profile name<input autoFocus maxLength={60} required value={draft.name}
        onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} /></label>
      {profileDialog !== 'new' && <>
        <div className="profile-form-durations">
          <label className="profile-form-field">Work minutes<input type="number" min="1" max="180" step="1" required value={draft.workMinutes} onChange={(event) => setDraft((current) => ({ ...current, workMinutes: event.target.value }))} /></label>
          <label className="profile-form-field">Short break minutes<input type="number" min="1" max="60" step="1" required value={draft.shortMinutes} onChange={(event) => setDraft((current) => ({ ...current, shortMinutes: event.target.value }))} /></label>
          <label className="profile-form-field">Long break minutes<input type="number" min="1" max="120" step="1" required value={draft.longMinutes} onChange={(event) => setDraft((current) => ({ ...current, longMinutes: event.target.value }))} /></label>
          <label className="profile-form-field">Work intervals before long break<input type="number" min="1" max="12" step="1" required value={draft.intervalsBeforeLongBreak} onChange={(event) => setDraft((current) => ({ ...current, intervalsBeforeLongBreak: event.target.value }))} /></label>
        </div>
        <label className="profile-checkbox"><input type="checkbox" checked={draft.autoStartBreaks} onChange={(event) => setDraft((current) => ({ ...current, autoStartBreaks: event.target.checked }))} /> Automatically start breaks</label>
        <label className="profile-checkbox"><input type="checkbox" checked={draft.autoStartWork} onChange={(event) => setDraft((current) => ({ ...current, autoStartWork: event.target.checked }))} /> Automatically start work intervals</label>
      </>}
      <p className="profile-form-hint">New profiles copy the current default timer settings.</p>
      <div className="dialog-actions"><button className="button button-secondary" type="button" onClick={() => setProfileDialog(null)}>Cancel</button>
        <button className="button button-primary" type="submit" disabled={pending}>{pending ? 'Saving…' : 'Save profile'}</button></div>
    </form></div>}

    {confirmation && <div className="dialog-backdrop"><section className="confirm-dialog" role="alertdialog" aria-modal="true"
      aria-labelledby="settings-confirm-title" aria-describedby="settings-confirm-description">
      <h2 id="settings-confirm-title">{confirmation.kind === 'clear' ? 'Clear session history?' : confirmation.kind === 'expired' ? 'Clean expired history?' : confirmation.kind === 'profile' ? 'Delete this profile?' : 'Delete all personal data?'}</h2>
      <p id="settings-confirm-description">{confirmation.kind === 'clear'
        ? 'All completed and abandoned sessions will be deleted. Settings and active sessions will remain.'
        : confirmation.kind === 'expired'
          ? 'Only completed or abandoned sessions older than the selected retention period will be deleted.'
          : confirmation.kind === 'profile'
            ? 'This profile and its saved timer settings will be removed.'
            : 'Sessions, history, profiles, and preferences will be removed. Default settings will be restored.'}</p>
      {confirmation.kind === 'all' && <label className="profile-form-field">Type DELETE_ALL_DATA to confirm<input autoFocus value={deleteAllPhrase}
        onChange={(event) => setDeleteAllPhrase(event.target.value)} /></label>}
      <div className="dialog-actions"><button className="button button-secondary" type="button" disabled={pending}
        onClick={() => { setConfirmation(null); setDeleteAllPhrase('') }}>Cancel</button>
        <button className="button button-danger" type="button" disabled={pending || (confirmation.kind === 'all' && deleteAllPhrase !== 'DELETE_ALL_DATA')}
          onClick={() => void confirmAction()}>{pending ? 'Working…' : 'Confirm'}</button></div>
    </section></div>}
  </div>
}
