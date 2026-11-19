import { access, mkdtemp, readFile, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { delimiter, join, resolve } from 'node:path'
import { spawn } from 'node:child_process'
import WebSocket from 'ws'

const frontendDir = resolve(import.meta.dirname, '..')
const repoDir = resolve(frontendDir, '..')
const jarPath = join(repoDir, 'backend/target/focus-and-study-tracking-0.0.1-SNAPSHOT.jar')
const packagedApp = process.argv[2] === '--packaged' ? process.argv[3] : undefined
const api = 'http://127.0.0.1:8080'
const temporaryData = await mkdtemp(join(tmpdir(), 'focusdesktop-smoke-'))
const childApps = new Set()

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function pathWithoutJava(pathValue) {
  const javaNames = process.platform === 'win32' ? ['java.exe', 'java.cmd', 'java.bat'] : ['java']
  return pathValue.split(delimiter).filter((directory) =>
    !javaNames.some((javaName) => existsSync(join(directory, javaName))),
  ).join(delimiter)
}

async function waitForHealth(processHandle, timeoutMs = 60_000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if (processHandle.exitCode !== null || processHandle.signalCode !== null) {
      throw new Error(`Tauri exited early with status ${processHandle.exitCode ?? processHandle.signalCode}`)
    }
    try {
      const response = await fetch(`${api}/health`, { signal: AbortSignal.timeout(500) })
      if (response.ok) return
    } catch { /* backend is still starting */ }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250))
  }
  let diagnostics = ''
  try {
    diagnostics = (await readFile(join(temporaryData, 'backend.log'), 'utf8')).trim()
  } catch { /* the app may have failed before creating its backend log */ }
  const detail = diagnostics ? `\nBackend diagnostics:\n${diagnostics}` : '\nThe app did not create backend.log.'
  throw new Error(`Timed out waiting for the Tauri-managed backend health endpoint.${detail}`)
}

function launchApp() {
  const command = packagedApp || 'npm'
  const args = packagedApp
    ? (packagedApp.toLowerCase().endsWith('.appimage') ? ['--appimage-extract-and-run'] : [])
    : ['run', 'tauri:dev']
  const appEnvironment = {
    ...process.env,
    FOCUSDESKTOP_DATA_DIR: temporaryData,
  }
  if (packagedApp) {
    appEnvironment.FOCUSDESKTOP_JAVA = join(temporaryData, 'java-must-not-be-used')
    appEnvironment.JAVA_HOME = ''
    appEnvironment.PATH = pathWithoutJava(process.env.PATH || '')
  }
  const processHandle = spawn(command, args, {
    cwd: frontendDir,
    env: appEnvironment,
    detached: process.platform !== 'win32',
    stdio: 'inherit',
  })
  childApps.add(processHandle)
  return processHandle
}

async function stopApp(processHandle) {
  if (processHandle.exitCode !== null || processHandle.signalCode !== null) return
  if (process.platform === 'win32' && processHandle.pid) {
    const taskkill = spawn('taskkill', ['/PID', String(processHandle.pid), '/T', '/F'], { stdio: 'ignore' })
    await new Promise((resolveExit) => taskkill.once('exit', resolveExit))
  } else if (processHandle.pid) {
    try { process.kill(-processHandle.pid, 'SIGINT') } catch { /* already stopped */ }
  } else processHandle.kill('SIGINT')
  await Promise.race([
    new Promise((resolveExit) => processHandle.once('exit', resolveExit)),
    new Promise((resolveTimeout) => setTimeout(resolveTimeout, 10_000)),
  ])
  if (processHandle.exitCode === null && processHandle.signalCode === null && processHandle.pid) {
    try {
      if (process.platform === 'win32') {
        const taskkill = spawn('taskkill', ['/PID', String(processHandle.pid), '/T', '/F'], { stdio: 'ignore' })
        await new Promise((resolveExit) => taskkill.once('exit', resolveExit))
      } else process.kill(-processHandle.pid, 'SIGTERM')
    } catch { /* already stopped */ }
  }
  childApps.delete(processHandle)
}

async function apiToken() {
  const tokenPath = join(temporaryData, 'api-token')
  const startedAt = Date.now()
  while (Date.now() - startedAt < 30_000) {
    try { return (await readFile(tokenPath, 'utf8')).trim() } catch { await new Promise((resolveWait) => setTimeout(resolveWait, 100)) }
  }
  throw new Error('The backend did not create its API token.')
}

async function request(token, path, method = 'GET', body) {
  return fetch(`${api}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, Origin: 'tauri://localhost', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
}

async function waitForSocketEvent(socket, predicate, timeoutMs = 10_000) {
  return new Promise((resolveEvent, rejectEvent) => {
    const timeout = setTimeout(() => rejectEvent(new Error('Timed out waiting for the session WebSocket event.')), timeoutMs)
    socket.on('message', (data) => {
      let event
      try { event = JSON.parse(data.toString()) } catch { return }
      if (!predicate(event)) return
      clearTimeout(timeout)
      resolveEvent(event)
    })
  })
}

async function run() {
  if (packagedApp) {
    assert(await access(packagedApp).then(() => true).catch(() => false), `Packaged app not found: ${packagedApp}`)
  } else {
    assert(await access(jarPath).then(() => true).catch(() => false), `Backend JAR missing: ${jarPath}. Build it with: cd backend && ./mvnw package -DskipTests`)
  }
  try {
    const existing = await fetch(`${api}/health`, { signal: AbortSignal.timeout(500) })
    if (existing.ok) throw new Error('Port 8080 already serves a backend. Stop it before running this smoke so the test can verify the Tauri-managed sidecar.')
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('Port 8080')) throw error
  }

  let app = launchApp()
  await waitForHealth(app)
  const token = await apiToken()
  const analyticsResponse = await request(token, '/api/v1/analytics/summary')
  assert(analyticsResponse.status === 200, `Expected analytics summary status 200, got ${analyticsResponse.status}`)
  const analyticsSummary = await analyticsResponse.json()
  assert(Number.isFinite(analyticsSummary.totalFocusSeconds), 'Analytics summary did not contain a focus total.')
  assert(Array.isArray(analyticsSummary.daily), 'Analytics summary did not contain daily buckets.')
  const socket = new WebSocket('ws://127.0.0.1:8080/ws', { headers: { Authorization: `Bearer ${token}`, Origin: 'tauri://localhost' } })
  await new Promise((resolveOpen, rejectOpen) => {
    socket.once('open', resolveOpen)
    socket.once('error', rejectOpen)
  })
  const startedEvent = waitForSocketEvent(socket, (event) => event.type === 'session.started')
  const startResponse = await request(token, '/api/v1/sessions', 'POST', { sessionType: 'WORK', projectName: 'Smoke test' })
  assert(startResponse.status === 201, `Expected session start status 201, got ${startResponse.status}`)
  const session = await startResponse.json()
  const event = await startedEvent
  assert(event.payload?.sessionId === session.sessionId, 'WebSocket session.started payload did not match the REST response.')
  socket.close()
  await stopApp(app)
  app = undefined
  await waitForHealthToStop()

  app = launchApp()
  await waitForHealth(app)
  const resumedToken = await apiToken()
  const activeResponse = await request(resumedToken, '/api/v1/sessions/active')
  assert(activeResponse.status === 200, `Expected active session to recover with status 200, got ${activeResponse.status}`)
  const recovered = await activeResponse.json()
  assert(recovered.sessionId === session.sessionId, 'Active session was not recovered after restarting the desktop shell.')
  const stopResponse = await request(resumedToken, `/api/v1/sessions/${session.sessionId}/stop`, 'POST')
  assert(stopResponse.ok, `Could not stop recovered session (${stopResponse.status}).`)
  await stopApp(app)
  app = undefined
  await waitForHealthToStop()
  console.log(`${packagedApp ? 'FST-044 packaged' : 'FST-040'} smoke passed: startup, authenticated REST, WebSocket event, active-session recovery and sidecar shutdown.`)
}

async function waitForHealthToStop() {
  const startedAt = Date.now()
  while (Date.now() - startedAt < 10_000) {
    try {
      await fetch(`${api}/health`, { signal: AbortSignal.timeout(300) })
    } catch { return }
    await new Promise((resolveWait) => setTimeout(resolveWait, 200))
  }
  throw new Error('Backend still responds after the Tauri process exited; sidecar shutdown did not complete.')
}

try {
  await run()
} finally {
  for (const processHandle of childApps) await stopApp(processHandle)
  await rm(temporaryData, { recursive: true, force: true })
}
