import type { MissionSnapshot } from '../domain/events'
import { StatusBadge } from './StatusBadge'

interface Props {
  missions: MissionSnapshot[]
  selected?: string
  onSelect: (id: string) => void
}

export function MissionList({ missions, selected, onSelect }: Props) {
  if (missions.length === 0) {
    return <p className="empty">Nenhuma missão ainda.</p>
  }

  return (
    <ul className="mission-list">
      {missions.map((mission) => (
        <li key={mission.id}>
          <button className={mission.id === selected ? 'selected' : ''} onClick={() => onSelect(mission.id)}>
            <span className="mission-title">{mission.title}</span>
            <span className="mission-meta">
              <StatusBadge status={mission.status} /> <code>{mission.id}</code>
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
