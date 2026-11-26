import { readdir, readFile } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'

const repoDir = join(import.meta.dirname, '..', '..')
const sourceRoots = [
  join(repoDir, 'frontend/src'),
  join(repoDir, 'frontend/src-tauri/src'),
  join(repoDir, 'backend/src/main/java'),
]
const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.rs', '.java'])
const allowedHosts = new Set(['127.0.0.1', 'localhost', '[::1]', 'tauri.localhost'])
const telemetryNames = new Set([
  'sentry', 'bugsnag', 'rollbar', 'datadog', 'newrelic', 'opentelemetry',
  'posthog', 'mixpanel', 'amplitude', 'segment', 'heap', 'fullstory',
  'logrocket', 'crashlytics',
])
const endpointPattern = /\b(?:https?|wss?|tauri):\/\/([^\s"'`<>/]+)/g
const violations = []

function isTelemetryDependency(name) {
  const tokens = name.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean)
  return tokens.some((token) => telemetryNames.has(token))
    || tokens.some((token, index) =>
      (token === 'firebase' || token === 'google') && tokens[index + 1] === 'analytics')
}

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) files.push(...await sourceFiles(path))
    else if (sourceExtensions.has(extname(entry.name))) files.push(path)
  }
  return files
}

for (const root of sourceRoots) {
  for (const path of await sourceFiles(root)) {
    const source = await readFile(path, 'utf8')
    for (const match of source.matchAll(endpointPattern)) {
      const authority = match[1]
      const host = authority.startsWith('[')
        ? authority.slice(0, authority.indexOf(']') + 1)
        : authority.split(':')[0]
      // ADR-011: this reserved .invalid authority is intercepted locally by the
      // consented proxy to verify browser routing. It is never an upstream host.
      const localProxyChallenge = relative(repoDir, path) === 'backend/src/main/java/com/focusdesktop/infrastructure/proxy/WebsiteProxy.java'
        && match[0] === 'http://focusdesktop.invalid'
      if (!allowedHosts.has(host) && !localProxyChallenge) {
        violations.push(`${relative(repoDir, path)}: non-local endpoint ${match[0]}`)
      }
    }
  }
}

const frontendPackage = JSON.parse(await readFile(join(repoDir, 'frontend/package.json'), 'utf8'))
for (const name of Object.keys({ ...frontendPackage.dependencies, ...frontendPackage.devDependencies })) {
  if (isTelemetryDependency(name)) violations.push(`frontend/package.json: telemetry dependency ${name}`)
}
const frontendLock = JSON.parse(await readFile(join(repoDir, 'frontend/package-lock.json'), 'utf8'))
for (const name of Object.keys(frontendLock.packages ?? {})) {
  if (isTelemetryDependency(name)) violations.push(`frontend/package-lock.json: telemetry package ${name}`)
}

const cargoManifest = await readFile(join(repoDir, 'frontend/src-tauri/Cargo.toml'), 'utf8')
for (const match of cargoManifest.matchAll(/^\s*([\w-]+)\s*=/gm)) {
  if (isTelemetryDependency(match[1])) violations.push(`frontend/src-tauri/Cargo.toml: telemetry dependency ${match[1]}`)
}
const cargoLock = await readFile(join(repoDir, 'frontend/src-tauri/Cargo.lock'), 'utf8')
for (const match of cargoLock.matchAll(/^name = "([^"]+)"/gm)) {
  if (isTelemetryDependency(match[1])) violations.push(`frontend/src-tauri/Cargo.lock: telemetry crate ${match[1]}`)
}

const mavenManifest = await readFile(join(repoDir, 'backend/pom.xml'), 'utf8')
for (const match of mavenManifest.matchAll(/<artifactId>([^<]+)<\/artifactId>/g)) {
  if (isTelemetryDependency(match[1])) violations.push(`backend/pom.xml: telemetry dependency ${match[1]}`)
}

if (violations.length > 0) {
  console.error('Privacy audit failed:')
  for (const violation of violations) console.error(`- ${violation}`)
  process.exitCode = 1
} else {
  console.log('Privacy audit passed: fixed runtime endpoints are local (including the intercepted proxy challenge) and no telemetry SDK dependency was found. Consented request-driven forwarding is tested separately.')
}
