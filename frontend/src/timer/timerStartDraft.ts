export interface TimerStartDraft {
  durations: { work: string; short: string; long: string }
  projectDraft: { sessionId: string | null; value: string }
}
export const emptyTimerStartDraft: TimerStartDraft = { durations: { work: '', short: '', long: '' }, projectDraft: { sessionId: null, value: '' } }
