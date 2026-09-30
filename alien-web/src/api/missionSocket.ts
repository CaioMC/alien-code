import type { AlienEvent } from '../domain/events'

export type ConnectionState = 'connecting' | 'open' | 'reconnecting' | 'closed'

export interface MissionSocketOptions {
  onEvent: (event: AlienEvent) => void
  onState?: (state: ConnectionState) => void
  /** Fábrica do WebSocket (troca nos testes). */
  create?: (url: string) => WebSocket
  /** Espera entre tentativas, em ms, pela ordem; a última se repete. */
  backoff?: number[]
  baseUrl?: string
}

/**
 * Canal da timeline de uma missão. Guarda o último seq recebido e, se a conexão cair, reconecta
 * sozinho com {@code lastSeq}: o servidor reenvia só o que faltou (especificação, seção 8.4).
 */
export class MissionSocket {
  private socket?: WebSocket
  private lastSeq: number
  private attempt = 0
  private closedByUs = false
  private timer?: ReturnType<typeof setTimeout>

  constructor(
    private readonly missionId: string,
    lastSeq: number,
    private readonly options: MissionSocketOptions,
  ) {
    this.lastSeq = lastSeq
  }

  connect(): void {
    this.closedByUs = false
    this.options.onState?.(this.attempt === 0 ? 'connecting' : 'reconnecting')

    const socket = (this.options.create ?? ((url) => new WebSocket(url)))(this.url())

    socket.onopen = () => {
      this.attempt = 0
      this.options.onState?.('open')
    }

    socket.onmessage = (message) => {
      const event = JSON.parse(String(message.data)) as AlienEvent

      if (event.seq > this.lastSeq) {
        this.lastSeq = event.seq
        this.options.onEvent(event)
      }
    }

    socket.onclose = () => {
      if (this.closedByUs) {
        this.options.onState?.('closed')
        return
      }

      this.scheduleReconnect()
    }

    this.socket = socket
  }

  stop(): void {
    this.socket?.send(JSON.stringify({ type: 'stop' }))
  }

  close(): void {
    this.closedByUs = true
    clearTimeout(this.timer)
    this.socket?.close()
  }

  get seq(): number {
    return this.lastSeq
  }

  private scheduleReconnect(): void {
    const backoff = this.options.backoff ?? [500, 1000, 2000, 5000]
    const delay = backoff[Math.min(this.attempt, backoff.length - 1)] ?? 5000

    this.attempt++
    this.options.onState?.('reconnecting')
    this.timer = setTimeout(() => this.connect(), delay)
  }

  private url(): string {
    const base = this.options.baseUrl ?? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`

    return `${base}/ws/missions/${encodeURIComponent(this.missionId)}?lastSeq=${this.lastSeq}`
  }
}
