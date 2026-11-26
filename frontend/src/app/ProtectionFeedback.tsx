import { useEffect, useState } from 'react'
import { createBlockingRulesApi } from '../api/blockingRulesApi'
import type { BlockingRulesApi, BlockingStatus } from '../api/blockingRulesApi'

export function ProtectionFeedback({ api: suppliedApi }: { api?: BlockingRulesApi }) {
  const [api] = useState(() => suppliedApi ?? createBlockingRulesApi())
  const [status, setStatus] = useState<BlockingStatus | null>(null)
  const [dismissed, setDismissed] = useState<string | null>(null)
  const [checkedAt, setCheckedAt] = useState(() => Date.now())
  useEffect(() => {
    if (!api.status) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      try { const value = await api.status!(); if (active) { setStatus(value); setCheckedAt(Date.now()) } }
      catch { if (active) setStatus(null) }
      finally { if (active) timer = setTimeout(() => void refresh(), 2000) }
    }
    void refresh()
    return () => { active = false; clearTimeout(timer) }
  }, [api])
  const notice = status?.notice
  const restoring = status?.applicationProtectionState === 'RESTORING'
  const recent = notice && (restoring || Date.parse(notice.occurredAt) + 15000 > checkedAt) && notice.id !== dismissed
  if (!recent && !status?.ownedRestrictions && !restoring) return null
  return <div className="protection-feedback" role={recent && notice.kind === 'ERROR' ? 'alert' : 'status'}>
    {recent && <span>{notice.message}</span>}
    {restoring ? <span>Application restoration is pending. Keep FocusDesktop open.</span>
      : !!status?.ownedRestrictions && <span>{status.ownedRestrictions} application{status.ownedRestrictions === 1 ? '' : 's'} suspended during focus.</span>}
    {recent && <button type="button" onClick={() => setDismissed(notice.id)} aria-label="Dismiss protection message">Dismiss</button>}
  </div>
}
