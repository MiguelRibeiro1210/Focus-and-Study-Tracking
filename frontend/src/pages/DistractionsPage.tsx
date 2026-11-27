import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createBlockingRulesApi, targetLabel } from '../api/blockingRulesApi'
import type { ApplicationMatchKind, BlockingAction, BlockingRule, BlockingRuleInput, BlockingRulesApi, TargetPlatform } from '../api/blockingRulesApi'

export function DistractionsPage({ api: suppliedApi, onOpenSettings }: { api?: BlockingRulesApi; onOpenSettings?: () => void }) {
  const [api] = useState(() => suppliedApi ?? createBlockingRulesApi())
  const [rules, setRules] = useState<BlockingRule[]>([])
  const [loaded, setLoaded] = useState(false)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('All')
  const [search, setSearch] = useState('')
  const [editor, setEditor] = useState<BlockingRule | 'new' | null>(null)
  const [deleting, setDeleting] = useState<BlockingRule | null>(null)
  const addButton = useRef<HTMLButtonElement>(null)
  const listPanel = useRef<HTMLElement>(null)
  useEffect(() => {
    let active = true
    api.list().then((data) => { if (active) { setRules(data); setLoaded(true) } })
      .catch((cause: unknown) => { if (active) setError(message(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [api])
  async function retry() {
    setLoading(true); setError('')
    try { setRules(await api.list()); setLoaded(true) } catch (cause) { setError(message(cause)) }
    finally { setLoading(false) }
  }
  async function mutate(operation: () => Promise<void>) {
    setPending(true); setError('')
    try { await operation() } catch (cause) { setError(message(cause)) }
    finally { setPending(false) }
  }
  function closeEditor() { setEditor(null); listPanel.current?.focus() }
  async function save(input: BlockingRuleInput) {
    await mutate(async () => {
      const saved = editor === 'new' ? await api.create(input) : await api.patch((editor as BlockingRule).id, input)
      setRules((previous) => editor === 'new' ? [...previous, saved] : previous.map((rule) => rule.id === saved.id ? saved : rule))
      closeEditor()
    })
  }
  const filtered = rules.filter((rule) => (filter === 'All' || (filter === 'Websites' ? rule.target.type === 'WEBSITE' : rule.target.type === 'APPLICATION'))
    && `${rule.displayName} ${targetLabel(rule.target)}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()))

  return <div className="distractions-page">
    <section className="boundaries-banner">
      <div><h2>Healthy boundaries for distractions.</h2><p>Set up rules for websites and applications.</p></div>
      <div className="distraction-header-actions">{onOpenSettings && <button type="button" className="button button-secondary" onClick={onOpenSettings}>Open blocking settings</button>}
      <button ref={addButton} type="button" className="button button-primary" disabled={!loaded || pending || editor !== null || deleting !== null}
        onClick={() => { setError(''); setEditor('new') }}>＋ Add distraction</button></div>
    </section>
    {error && <div role="alert" className="rule-error">{error}{!loaded && <button type="button" disabled={loading} onClick={() => void retry()}>Retry</button>}</div>}
    {editor && <RuleEditor key={editor === 'new' ? 'new' : editor.id} rule={editor === 'new' ? undefined : editor}
      pending={pending} onSave={save} onCancel={closeEditor} />}
    {deleting && <section role="alertdialog" aria-labelledby="delete-rule-title" className="rule-editor card-panel">
      <h2 id="delete-rule-title">Delete {deleting.displayName}?</h2><p>This removes the saved rule.</p>
      <button type="button" disabled={pending} onClick={() => void mutate(async () => {
        await api.delete(deleting.id)
        setRules((previous) => previous.filter((rule) => rule.id !== deleting.id)); setDeleting(null); listPanel.current?.focus()
      })}>Confirm delete</button>
      <button type="button" disabled={pending} onClick={() => { setDeleting(null); listPanel.current?.focus() }}>Cancel</button>
    </section>}
    <div className="distractions-layout">
      <section ref={listPanel} tabIndex={-1} className="distraction-list-panel" aria-label="Saved distractions" aria-busy={loading || pending}>
        <div className="distraction-tools">
          <div className="filter-tabs" role="group" aria-label="Filter distractions">
            {['All', 'Websites', 'Apps'].map((name) => <button key={name} className={`filter-tab${filter === name ? ' selected' : ''}`}
              aria-pressed={filter === name} type="button" onClick={() => setFilter(name)}>{name}</button>)}
          </div>
          <input className="search-field" type="search" placeholder="Search name or target..." aria-label="Search distractions" value={search} onChange={(event) => setSearch(event.target.value)} />
        </div>
        {loading ? <p role="status">Loading saved rules…</p> : loaded && <div className="distraction-table saved-rules-table" role="table" aria-label="Distraction rules">
          <div className="distraction-row distraction-table-head" role="row"><span role="columnheader">DISTRACTION / TYPE</span><span role="columnheader">ACTION / ENABLED</span><span role="columnheader">MANAGE</span></div>
          {filtered.length === 0 ? <p className="future-empty-state table-empty-state">{rules.length === 0 ? 'No distraction rules have been added.' : 'No rules match these filters.'}</p>
            : filtered.map((rule) => <div className="distraction-row" role="row" key={rule.id}>
              <span className="distraction-name" role="cell"><span className="distraction-icon" aria-hidden="true">{rule.target.type === 'WEBSITE' ? '◎' : '▣'}</span><span><strong>{rule.displayName}</strong><small>{targetLabel(rule.target)}</small><small>{rule.target.type === 'WEBSITE' ? 'Website · includes subdomains' : `${rule.target.platform} · ${rule.target.matchKind === 'PROCESS_NAME' ? 'Process name' : 'Executable path'}`}</small></span></span>
              <span className="rule-action" role="cell"><span>{actionLabel(rule.action)}</span><button type="button" className={`toggle-switch${rule.enabled ? ' on' : ''}`} role="switch" aria-checked={rule.enabled} aria-label={`Enable ${rule.displayName}`} disabled={pending || editor !== null || deleting !== null}
                onClick={() => void mutate(async () => {
                  const saved = await api.patch(rule.id, { enabled: !rule.enabled })
                  setRules((previous) => previous.map((item) => item.id === saved.id ? saved : item))
                })}><span /></button></span>
              <span className="rule-controls" role="cell"><button type="button" disabled={pending || editor !== null || deleting !== null} aria-label={`Edit ${rule.displayName}`} onClick={() => { setError(''); setEditor(rule) }}>Edit</button>
                <button type="button" disabled={pending || editor !== null || deleting !== null} aria-label={`Delete ${rule.displayName}`} onClick={() => { setError(''); setDeleting(rule) }}>Delete</button></span>
            </div>)}
        </div>}
      </section>

    </div>
  </div>
}

function RuleEditor({ rule, pending, onSave, onCancel }: { rule?: BlockingRule; pending: boolean; onSave(input: BlockingRuleInput): Promise<void>; onCancel(): void }) {
  const [name, setName] = useState(rule?.displayName ?? '')
  const [type, setType] = useState(rule?.target.type ?? 'WEBSITE')
  const [domain, setDomain] = useState(rule?.target.type === 'WEBSITE' ? rule.target.domain : '')
  const [value, setValue] = useState(rule?.target.type === 'APPLICATION' ? rule.target.value : '')
  const [platform, setPlatform] = useState<TargetPlatform>(rule?.target.type === 'APPLICATION' ? rule.target.platform : 'WINDOWS')
  const [matchKind, setMatchKind] = useState<ApplicationMatchKind>(rule?.target.type === 'APPLICATION' ? rule.target.matchKind : 'EXECUTABLE_PATH')
  const [action, setAction] = useState<BlockingAction>(rule?.action ?? 'BLOCK')
  const [enabled, setEnabled] = useState(rule?.enabled ?? true)
  const [validation, setValidation] = useState('')
  const firstField = useRef<HTMLInputElement>(null)
  useEffect(() => { firstField.current?.focus() }, [])
  function submit(event: FormEvent) {
    event.preventDefault()
    if (type === 'WEBSITE' && /[\s/:?#@]/.test(domain.trim())) { setValidation('Enter a domain without a URL, path or spaces.'); return }
    setValidation('')
    void onSave({ displayName: name.trim(), target: type === 'WEBSITE' ? { type: 'WEBSITE', domain: domain.trim() }
      : { type: 'APPLICATION', platform, matchKind, value: value.trim() }, category: 'OTHER', action, enabled })
  }
  return <form className="rule-editor card-panel" aria-label={rule ? 'Edit distraction' : 'Add distraction'} onSubmit={submit}>
    <h2>{rule ? 'Edit distraction' : 'Add distraction'}</h2>
    <fieldset disabled={pending}>
      <label>Display name<input ref={firstField} required value={name} onChange={(event) => setName(event.target.value)} /></label>
      <label>Target type<select value={type} onChange={(event) => setType(event.target.value as typeof type)}><option value="WEBSITE">Website</option><option value="APPLICATION">Application</option></select></label>
      {type === 'WEBSITE' ? <label>Domain<input aria-label="Domain" required value={domain} onChange={(event) => setDomain(event.target.value)} aria-describedby="domain-help" /><small id="domain-help">Matches this domain and its subdomains.</small></label>
        : <><label>Platform<select value={platform} onChange={(event) => setPlatform(event.target.value as TargetPlatform)}><option value="WINDOWS">Windows</option><option value="MACOS">macOS</option><option value="LINUX">Linux</option></select></label>
          <label>Identify by<select value={matchKind} onChange={(event) => setMatchKind(event.target.value as ApplicationMatchKind)}><option value="EXECUTABLE_PATH">Executable path</option><option value="PROCESS_NAME">Process name</option></select></label>
          <label>Application identifier<input aria-label="Application identifier" required value={value} onChange={(event) => setValue(event.target.value)} /><small>{matchKind === 'EXECUTABLE_PATH' ? 'Full executable path on the selected platform.' : 'Exact process name, including its extension where applicable.'}</small></label></>}
      <label>Action<select value={action} onChange={(event) => setAction(event.target.value as BlockingAction)}>{(['LOG', 'WARN', 'BLOCK'] as const).map((item) => <option key={item} value={item}>{actionLabel(item)}</option>)}</select></label>
      <label className="rule-enabled"><input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />Enabled</label>
      <p>Category: Other. Saving a rule does not activate protection.</p>
      {validation && <p role="alert">{validation}</p>}
      <div className="rule-form-actions"><button type="submit" className="button button-primary">{pending ? 'Saving…' : 'Save rule'}</button><button type="button" onClick={onCancel}>Cancel</button></div>
    </fieldset>
  </form>
}

function message(cause: unknown) { return cause instanceof Error ? cause.message : 'The local rule request failed.' }
function actionLabel(action: BlockingAction) { return { LOG: 'Log', WARN: 'Warn', BLOCK: 'Block' }[action] }
