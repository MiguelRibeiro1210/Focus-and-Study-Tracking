import { invoke, isTauri } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'

interface NativeResponse { status: number; body: unknown | null }
interface NativeSocketEvent { connectionId: string; kind: 'open' | 'message' | 'error' | 'close'; data: string }
export type NativeExportFormat = 'json' | 'csv-zip'

export function isDesktopShell(): boolean { return isTauri() }

export const nativeFetch: typeof fetch = async (input, init = {}) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
  const parsed = new URL(url, location.origin)
  const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
  const response = await invoke<NativeResponse>('api_request', {
    method: init.method ?? 'GET', path: `${parsed.pathname}${parsed.search}`, body,
  })
  return new Response(response.body === null ? null : JSON.stringify(response.body), {
    status: response.status,
    headers: response.body === null ? undefined : { 'Content-Type': 'application/json' },
  })
}

export function saveNativeDataExport(format: NativeExportFormat): Promise<boolean> {
  return invoke<boolean>('save_data_export', { format })
}

export function openNativeDataFolder(): Promise<void> {
  return invoke<void>('open_data_folder')
}

export function createNativeWebSocket(): Pick<WebSocket, 'close' | 'onopen' | 'onmessage' | 'onclose' | 'onerror'> {
  const connectionId = crypto.randomUUID()
  let unlisten: (() => void) | undefined
  let closed = false
  const socket: Pick<WebSocket, 'close' | 'onopen' | 'onmessage' | 'onclose' | 'onerror'> = {
    onopen: null, onmessage: null, onclose: null, onerror: null,
    close() {
      closed = true
      unlisten?.()
      void invoke('disconnect_session_websocket', { connectionId })
    },
  }
  void listen<NativeSocketEvent>('session-websocket', ({ payload }) => {
    if (closed || payload.connectionId !== connectionId) return
    if (payload.kind === 'open') socket.onopen?.call(socket as unknown as WebSocket, new Event('open'))
    else if (payload.kind === 'message') socket.onmessage?.call(socket as unknown as WebSocket, new MessageEvent('message', { data: payload.data }))
    else if (payload.kind === 'error') socket.onerror?.call(socket as unknown as WebSocket, new Event('error'))
    else socket.onclose?.call(socket as unknown as WebSocket, new CloseEvent('close'))
  }).then((stopListening) => {
    unlisten = stopListening
    if (!closed) void invoke('connect_session_websocket', { connectionId }).catch(() => {
      socket.onerror?.call(socket as unknown as WebSocket, new Event('error'))
      socket.onclose?.call(socket as unknown as WebSocket, new CloseEvent('close'))
    })
  }).catch(() => {
    socket.onerror?.call(socket as unknown as WebSocket, new Event('error'))
    socket.onclose?.call(socket as unknown as WebSocket, new CloseEvent('close'))
  })
  return socket
}
