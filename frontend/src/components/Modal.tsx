import { useEffect, useId, useRef } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import { createPortal } from 'react-dom'

export function Modal({ title, children, onClose, alert = false }: { title: string; children: ReactNode; onClose(): void; alert?: boolean }) {
  const titleId = useId()
  const backdrop = useRef<HTMLDivElement>(null)
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    const siblings = Array.from(document.body.children).filter((element) => element !== backdrop.current && !element.hasAttribute('inert'))
    siblings.forEach((element) => element.setAttribute('inert', ''))
    document.body.style.overflow = 'hidden'
    ;(panel.current?.querySelector<HTMLElement>('button:not(:disabled), input:not(:disabled), a[href]') ?? panel.current)?.focus()
    return () => {
      siblings.forEach((element) => element.removeAttribute('inert'))
      document.body.style.overflow = overflow
      if (previous?.isConnected && !previous.closest('[inert]')) previous.focus()
    }
  }, [])
  function keyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); return }
    if (event.key !== 'Tab') return
    const elements = Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href], [tabindex="0"]') ?? [])
    const first = elements[0], last = elements.at(-1)
    if (!first) { event.preventDefault(); return }
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
  return createPortal(<div className="dialog-backdrop" ref={backdrop}>
    <section ref={panel} className="confirm-dialog blocking-dialog" role={alert ? 'alertdialog' : 'dialog'} aria-modal="true" aria-labelledby={titleId} tabIndex={-1} onKeyDown={keyDown}>
      <h2 id={titleId}>{title}</h2>{children}
    </section>
  </div>, document.body)
}
