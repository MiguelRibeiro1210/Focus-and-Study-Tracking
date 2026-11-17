import { useCallback, useEffect, useState } from 'react'
import { sessionApi, SessionApiError, type SessionApi } from '../api/sessionApi'
import type { SessionHistoryPage, SessionHistoryQuery } from '../api/sessionTypes'

type HistoryReader = Pick<SessionApi, 'getSessionHistory'>

export function useSessionHistory(query: SessionHistoryQuery, api: HistoryReader = sessionApi) {
  const [requestState, setRequestState] = useState<{
    key: string
    data: SessionHistoryPage | null
    error: string | null
  } | null>(null)
  const [retryVersion, setRetryVersion] = useState(0)
  const { from, to, status, page, size } = query
  const requestKey = JSON.stringify([from, to, status, page, size, retryVersion])

  useEffect(() => {
    let current = true
    void api.getSessionHistory({ from, to, status, page, size }).then((result) => {
      if (current) setRequestState({ key: requestKey, data: result, error: null })
    }).catch((cause: unknown) => {
      if (!current) return
      setRequestState({
        key: requestKey,
        data: null,
        error: cause instanceof SessionApiError ? cause.message : 'Could not load session history. Please retry.',
      })
    })
    return () => { current = false }
  }, [api, from, to, status, page, size, requestKey])

  const retry = useCallback(() => setRetryVersion((value) => value + 1), [])
  const currentState = requestState?.key === requestKey ? requestState : null
  return {
    data: currentState?.data ?? null,
    loading: currentState === null,
    error: currentState?.error ?? null,
    retry,
  }
}
