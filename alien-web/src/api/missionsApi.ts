import type { MissionSnapshot } from '../domain/events'

export interface StartMissionRequest {
  title?: string
  prompt: string
  model?: string
  seed: { type: 'existing'; repositories: { path: string; ref?: string }[] } | { type: 'new'; name: string }
}

/** Erro da API com a mensagem do ProblemDetail do servidor. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

export async function startMission(request: StartMissionRequest): Promise<MissionSnapshot> {
  return call<MissionSnapshot>('/api/missions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export async function listMissions(): Promise<MissionSnapshot[]> {
  return call<MissionSnapshot[]>('/api/missions')
}

export async function getMission(id: string): Promise<MissionSnapshot> {
  return call<MissionSnapshot>(`/api/missions/${encodeURIComponent(id)}`)
}

export async function stopMission(id: string): Promise<MissionSnapshot> {
  return call<MissionSnapshot>(`/api/missions/${encodeURIComponent(id)}/stop`, { method: 'POST' })
}

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)

  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))

    throw new ApiError(problem.detail ?? `HTTP ${response.status}`, response.status)
  }

  return response.json() as Promise<T>
}
