import type { AlienEvent, MissionStatus } from './events'

/**
 * A timeline da missão, montada só a partir dos eventos, em ordem de seq.
 * É um reducer puro: aplicar o mesmo evento duas vezes não muda nada (reconexão segura).
 */

export type StepKind = 'step' | 'tool' | 'text' | 'thinking'
export type StepStatus = 'running' | 'done' | 'failed' | 'cancelled'

export interface Step {
  id: string
  parentId?: string
  kind: StepKind
  title: string
  status: StepStatus
  startedAt: string
  durationMs?: number
  tool?: string
  /** Texto acumulado (assistant/thinking) ou saída da tool. */
  text: string
  exitCode?: number
  error?: string
}

export interface TerminalEntry {
  seq: number
  stepId?: string
  command: string
  output: string
  exitCode?: number
}

export interface FileDiff {
  path: string
  patch: string
  additions: number
  deletions: number
}

export interface Budget {
  inputTokens: number
  outputTokens: number
  reasoningTokens: number
  totalTokens: number
}

export interface TimelineState {
  lastSeq: number
  status?: MissionStatus
  title?: string
  prompt?: string
  model?: string
  failureReason?: string
  tocaId?: string
  steps: Record<string, Step>
  /** Passos da raiz, na ordem em que começaram. */
  roots: string[]
  /** Filhos de cada passo, na ordem em que começaram. */
  children: Record<string, string[]>
  terminal: TerminalEntry[]
  files: Record<string, FileDiff>
  budget?: Budget
}

export const emptyTimeline: TimelineState = {
  lastSeq: 0,
  steps: {},
  roots: [],
  children: {},
  terminal: [],
  files: {},
}

export function applyEvents(state: TimelineState, events: AlienEvent[]): TimelineState {
  return events.reduce(applyEvent, state)
}

export function applyEvent(state: TimelineState, event: AlienEvent): TimelineState {
  if (event.seq <= state.lastSeq) {
    return state
  }

  const next = reduce(state, event)

  return { ...next, lastSeq: event.seq }
}

function reduce(state: TimelineState, event: AlienEvent): TimelineState {
  const p = event.payload

  switch (event.type) {
    case 'mission.created':
      return {
        ...state,
        title: str(p.title),
        prompt: str(p.prompt),
        model: str(p.model),
        status: (str(p.status) as MissionStatus) ?? 'CREATED',
      }

    case 'mission.state':
      return {
        ...state,
        status: str(p.status) as MissionStatus,
        tocaId: str(p.tocaId) ?? state.tocaId,
        failureReason: str(p.reason) ?? state.failureReason,
      }

    case 'step.started':
      return upsert(state, event, 'step', {
        title: str(p.title) ?? event.stepId ?? 'passo',
        status: 'running',
      })

    case 'step.completed':
      return upsert(state, event, 'step', {
        title: str(p.title),
        status: 'done',
        durationMs: num(p.durationMs),
      })

    case 'step.failed':
      return upsert(state, event, 'step', {
        title: str(p.title),
        status: 'failed',
        durationMs: num(p.durationMs),
        error: str(p.reason) ?? str(p.error),
      })

    case 'assistant.delta':
    case 'thinking.delta':
      return appendText(state, event, event.type === 'assistant.delta' ? 'text' : 'thinking', str(p.text) ?? '')

    case 'tool.started':
      return upsert(state, event, 'tool', {
        title: str(p.title) ?? str(p.tool) ?? 'tool',
        tool: str(p.tool),
        status: 'running',
      })

    case 'tool.completed':
      return upsert(state, event, 'tool', {
        title: str(p.title),
        tool: str(p.tool),
        status: toolStatus(str(p.status)),
        text: str(p.output) ?? '',
        exitCode: num(p.exitCode),
        durationMs: num(p.durationMs),
      })

    case 'terminal.output':
      return {
        ...state,
        terminal: [
          ...state.terminal,
          {
            seq: event.seq,
            stepId: event.stepId,
            command: str(p.command) ?? '',
            output: str(p.output) ?? '',
            exitCode: num(p.exitCode),
          },
        ],
      }

    case 'file.changed': {
      const path = str(p.path) ?? '?'

      return {
        ...state,
        files: {
          ...state.files,
          [path]: { path, patch: str(p.patch) ?? '', additions: num(p.additions) ?? 0, deletions: num(p.deletions) ?? 0 },
        },
      }
    }

    case 'budget.updated':
      return {
        ...state,
        budget: {
          inputTokens: num(p.inputTokens) ?? 0,
          outputTokens: num(p.outputTokens) ?? 0,
          reasoningTokens: num(p.reasoningTokens) ?? 0,
          totalTokens: num(p.totalTokens) ?? 0,
        },
      }

    default:
      return state
  }
}

/** Cria o passo na primeira vez que aparece e só sobrescreve os campos que o evento traz. */
function upsert(state: TimelineState, event: AlienEvent, kind: StepKind, changes: Partial<Step>): TimelineState {
  const id = event.stepId

  if (!id) {
    return state
  }

  const existing = state.steps[id]
  const defined = Object.fromEntries(Object.entries(changes).filter(([, value]) => value !== undefined)) as Partial<Step>

  const step: Step = existing
    ? { ...existing, ...defined }
    : {
        id,
        parentId: event.parentStepId,
        kind,
        title: id,
        status: 'running',
        startedAt: event.ts,
        text: '',
        ...defined,
      }

  return {
    ...state,
    steps: { ...state.steps, [id]: step },
    ...(existing ? {} : link(state, id, event.parentStepId)),
  }
}

function appendText(state: TimelineState, event: AlienEvent, kind: StepKind, text: string): TimelineState {
  const id = event.stepId

  if (!id) {
    return state
  }

  const existing = state.steps[id]
  const title = kind === 'thinking' ? 'Raciocínio' : 'Resposta'

  if (existing) {
    return { ...state, steps: { ...state.steps, [id]: { ...existing, text: existing.text + text } } }
  }

  return upsert(state, event, kind, { title, status: 'done', text })
}

function link(state: TimelineState, id: string, parentId?: string): Pick<TimelineState, 'roots' | 'children'> {
  if (!parentId) {
    return { roots: [...state.roots, id], children: state.children }
  }

  return {
    roots: state.roots,
    children: { ...state.children, [parentId]: [...(state.children[parentId] ?? []), id] },
  }
}

function toolStatus(status?: string): StepStatus {
  switch (status) {
    case 'completed':
      return 'done'
    case 'cancelled':
      return 'cancelled'
    default:
      return 'failed'
  }
}

function str(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function num(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined
}
