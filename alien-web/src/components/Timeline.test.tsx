import { fireEvent, render, screen } from '@testing-library/react'

import { completedMission } from '../domain/fixtures'
import { applyEvents, emptyTimeline } from '../domain/timeline'
import { Timeline, formatDuration } from './Timeline'

describe('Timeline', () => {
  it('mostra passos, tools como sub-linhas e o texto do agente', () => {
    render(<Timeline timeline={applyEvents(emptyTimeline, completedMission())} />)

    expect(screen.getByText('Provisionar Toca')).toBeInTheDocument()
    expect(screen.getByText('Descartar Toca')).toBeInTheDocument()
    expect(screen.getByText('python3 teste.py')).toBeInTheDocument()
    expect(screen.getByText('exit 1')).toBeInTheDocument()
    expect(screen.getByText('Corrigido: soma devolve a + b.')).toBeInTheDocument()
  })

  it('a saída da tool abre ao clicar', () => {
    render(<Timeline timeline={applyEvents(emptyTimeline, completedMission())} />)

    expect(screen.queryByText('AssertionError')).not.toBeInTheDocument()

    fireEvent.click(screen.getByText('python3 teste.py'))

    expect(screen.getByText('AssertionError')).toBeInTheDocument()
  })

  it('tool em andamento aparece rodando', () => {
    render(<Timeline timeline={applyEvents(emptyTimeline, completedMission().slice(0, 16))} />)

    expect(screen.getAllByText('rodando')).toHaveLength(2)
  })

  it('formata durações', () => {
    expect(formatDuration(146)).toBe('146 ms')
    expect(formatDuration(4012)).toBe('4s')
    expect(formatDuration(130_000)).toBe('2m10')
    expect(formatDuration(undefined)).toBe('')
  })
})
