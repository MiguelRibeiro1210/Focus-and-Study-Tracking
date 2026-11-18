import { useEffect, useState } from 'react'
import type { TimerController } from '../timer/useTimerSession'
import { TimerScreen } from '../timer/TimerScreen'
import { DistractionsPage } from '../pages/DistractionsPage'
import { HistoryPage } from '../pages/HistoryPage'
import { SettingsPage } from '../pages/SettingsPage'
import { useAppSettings } from '../pages/useAppSettings'

type Page = 'overview' | 'distractions' | 'history' | 'settings'

const navigation: { id: Page; label: string; icon: string }[] = [
  { id: 'overview', label: 'Overview', icon: '/figma/grid.svg' },
  { id: 'distractions', label: 'Distractions', icon: '/figma/shield.svg' },
  { id: 'history', label: 'History', icon: '/figma/history.svg' },
  { id: 'settings', label: 'Settings', icon: '/figma/settings.svg' },
]

const pageCopy: Record<Page, { title: string; subtitle: string }> = {
  overview: { title: 'Your space to Focus', subtitle: 'One task at a time. The rest can wait.' },
  distractions: { title: 'Make room for what matters.', subtitle: 'Choose what stays out during a focus session.' },
  history: { title: 'Your focus, in perspective.', subtitle: 'Small steps that make a difference.' },
  settings: { title: 'Your way. Always local.', subtitle: 'Set your own pace and stay in control of your data.' },
}

interface FocusDesktopAppProps {
  timer: TimerController
}

export function FocusDesktopApp({ timer }: FocusDesktopAppProps) {
  const [page, setPage] = useState<Page>('overview')
  const settings = useAppSettings()
  const copy = pageCopy[page]

  useEffect(() => {
    const requestedTheme = settings.settings?.theme ?? 'LIGHT'
    if (requestedTheme !== 'SYSTEM') {
      document.documentElement.dataset.theme = requestedTheme
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => { document.documentElement.dataset.theme = media.matches ? 'DARK' : 'LIGHT' }
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [settings.settings?.theme])

  return (
    <div className="desktop-app">
      <aside className="sidebar" aria-label="Main navigation">
        <a className="sidebar-brand" href="#overview" onClick={() => setPage('overview')} aria-label="FocusDesktop home">
          <span className="sidebar-brand-mark"><img src="/figma/target.svg" width="20" height="20" alt="" /></span>
          <span>FocusDesktop</span>
        </a>
        <p className="sidebar-tagline">LESS NOISE. MORE FOCUS.</p>
        <p className="sidebar-section-label">WORKSPACE</p>
        <nav className="sidebar-navigation">
          {navigation.map((item) => (
            <button
              aria-current={page === item.id ? 'page' : undefined}
              className={`nav-item${page === item.id ? ' active' : ''}`}
              key={item.id}
              onClick={() => setPage(item.id)}
              type="button"
            >
              <img className="nav-icon" src={item.icon} width="20" height="20" alt="" />
              <span>{item.label}</span>
              {page === item.id && <img className="nav-current" src="/figma/current.svg" width="4" height="4" alt="" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-privacy">
          <div className="privacy-heading"><img src="/figma/lock.svg" width="18" height="18" alt="" /><span>Only on this computer</span></div>
          <p>Your data stays with you.</p>
          <p>No account. No cloud.</p>
        </div>
      </aside>

      <div className="workspace">
        <header className="workspace-header">
          <div>
            <h1>{copy.title}</h1>
            <p>{copy.subtitle}</p>
          </div>
          <div className="local-status"><img src="/figma/connected.svg" width="6" height="6" alt="" />Running locally</div>
        </header>
        <main className={`workspace-content page-${page}`}>
          {page === 'overview' && <TimerScreen timer={timer} settings={settings.settings} onViewHistory={() => setPage('history')} />}
          {page === 'distractions' && <DistractionsPage />}
          {page === 'history' && <HistoryPage />}
          {page === 'settings' && <SettingsPage controller={settings} />}
        </main>
      </div>
    </div>
  )
}
