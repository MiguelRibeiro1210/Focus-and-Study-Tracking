import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { isAbsolute, join, resolve } from 'node:path'

type Platform = 'win32' | 'darwin' | 'linux'

export function resolveApiTokenPath(
  env: NodeJS.ProcessEnv = process.env,
  platform: Platform = process.platform as Platform,
  home = homedir(),
): string {
  const override = env.FOCUSDESKTOP_DATA_DIR?.trim()
  if (override) return join(isAbsolute(override) ? override : resolve(home, override), 'api-token')

  if (platform === 'win32') {
    return join(env.LOCALAPPDATA?.trim() || join(home, 'AppData', 'Local'), 'FocusDesktop', 'api-token')
  }
  if (platform === 'darwin') {
    return join(home, 'Library', 'Application Support', 'FocusDesktop', 'api-token')
  }
  const xdg = env.XDG_DATA_HOME?.trim()
  return join(xdg && isAbsolute(xdg) ? xdg : join(home, '.local', 'share'), 'focusdesktop', 'api-token')
}

export function readApiToken(env: NodeJS.ProcessEnv = process.env): string | undefined {
  try {
    const token = readFileSync(resolveApiTokenPath(env), 'utf8').trim()
    return token || undefined
  } catch {
    return undefined
  }
}
