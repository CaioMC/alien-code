import { useCallback, useEffect, useState } from 'react'

import { listMissions } from '../api/missionsApi'
import type { MissionSnapshot } from '../domain/events'
import { MissionList } from './MissionList'
import { MissionView } from './MissionView'
import { NewMissionForm } from './NewMissionForm'

const REFRESH_MS = 5000

export function App() {
  const [missions, setMissions] = useState<MissionSnapshot[]>([])
  const [selected, setSelected] = useState<string | undefined>(() => location.hash.slice(1) || undefined)
  const [offline, setOffline] = useState(false)

  const refresh = useCallback(() => {
    listMissions()
      .then((list) => {
        setMissions(list)
        setOffline(false)
      })
      .catch(() => setOffline(true))
  }, [])

  useEffect(() => {
    refresh()

    const timer = setInterval(refresh, REFRESH_MS)

    return () => clearInterval(timer)
  }, [refresh])

  const select = (id?: string) => {
    setSelected(id)
    history.replaceState(null, '', id ? `#${id}` : location.pathname)
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <header className="brand">
          <img src="/alien.svg" alt="" width={22} height={22} />
          <span>Alien Code</span>
        </header>
        <button className="new-mission" onClick={() => select(undefined)}>
          + Nova missão
        </button>
        {offline && <p className="offline">Servidor fora do ar (127.0.0.1:8080)</p>}
        <MissionList missions={missions} selected={selected} onSelect={select} />
      </aside>
      <main className="content">
        {selected ? (
          <MissionView key={selected} missionId={selected} onChanged={refresh} />
        ) : (
          <NewMissionForm
            onStarted={(mission) => {
              refresh()
              select(mission.id)
            }}
          />
        )}
      </main>
    </div>
  )
}
