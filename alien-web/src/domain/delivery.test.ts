import type { AlienEvent, EventType } from './events'
import { applyEvents, emptyTimeline } from './timeline'

function events(...items: [EventType, Record<string, unknown>][]): AlienEvent[] {
  return items.map(([type, payload], index) => ({
    v: 1,
    missionId: 'm-3f9a1c2e',
    seq: index + 1,
    ts: '2026-09-29T12:00:00Z',
    type,
    stepId: type.startsWith('delivery') ? 'delivery' : undefined,
    source: 'alien',
    payload,
  }))
}

const ready: [EventType, Record<string, unknown>] = [
  'delivery.ready',
  {
    title: 'Entrega aguardando revisão',
    branch: 'alien/m-3f9a1c2e',
    additions: 1,
    deletions: 1,
    files: [{ path: 'calc.py', additions: 1, deletions: 1 }],
  },
]

describe('entrega na timeline', () => {
  it('delivery.ready deixa a entrega pendente e um passo rodando', () => {
    const timeline = applyEvents(emptyTimeline, events(ready, ['mission.state', { status: 'AWAITING_REVIEW' }]))

    expect(timeline.status).toBe('AWAITING_REVIEW')
    expect(timeline.delivery).toMatchObject({ status: 'PENDING', branch: 'alien/m-3f9a1c2e', additions: 1 })
    expect(timeline.delivery?.files).toEqual([{ path: 'calc.py', additions: 1, deletions: 1 }])
    expect(timeline.steps['delivery']).toMatchObject({ status: 'running', title: 'Entrega aguardando revisão' })
  })

  it('delivery.applied guarda o commit e conclui o passo', () => {
    const timeline = applyEvents(
      emptyTimeline,
      events(ready, ['delivery.applied', { title: 'Entrega aplicada em alien/m-3f9a1c2e', headCommit: 'c0ffee0123' }]),
    )

    expect(timeline.delivery).toMatchObject({ status: 'APPLIED', headCommit: 'c0ffee0123' })
    expect(timeline.steps['delivery']).toMatchObject({ status: 'done', title: 'Entrega aplicada em alien/m-3f9a1c2e' })
  })

  it('delivery.rejected marca a entrega como descartada', () => {
    const timeline = applyEvents(emptyTimeline, events(ready, ['delivery.rejected', { title: 'Entrega descartada' }]))

    expect(timeline.delivery?.status).toBe('REJECTED')
    expect(timeline.steps['delivery']?.status).toBe('cancelled')
  })
})
