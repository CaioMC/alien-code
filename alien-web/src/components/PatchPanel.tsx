import { useEffect, useState } from 'react'

import { getDelivery } from '../api/missionsApi'
import type { Delivery } from '../domain/events'
import { lineClass } from './DiffPanel'

/** O patch completo da entrega (git format-patch), buscado sob demanda: não viaja nos eventos. */
export function PatchPanel({ missionId, version }: { missionId: string; version: string }) {
  const [delivery, setDelivery] = useState<Delivery>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    getDelivery(missionId)
      .then(setDelivery)
      .catch((e: Error) => setError(e.message))
  }, [missionId, version])

  if (error) {
    return <p className="error panel">{error}</p>
  }

  if (!delivery) {
    return <p className="empty panel">Carregando o patch…</p>
  }

  return (
    <div className="diff panel">
      <pre>
        {delivery.patch.split('\n').map((line, index) => (
          <span key={index} className={lineClass(line)}>
            {line + '\n'}
          </span>
        ))}
      </pre>
    </div>
  )
}
