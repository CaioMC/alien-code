import { useEffect, useState } from 'react'

import { stopMission } from '../api/missionsApi'
import { isActive } from '../domain/events'
import { useMissionTimeline } from '../hooks/useMissionTimeline'
import { DiffPanel } from './DiffPanel'
import { StatusBadge } from './StatusBadge'
import { TerminalPanel } from './TerminalPanel'
import { Timeline } from './Timeline'

interface Props {
  missionId: string
  onChanged: () => void
}

type Tab = 'terminal' | 'diff'

export function MissionView({ missionId, onChanged }: Props) {
  const { timeline, connection, stop } = useMissionTimeline(missionId)
  const [tab, setTab] = useState<Tab>('terminal')
  const [stopping, setStopping] = useState(false)
  const active = isActive(timeline.status)

  // a lista lateral mostra o estado de cada missão: atualiza quando este muda
  useEffect(() => {
    onChanged()
  }, [timeline.status, onChanged])

  useEffect(() => {
    if (!active) {
      setStopping(false)
    }
  }, [active])

  const requestStop = () => {
    setStopping(true)

    if (connection === 'open') {
      stop()
    } else {
      stopMission(missionId).catch(() => setStopping(false))
    }
  }

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && active && !stopping) {
        requestStop()
      }
    }

    window.addEventListener('keydown', onKey)

    return () => window.removeEventListener('keydown', onKey)
  })

  const files = Object.values(timeline.files)

  return (
    <section className="mission">
      <header className="mission-header">
        <div className="mission-heading">
          <h1>{timeline.title ?? missionId}</h1>
          <div className="mission-facts">
            <StatusBadge status={timeline.status} />
            <code>{missionId}</code>
            {timeline.model && <span>{timeline.model}</span>}
            {timeline.budget && <span>{formatTokens(timeline.budget.totalTokens)} tokens</span>}
            <span className={`connection connection-${connection}`} title="Conexão com a timeline">
              {CONNECTION_LABELS[connection]}
            </span>
          </div>
        </div>
        {active && (
          <button className="stop" onClick={requestStop} disabled={stopping} title="Parar (Esc)">
            {stopping ? 'Parando…' : '■ Parar'}
          </button>
        )}
      </header>

      {timeline.failureReason && timeline.status === 'FAILED' && <p className="error">{timeline.failureReason}</p>}

      <div className="mission-body">
        <div className="timeline-column">
          {timeline.prompt && <blockquote className="prompt">{timeline.prompt}</blockquote>}
          <Timeline timeline={timeline} />
        </div>
        <div className="panel-column">
          <nav className="tabs">
            <button className={tab === 'terminal' ? 'active' : ''} onClick={() => setTab('terminal')}>
              Terminal {timeline.terminal.length > 0 && <span className="count">{timeline.terminal.length}</span>}
            </button>
            <button className={tab === 'diff' ? 'active' : ''} onClick={() => setTab('diff')}>
              Diff {files.length > 0 && <span className="count">{files.length}</span>}
            </button>
          </nav>
          {tab === 'terminal' ? <TerminalPanel entries={timeline.terminal} /> : <DiffPanel files={files} />}
        </div>
      </div>
    </section>
  )
}

const CONNECTION_LABELS = {
  connecting: 'conectando…',
  open: 'ao vivo',
  reconnecting: 'reconectando…',
  closed: 'desconectado',
} as const

function formatTokens(tokens: number): string {
  return tokens >= 1000 ? `${(tokens / 1000).toFixed(1)}k` : String(tokens)
}
