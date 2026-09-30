import { completedMission } from './fixtures'
import { applyEvent, applyEvents, emptyTimeline } from './timeline'

describe('timeline', () => {
  it('monta os passos da raiz e os filhos da tarefa na ordem em que começaram', () => {
    const timeline = applyEvents(emptyTimeline, completedMission())

    expect(timeline.roots).toEqual(['toca', 't1', 'toca.dispose'])
    expect(timeline.children['t1']).toEqual(['t1.thinking.p1', 't1.tool.call_0', 't1.tool.call_1', 't1.tool.call_2', 't1.text.p2'])
    expect(timeline.steps['toca.dispose']).toMatchObject({ title: 'Descartar Toca', status: 'done' })
  })

  it('acompanha o estado da missão, o modelo e os tokens', () => {
    const timeline = applyEvents(emptyTimeline, completedMission())

    expect(timeline.status).toBe('COMPLETED')
    expect(timeline.title).toBe('Corrija a soma')
    expect(timeline.model).toBe('ollama/qwen3:8b')
    expect(timeline.tocaId).toBe('toca-1a2b3c4d')
    expect(timeline.budget?.totalTokens).toBe(1020)
    expect(timeline.lastSeq).toBe(23)
  })

  it('junta os trechos de texto e raciocínio da mesma parte', () => {
    const timeline = applyEvents(emptyTimeline, completedMission())

    expect(timeline.steps['t1.thinking.p1']).toMatchObject({ kind: 'thinking', text: 'Preciso ver o arquivo.' })
    expect(timeline.steps['t1.text.p2']).toMatchObject({ kind: 'text', text: 'Corrigido: soma devolve a + b.' })
  })

  it('tool começa rodando e termina com status, saída, exit code e duração', () => {
    const events = completedMission()
    const running = applyEvents(emptyTimeline, events.slice(0, 16))

    expect(running.steps['t1.tool.call_2']).toMatchObject({ status: 'running', tool: 'bash', title: 'python3 teste.py' })

    const finished = applyEvents(running, events.slice(16))

    expect(finished.steps['t1.tool.call_2']).toMatchObject({ status: 'failed', exitCode: 1, durationMs: 146, text: 'AssertionError' })
    expect(finished.steps['t1.tool.call_0']).toMatchObject({ status: 'done', title: 'calc.py' })
  })

  it('guarda o terminal e o diff de cada arquivo', () => {
    const timeline = applyEvents(emptyTimeline, completedMission())

    expect(timeline.terminal).toEqual([{ seq: 18, stepId: 't1.tool.call_2', command: 'python3 teste.py', output: 'AssertionError', exitCode: 1 }])
    expect(timeline.files['/workspace/calc/calc.py']).toMatchObject({ additions: 1, deletions: 1 })
  })

  it('ignora eventos repetidos ou antigos (reconexão)', () => {
    const events = completedMission()
    const once = applyEvents(emptyTimeline, events)
    const twice = applyEvents(once, events)

    expect(twice).toBe(once)
    expect(applyEvent(once, events[7]!)).toBe(once)
  })

  it('tool cancelada pelo Parar e falha da tarefa', () => {
    const events = completedMission().slice(0, 10)
    const last = events[events.length - 1]!
    const cancelled = applyEvents(emptyTimeline, [
      ...events,
      { ...last, seq: 11, type: 'tool.completed', payload: { tool: 'read', status: 'cancelled', output: 'Parada pelo usuário' } },
      { ...last, seq: 12, type: 'step.failed', stepId: 't1', parentStepId: undefined, payload: { reason: 'Parada pelo usuário' } },
      { ...last, seq: 13, type: 'mission.state', stepId: undefined, parentStepId: undefined, payload: { status: 'CANCELLED' } },
    ])

    expect(cancelled.steps['t1.tool.call_0']?.status).toBe('cancelled')
    expect(cancelled.steps['t1']).toMatchObject({ status: 'failed', error: 'Parada pelo usuário' })
    expect(cancelled.status).toBe('CANCELLED')
  })
})
