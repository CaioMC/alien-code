/** Envelope enviado pelo Alien Server (especificação, seção 8.1). */
export interface AlienEvent {
  v: number
  missionId: string
  seq: number
  ts: string
  type: EventType
  stepId?: string
  parentStepId?: string
  source: 'alien' | 'opencode' | 'docker'
  payload: Record<string, unknown>
}

export type EventType =
  | 'mission.created'
  | 'mission.state'
  | 'step.started'
  | 'step.completed'
  | 'step.failed'
  | 'assistant.delta'
  | 'thinking.delta'
  | 'tool.started'
  | 'tool.completed'
  | 'terminal.output'
  | 'file.changed'
  | 'diff.updated'
  | 'budget.updated'
  | 'delivery.ready'
  | 'delivery.applied'
  | 'delivery.rejected'

export type MissionStatus =
  | 'CREATED'
  | 'PROVISIONING'
  | 'EXECUTING'
  | 'AWAITING_REVIEW'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'REJECTED'

export const ACTIVE_STATUSES: readonly MissionStatus[] = ['CREATED', 'PROVISIONING', 'EXECUTING']

export function isActive(status: MissionStatus | undefined): boolean {
  return status !== undefined && ACTIVE_STATUSES.includes(status)
}

/** Resposta de GET /api/missions/{id}. */
export interface MissionSnapshot {
  id: string
  title: string
  prompt: string
  model: string
  status: MissionStatus
  tocaId?: string
  createdAt: string
  finishedAt?: string
  failureReason?: string
  lastSeq?: number
}

export type DeliveryStatus = 'PENDING' | 'APPLIED' | 'REJECTED'

export interface ChangedFile {
  path: string
  additions: number
  deletions: number
}

/** Resposta de GET /api/missions/{id}/delivery: o patch completo que o agente produziu. */
export interface Delivery {
  missionId: string
  status: DeliveryStatus
  repository: string
  repositoryPath: string
  baseCommit: string
  branch: string
  headCommit?: string
  additions: number
  deletions: number
  files: ChangedFile[]
  patch: string
  createdAt: string
  resolvedAt?: string
}
