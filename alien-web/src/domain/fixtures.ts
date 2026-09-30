import type { AlienEvent, EventType } from './events'

/** Uma missão completa, na forma que o servidor envia (mesma sequência do MissionLifecycleIT). */
export function completedMission(missionId = 'm-3f9a1c2e'): AlienEvent[] {
  let seq = 0

  const event = (type: EventType, payload: Record<string, unknown>, stepId?: string, parentStepId?: string): AlienEvent => ({
    v: 1,
    missionId,
    seq: ++seq,
    ts: `2026-09-29T12:00:${String(seq).padStart(2, '0')}Z`,
    type,
    stepId,
    parentStepId,
    source: parentStepId ? 'opencode' : 'alien',
    payload,
  })

  return [
    event('mission.created', { title: 'Corrija a soma', prompt: 'Corrija a soma em calc.py', model: 'ollama/qwen3:8b', status: 'CREATED' }),
    event('mission.state', { status: 'PROVISIONING' }),
    event('step.started', { title: 'Provisionar Toca' }, 'toca'),
    event('step.completed', { title: 'Provisionar Toca', durationMs: 4012, tocaId: 'toca-1a2b3c4d' }, 'toca'),
    event('mission.state', { status: 'PROVISIONING', tocaId: 'toca-1a2b3c4d' }),
    event('mission.state', { status: 'EXECUTING', tocaId: 'toca-1a2b3c4d' }),
    event('step.started', { title: 'Corrija a soma', model: 'ollama/qwen3:8b' }, 't1'),
    event('thinking.delta', { text: 'Preciso ver ' }, 't1.thinking.p1', 't1'),
    event('thinking.delta', { text: 'o arquivo.' }, 't1.thinking.p1', 't1'),
    event('tool.started', { tool: 'read', title: '/workspace/calc/calc.py' }, 't1.tool.call_0', 't1'),
    event('tool.completed', { tool: 'read', status: 'completed', title: 'calc.py', output: 'def soma', durationMs: 24 }, 't1.tool.call_0', 't1'),
    event('budget.updated', { inputTokens: 1000, outputTokens: 20, reasoningTokens: 0, totalTokens: 1020 }),
    event('tool.started', { tool: 'edit', title: '/workspace/calc/calc.py' }, 't1.tool.call_1', 't1'),
    event('tool.completed', { tool: 'edit', status: 'completed', title: 'calc.py', output: 'Edit applied successfully.' }, 't1.tool.call_1', 't1'),
    event('file.changed', { path: '/workspace/calc/calc.py', patch: '@@ -1,2 +1,2 @@\n def soma(a, b):\n-    return a - b\n+    return a + b', additions: 1, deletions: 1 }, 't1.file./workspace/calc/calc.py', 't1'),
    event('tool.started', { tool: 'bash', title: 'python3 teste.py' }, 't1.tool.call_2', 't1'),
    event('tool.completed', { tool: 'bash', status: 'failed', title: 'python3 teste.py', output: 'AssertionError', exitCode: 1, durationMs: 146 }, 't1.tool.call_2', 't1'),
    event('terminal.output', { command: 'python3 teste.py', output: 'AssertionError', exitCode: 1 }, 't1.tool.call_2', 't1'),
    event('assistant.delta', { text: 'Corrigido: ' }, 't1.text.p2', 't1'),
    event('assistant.delta', { text: 'soma devolve a + b.' }, 't1.text.p2', 't1'),
    event('step.completed', { title: 'Corrija a soma', durationMs: 3500 }, 't1'),
    event('mission.state', { status: 'COMPLETED', tocaId: 'toca-1a2b3c4d' }),
    event('step.completed', { title: 'Descartar Toca', durationMs: 380 }, 'toca.dispose'),
  ]
}
