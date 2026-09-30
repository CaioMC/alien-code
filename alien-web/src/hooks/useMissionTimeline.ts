import { useEffect, useReducer, useRef, useState } from 'react'

import { MissionSocket, type ConnectionState } from '../api/missionSocket'
import type { AlienEvent } from '../domain/events'
import { applyEvent, emptyTimeline, type TimelineState } from '../domain/timeline'

type Action = { type: 'event'; event: AlienEvent } | { type: 'reset' }

function reducer(state: TimelineState, action: Action): TimelineState {
  return action.type === 'reset' ? emptyTimeline : applyEvent(state, action.event)
}

/** Timeline ao vivo de uma missão: começa do seq 0 (replay completo) e segue ao vivo. */
export function useMissionTimeline(missionId?: string) {
  const [timeline, dispatch] = useReducer(reducer, emptyTimeline)
  const [connection, setConnection] = useState<ConnectionState>('closed')
  const socket = useRef<MissionSocket | undefined>(undefined)

  useEffect(() => {
    dispatch({ type: 'reset' })

    if (!missionId) {
      return
    }

    const current = new MissionSocket(missionId, 0, {
      onEvent: (event) => dispatch({ type: 'event', event }),
      onState: setConnection,
    })

    socket.current = current
    current.connect()

    return () => current.close()
  }, [missionId])

  return {
    timeline,
    connection,
    stop: () => socket.current?.stop(),
  }
}
