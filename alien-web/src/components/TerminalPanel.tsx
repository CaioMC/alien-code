import { useEffect, useRef } from 'react'

import type { TerminalEntry } from '../domain/timeline'

export function TerminalPanel({ entries }: { entries: TerminalEntry[] }) {
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' })
  }, [entries.length])

  if (entries.length === 0) {
    return <p className="empty panel">Os comandos que o agente rodar aparecem aqui.</p>
  }

  return (
    <div className="terminal panel">
      {entries.map((entry) => (
        <div key={entry.seq} className="terminal-entry">
          <div className="command">$ {entry.command}</div>
          <pre>{entry.output}</pre>
          {entry.exitCode !== undefined && entry.exitCode !== 0 && <div className="exit">exit {entry.exitCode}</div>}
        </div>
      ))}
      <div ref={end} />
    </div>
  )
}
