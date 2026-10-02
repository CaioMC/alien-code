import type { MissionStatus } from '../domain/events'

const LABELS: Record<MissionStatus, string> = {
  CREATED: 'CRIADA',
  PROVISIONING: 'PROVISIONANDO',
  EXECUTING: 'EXECUTANDO',
  AWAITING_REVIEW: 'AGUARDANDO REVISÃO',
  COMPLETED: 'CONCLUÍDA',
  FAILED: 'FALHOU',
  CANCELLED: 'CANCELADA',
  REJECTED: 'DESCARTADA',
}

export function StatusBadge({ status }: { status?: MissionStatus }) {
  if (!status) {
    return null
  }

  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status]}</span>
}
