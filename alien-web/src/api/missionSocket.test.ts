import { completedMission } from '../domain/fixtures'
import type { AlienEvent } from '../domain/events'
import { MissionSocket, type ConnectionState } from './missionSocket'

/** WebSocket falso: o teste decide quando abre, o que chega e quando cai. */
class FakeSocket {
  static created: FakeSocket[] = []

  onopen?: () => void
  onmessage?: (message: { data: string }) => void
  onclose?: () => void
  sent: string[] = []
  closed = false

  constructor(readonly url: string) {
    FakeSocket.created.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.closed = true
    this.onclose?.()
  }

  deliver(...events: AlienEvent[]) {
    events.forEach((event) => this.onmessage?.({ data: JSON.stringify(event) }))
  }

  drop() {
    this.onclose?.()
  }
}

describe('MissionSocket', () => {
  beforeEach(() => {
    FakeSocket.created = []
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function open(onEvent: (event: AlienEvent) => void, states: ConnectionState[] = []) {
    const socket = new MissionSocket('m-3f9a1c2e', 0, {
      onEvent,
      onState: (state) => states.push(state),
      create: (url) => new FakeSocket(url) as unknown as WebSocket,
      backoff: [100],
      baseUrl: 'ws://127.0.0.1:8080',
    })

    socket.connect()
    FakeSocket.created.at(-1)!.onopen?.()

    return socket
  }

  it('conecta pedindo tudo desde o seq 0', () => {
    open(() => {})

    expect(FakeSocket.created[0]!.url).toBe('ws://127.0.0.1:8080/ws/missions/m-3f9a1c2e?lastSeq=0')
  })

  it('ao cair, reconecta com o último seq recebido e não repete eventos', () => {
    const events = completedMission()
    const received: number[] = []
    const states: ConnectionState[] = []

    open((event) => received.push(event.seq), states)
    FakeSocket.created[0]!.deliver(...events.slice(0, 5))
    FakeSocket.created[0]!.drop()

    vi.advanceTimersByTime(100)

    const second = FakeSocket.created[1]!

    expect(second.url).toContain('lastSeq=5')

    second.onopen?.()
    second.deliver(...events.slice(3, 8))

    expect(received).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
    expect(states).toEqual(['connecting', 'open', 'reconnecting', 'reconnecting', 'open'])
  })

  it('Parar envia o comando stop pelo WebSocket', () => {
    const socket = open(() => {})

    socket.stop()

    expect(FakeSocket.created[0]!.sent).toEqual(['{"type":"stop"}'])
  })

  it('fechar de propósito não reconecta', () => {
    const socket = open(() => {})

    socket.close()
    vi.advanceTimersByTime(1000)

    expect(FakeSocket.created).toHaveLength(1)
  })
})
