import { fireEvent, render, screen, waitFor } from '@testing-library/react'

import type { DeliverySummary } from '../domain/timeline'
import { DeliveryCard } from './DeliveryCard'

const pending: DeliverySummary = {
  status: 'PENDING',
  branch: 'alien/m-3f9a1c2e',
  files: [{ path: 'calc.py', additions: 1, deletions: 1 }],
  additions: 1,
  deletions: 1,
}

describe('DeliveryCard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('esperando revisão mostra os arquivos e os botões', () => {
    render(<DeliveryCard missionId="m-3f9a1c2e" status="AWAITING_REVIEW" delivery={pending} onShowPatch={() => {}} />)

    expect(screen.getByText('calc.py')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Aplicar em/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descartar' })).toBeInTheDocument()
  })

  it('aplicar chama a API e mostra o erro do servidor quando o git recusa', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ detail: 'A branch alien/m-3f9a1c2e já existe' }), { status: 409 }),
    )

    render(<DeliveryCard missionId="m-3f9a1c2e" status="AWAITING_REVIEW" delivery={pending} onShowPatch={() => {}} />)
    fireEvent.click(screen.getByRole('button', { name: /Aplicar em/ }))

    await waitFor(() => expect(screen.getByText('A branch alien/m-3f9a1c2e já existe')).toBeInTheDocument())
    expect(fetch).toHaveBeenCalledWith('/api/missions/m-3f9a1c2e/delivery/approve', { method: 'POST' })
  })

  it('aplicada mostra a branch e some com os botões', () => {
    const applied: DeliverySummary = { ...pending, status: 'APPLIED', headCommit: 'c0ffee0123' }

    render(<DeliveryCard missionId="m-3f9a1c2e" status="COMPLETED" delivery={applied} onShowPatch={() => {}} />)

    expect(screen.queryByRole('button', { name: /Aplicar em/ })).not.toBeInTheDocument()
    expect(screen.getByText(/c0ffee0/)).toBeInTheDocument()
    expect(screen.getByText('git switch alien/m-3f9a1c2e')).toBeInTheDocument()
  })
})
