import { useEffect, useState } from 'react'
import { createBlockingRulesApi } from '../api/blockingRulesApi'
import type { BlockingRulesApi, BlockingStatus, WebsiteProtectionStatus } from '../api/blockingRulesApi'

export function ProtectionFeedback({ api: suppliedApi }: { api?: BlockingRulesApi }) {
  const [api] = useState(() => suppliedApi ?? createBlockingRulesApi())
  const [status, setStatus] = useState<BlockingStatus | null>(null)
  const [website, setWebsite] = useState<WebsiteProtectionStatus | null>(null)
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
  useEffect(() => {
    if (!api.websiteStatus) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      try { const value = await api.websiteStatus!(); if (active) { setWebsite(value); setCheckedAt(Date.now()) } }
      catch { if (active) setWebsite(null) }
      finally { if (active) timer = setTimeout(() => void refresh(), 2000) }
    }
    void refresh()
    return () => { active = false; clearTimeout(timer) }
  }, [api])
  const notice = website?.notice && (!status?.notice || Date.parse(website.notice.occurredAt) > Date.parse(status.notice.occurredAt))
    ? website.notice : status?.notice
  const restoring = status?.applicationProtectionState === 'RESTORING'
  const restoringWebsites = website?.state === 'RESTORING'
  const recent = notice && (restoring || restoringWebsites || Date.parse(notice.occurredAt) + 15000 > checkedAt) && notice.id !== dismissed
  if (!recent && !status?.ownedRestrictions && !restoring && !restoringWebsites) return null
  return <div className="protection-feedback" role={recent && notice.kind === 'ERROR' ? 'alert' : 'status'}>
    {recent && <span>{notice.message}</span>}
    {restoring ? <span>Application restoration is pending. Keep FocusDesktop open.</span>
      : !!status?.ownedRestrictions && <span>{status.ownedRestrictions} application{status.ownedRestrictions === 1 ? '' : 's'} suspended during focus.</span>}
    {restoringWebsites && <span>Network settings restoration is pending. Keep FocusDesktop open.</span>}
    {recent && <button type="button" onClick={() => setDismissed(notice.id)} aria-label="Dismiss protection message">Dismiss</button>}
  </div>
}
