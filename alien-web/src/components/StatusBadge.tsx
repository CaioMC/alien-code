import type { MissionStatus } from '../domain/events'

const LABELS: Record<MissionStatus, string> = {
  CREATED: 'CRIADA',
  PROVISIONING: 'PROVISIONANDO',
  EXECUTING: 'EXECUTANDO',
  COMPLETED: 'CONCLUÍDA',
  FAILED: 'FALHOU',
  CANCELLED: 'CANCELADA',
}

export function StatusBadge({ status }: { status?: MissionStatus }) {
  if (!status) {
    return null
  }

  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status]}</span>
}
