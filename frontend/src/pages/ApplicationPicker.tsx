import { useEffect, useRef, useState } from 'react'
import type { ApplicationCatalog, BlockingRule, BlockingRuleInput, BlockingRulesApi, RuleTarget, TargetPlatform } from '../api/blockingRulesApi'

function applicationTargetKey(target: RuleTarget) {
  if (target.type !== 'APPLICATION') return ''
  let value = target.value.trim()
  if (target.matchKind !== 'APPLICATION_ID' && target.platform === 'WINDOWS') value = value.replaceAll('\\', '/').toLowerCase()
  if (target.matchKind === 'EXECUTABLE_PATH') {
    const parts: string[] = []
    for (const part of value.split('/')) {
      if (part === '..') parts.pop()
      else if (part && part !== '.') parts.push(part)
    }
    value = parts.join('/')
  }
  return JSON.stringify([target.platform, target.matchKind, value])
}

export function ApplicationPicker({ api, rules, pending, onPlatform, onAdd, onManual, onCancel }: {
  api: BlockingRulesApi
  rules: BlockingRule[]
  pending: boolean
  onPlatform(platform: TargetPlatform): void
  onAdd(inputs: BlockingRuleInput[]): Promise<boolean>
  onManual(): void
  onCancel(): void
}) {
  const [catalog, setCatalog] = useState<ApplicationCatalog | null>(null)
  const [loading, setLoading] = useState(Boolean(api.applications))
  const [error, setError] = useState(api.applications ? '' : 'Application discovery is unavailable. Add an app manually.')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const searchField = useRef<HTMLInputElement>(null)
  useEffect(() => {
    let active = true
    if (!api.applications) return
    api.applications().then((data) => { if (active) { setCatalog(data); onPlatform(data.platform) } })
      .catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause.message : 'Applications could not be loaded.') })
      .finally(() => { if (active) setLoading(false) })
    searchField.current?.focus()
    return () => { active = false }
  }, [api, onPlatform])
  async function refresh() {
    setLoading(true); setError('')
    try {
      if (!api.applications) throw new Error('Application discovery is unavailable. Add an app manually.')
      const data = await api.applications(true); setCatalog(data); onPlatform(data.platform)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Applications could not be loaded.') }
    finally { setLoading(false) }
  }
  const existing = new Set(rules.map((rule) => applicationTargetKey(rule.target)).filter(Boolean))
  const choices = catalog?.applications ?? []
  const nameCounts = new Map<string, number>()
  for (const app of choices) { const key = JSON.stringify([app.displayName, app.source]); nameCounts.set(key, (nameCounts.get(key) ?? 0) + 1) }
  const selectedApps = choices.filter((app) => selected.has(app.id) && app.selectable && app.target && !existing.has(applicationTargetKey(app.target)))
  const visible = choices.filter((app) => `${app.displayName} ${app.source} ${app.target?.value ?? ''}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()))
  return <section className="application-picker" aria-label="Choose applications" aria-busy={loading || pending}>
    <div className="application-picker-tools">
      <input ref={searchField} type="search" aria-label="Search applications" placeholder="Search applications…" value={search} onChange={(event) => setSearch(event.target.value)} />
      <button type="button" disabled={loading || pending} onClick={() => void refresh()}>Refresh</button>
    </div>
    {error && <p role="alert">{error}<button type="button" disabled={loading || pending} onClick={() => void refresh()}>Retry</button></p>}
    {loading && <p role="status">Finding applications…</p>}
    {catalog?.partial && <p role="status">Some apps could not be listed. Refresh or add a missing app manually.</p>}
    {!loading && !error && visible.length === 0 && <p>{choices.length ? 'No applications match your search.' : 'No applications were found. Add an app manually.'}</p>}
    <ul className="application-picker-list">
      {visible.map((app) => {
        const added = app.target !== null && existing.has(applicationTargetKey(app.target))
        const ambiguous = (nameCounts.get(JSON.stringify([app.displayName, app.source])) ?? 0) > 1
        return <li key={app.id}><label className={`application-picker-row${added || !app.selectable ? ' unavailable' : ''}`}>
          <input type="checkbox" aria-label={`Select ${app.displayName} (${app.source})${ambiguous && app.target ? ` ${app.target.value}` : ''}`} checked={added || selected.has(app.id)}
            disabled={pending || loading || added || !app.selectable || (selectedApps.length >= 100 && !selected.has(app.id))}
            onChange={(event) => setSelected((previous) => { const next = new Set(previous); if (event.target.checked) next.add(app.id); else next.delete(app.id); return next })} />
          <span className="application-picker-icon" aria-hidden="true">▣</span>
          <span><strong>{app.displayName}</strong><small>{app.source}</small>
            {ambiguous && app.target && <small>{app.target.value}</small>}
            {added ? <small>Already added</small> : !app.selectable && <small>{app.reason ?? 'Add this app manually.'}</small>}</span>
        </label></li>
      })}
    </ul>
    <div className="rule-form-actions">
      <button type="button" className="button button-primary" disabled={pending || loading || selectedApps.length === 0}
        onClick={() => void onAdd(selectedApps.map((app) => ({ displayName: app.displayName, target: app.target!, category: 'OTHER', action: 'BLOCK', enabled: true })))}>
        {pending ? 'Adding…' : `Add selected (${selectedApps.length})`}</button>
      <button type="button" disabled={pending} onClick={onCancel}>Cancel</button>
    </div>
    <button className="application-manual-button" type="button" disabled={pending} onClick={onManual}>App not listed? Add manually</button>
  </section>
}
