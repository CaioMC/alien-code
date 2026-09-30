import { useState, type FormEvent } from 'react'

import { startMission, type StartMissionRequest } from '../api/missionsApi'
import type { MissionSnapshot } from '../domain/events'

interface Props {
  onStarted: (mission: MissionSnapshot) => void
}

export function NewMissionForm({ onStarted }: Props) {
  const [prompt, setPrompt] = useState('')
  const [seedType, setSeedType] = useState<'existing' | 'new'>('existing')
  const [path, setPath] = useState('')
  const [ref, setRef] = useState('')
  const [name, setName] = useState('')
  const [model, setModel] = useState('')
  const [error, setError] = useState<string>()
  const [sending, setSending] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(undefined)
    setSending(true)

    const request: StartMissionRequest = {
      prompt,
      model: model.trim() || undefined,
      seed:
        seedType === 'existing'
          ? { type: 'existing', repositories: [{ path: path.trim(), ref: ref.trim() || undefined }] }
          : { type: 'new', name: name.trim() },
    }

    try {
      onStarted(await startMission(request))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSending(false)
    }
  }

  return (
    <form className="new-mission-form" onSubmit={submit}>
      <h1>Nova missão</h1>
      <p className="hint">
        O Alien cria uma Toca (container isolado) com uma cópia do projeto e coloca o agente para trabalhar.
        Você acompanha cada passo ao vivo e pode parar a qualquer momento.
      </p>

      <label>
        O que o agente deve fazer
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Ex.: A função soma em calc.py está errada. Corrija e rode um teste rápido."
          rows={5}
          required
        />
      </label>

      <fieldset>
        <legend>Projeto</legend>
        <label className="inline">
          <input type="radio" checked={seedType === 'existing'} onChange={() => setSeedType('existing')} />
          Repositório local existente
        </label>
        <label className="inline">
          <input type="radio" checked={seedType === 'new'} onChange={() => setSeedType('new')} />
          Projeto novo
        </label>

        {seedType === 'existing' ? (
          <div className="row">
            <label className="grow">
              Caminho do repositório
              <input value={path} onChange={(e) => setPath(e.target.value)} placeholder="/home/voce/projetos/calc" required />
            </label>
            <label>
              Branch/commit (opcional)
              <input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="HEAD" />
            </label>
          </div>
        ) : (
          <label>
            Nome do projeto
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="meu-projeto" required />
          </label>
        )}
      </fieldset>

      <label>
        Modelo (opcional)
        <input value={model} onChange={(e) => setModel(e.target.value)} placeholder="padrão do servidor (ollama/qwen3-8b-t10)" />
      </label>

      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={sending}>
        {sending ? 'Abrindo…' : 'Abrir missão'}
      </button>
    </form>
  )
}
