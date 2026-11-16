/** Data contracts for screens whose persistence and APIs are not implemented yet. */
export interface DistractionRule {
  id: string
  name: string
  target: string
  kind: 'website' | 'application'
  enabled: boolean
}

export interface FocusProfile {
  id: string
  name: string
  rules: DistractionRule[]
}

export interface FocusHistoryEntry {
  id: string
  projectName: string
  startedAt: string
  durationSeconds: number
  status: 'completed' | 'stopped'
  blockedAttempts: number
}

export interface TimerProfile {
  id: string
  name: string
  focusMinutes: number
  shortBreakMinutes: number
  longBreakMinutes: number
  isDefault: boolean
}

export type ApplicationTheme = 'light' | 'dark' | 'system'

export interface ApplicationPreferences {
  theme: ApplicationTheme
  startNextSessionAutomatically: boolean
}

export const distractionRules: readonly DistractionRule[] = []
export const focusProfiles: readonly FocusProfile[] = []
export const focusHistory: readonly FocusHistoryEntry[] = []
export const timerProfiles: readonly TimerProfile[] = []
