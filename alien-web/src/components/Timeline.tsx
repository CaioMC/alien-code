import { useState } from 'react'

import type { Step, TimelineState } from '../domain/timeline'

export function Timeline({ timeline }: { timeline: TimelineState }) {
  if (timeline.roots.length === 0) {
    return <p className="empty">Aguardando os primeiros eventos…</p>
  }

  return (
    <ol className="timeline">
      {timeline.roots.map((id) => (
        <StepRow key={id} step={timeline.steps[id]!} timeline={timeline} />
      ))}
    </ol>
  )
}

const ICONS: Record<Step['status'], string> = {
  running: '●',
  done: '✓',
  failed: '✕',
  cancelled: '⊘',
}

function StepRow({ step, timeline }: { step: Step; timeline: TimelineState }) {
  const children = timeline.children[step.id] ?? []

  return (
    <li className={`step step-${step.status}`}>
      <div className="step-line">
        <span className="icon">{ICONS[step.status]}</span>
        <span className="step-title">{step.title}</span>
        <span className="duration">{step.status === 'running' ? 'rodando' : formatDuration(step.durationMs)}</span>
      </div>
      {step.error && <p className="step-error">{step.error}</p>}
      {children.length > 0 && (
        <ul className="children">
          {children.map((id) => (
            <ChildRow key={id} step={timeline.steps[id]!} />
          ))}
        </ul>
      )}
    </li>
  )
}

function ChildRow({ step }: { step: Step }) {
  const [open, setOpen] = useState(false)

  if (step.kind === 'text') {
    return <li className="child text">{step.text}</li>
  }

  if (step.kind === 'thinking') {
    return (
      <li className="child thinking">
        <button className="toggle" onClick={() => setOpen(!open)}>
          {open ? '▾' : '▸'} raciocínio
        </button>
        {open && <p>{step.text}</p>}
      </li>
    )
  }

  return (
    <li className={`child tool tool-${step.status}`}>
      <button className="toggle" onClick={() => setOpen(!open)} disabled={!step.text}>
        <span className="arrow">↳</span>
        <span className="tool-name">{step.tool}</span>
        <span className="tool-title">{step.title}</span>
        {step.exitCode !== undefined && step.exitCode !== 0 && <span className="exit">exit {step.exitCode}</span>}
        <span className="duration">{step.status === 'running' ? 'rodando' : formatDuration(step.durationMs)}</span>
      </button>
      {open && step.text && <pre className="tool-output">{step.text}</pre>}
    </li>
  )
}

export function formatDuration(ms?: number): string {
  if (ms === undefined) {
    return ''
  }

  if (ms < 1000) {
    return `${ms} ms`
  }

  const seconds = Math.round(ms / 1000)

  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m${String(seconds % 60).padStart(2, '0')}`
}
