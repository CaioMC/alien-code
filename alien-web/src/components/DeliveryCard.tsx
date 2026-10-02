import { useState } from 'react'

import { approveDelivery, rejectDelivery } from '../api/missionsApi'
import type { MissionStatus } from '../domain/events'
import type { DeliverySummary } from '../domain/timeline'

interface Props {
  missionId: string
  status?: MissionStatus
  delivery: DeliverySummary
  onShowPatch: () => void
}

/**
 * A entrega do agente: o que mudou e a decisão do dev.
 * Aplicar cria a branch no repositório original; o resultado chega de volta pelos eventos.
 */
export function DeliveryCard({ missionId, status, delivery, onShowPatch }: Props) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const reviewing = delivery.status === 'PENDING' && status === 'AWAITING_REVIEW'

  const decide = (action: (id: string) => Promise<unknown>) => {
    setBusy(true)
    setError(undefined)

    action(missionId)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false))
  }

  return (
    <section className={`delivery delivery-${delivery.status.toLowerCase()}`}>
      <header>
        <h2>Entrega</h2>
        <span className="additions">+{delivery.additions}</span>
        <span className="deletions">−{delivery.deletions}</span>
        <button className="link" onClick={onShowPatch}>
          ver patch
        </button>
      </header>

      <ul className="delivery-files">
        {delivery.files.map((file) => (
          <li key={file.path}>
            <code>{file.path}</code>
            <span className="additions">+{file.additions}</span>
            <span className="deletions">−{file.deletions}</span>
          </li>
        ))}
      </ul>

      {reviewing && (
        <div className="delivery-actions">
          <button className="approve" disabled={busy} onClick={() => decide(approveDelivery)}>
            Aplicar em <code>{delivery.branch}</code>
          </button>
          <button className="reject" disabled={busy} onClick={() => decide(rejectDelivery)}>
            Descartar
          </button>
          <p className="hint">Cria uma branch nova a partir do commit base. Sua branch atual e seus arquivos não são tocados.</p>
        </div>
      )}

      {delivery.status === 'APPLIED' && (
        <p className="delivery-result">
          Aplicada em <code>{delivery.branch}</code>
          {delivery.headCommit && <> ({delivery.headCommit.slice(0, 7)})</>}. Para revisar: <code>git switch {delivery.branch}</code>
        </p>
      )}

      {delivery.status === 'REJECTED' && <p className="delivery-result">Descartada: nada chegou ao repositório.</p>}

      {error && <p className="error">{error}</p>}
    </section>
  )
}
