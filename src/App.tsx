import { useEffect, useMemo, useState } from 'react'
import { rawTimeZones } from '@vvo/tzdb'
import { findNearest, formatDistance, type ZoneResult, type ZoneSource } from './domain/time'

const zones = rawTimeZones as ZoneSource[]
const preferredCities = [
  'London',
  'New York',
  'Tokyo',
  'Sydney',
  'Paris',
  'Berlin',
  'Rome',
  'Cairo',
  'Nairobi',
  'Mumbai',
  'Singapore',
  'Dubai',
  'Los Angeles',
  'Chicago',
  'Toronto',
  'Mexico City',
  'São Paulo',
  'Cape Town',
  'Auckland',
  'Honolulu',
] as const
const preferredCityRank = new Map<string, number>(preferredCities.map((city, index) => [city, index]))

function useNow() {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    let timer = 0
    const tick = () => {
      setNow(new Date())
      timer = window.setTimeout(tick, 1000 - (Date.now() % 1000) + 8)
    }
    const sync = () => {
      window.clearTimeout(timer)
      if (!document.hidden) {
        setNow(new Date())
        timer = window.setTimeout(tick, 1000 - (Date.now() % 1000) + 8)
      }
    }
    sync()
    document.addEventListener('visibilitychange', sync)
    return () => {
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', sync)
    }
  }, [])

  return now
}

function placeName(zone: ZoneResult) {
  return zone.mainCities[0] || zone.name.split('/').pop()?.replaceAll('_', ' ') || zone.name
}

function choosePlace(results: ZoneResult[]) {
  const familiarity = (zone: ZoneResult) => preferredCityRank.get(placeName(zone)) ?? preferredCities.length

  return [...results].sort((a, b) => {
    const undesirable = (zone: ZoneResult) =>
      Number(zone.continentName === 'Antarctica') * 10 +
      Number(!zone.countryName) * 5 +
      Number(!zone.mainCities.length) * 2
    return familiarity(a) - familiarity(b) ||
      undesirable(a) - undesirable(b) ||
      b.mainCities.length - a.mainCities.length ||
      a.countryName.localeCompare(b.countryName)
  })[0]
}

function ClockMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.5 5.5" />
    </svg>
  )
}

function AnalogueClock({ time, place }: { time: string; place: string }) {
  const [hours, minutes, seconds] = time.split(':').map(Number)
  const secondAngle = seconds * 6
  const minuteAngle = minutes * 6 + seconds * 0.1
  const hourAngle = (hours % 12) * 30 + minutes * 0.5

  return (
    <svg className="analogue" viewBox="0 0 320 320" role="img" aria-label={`Analogue clock showing ${time} in ${place}`}>
      <circle className="clock-face" cx="160" cy="160" r="146" />
      {Array.from({ length: 60 }, (_, index) => {
        const major = index % 5 === 0
        return <line key={index} className={major ? 'tick major' : 'tick'} x1="160" y1={major ? '28' : '22'} x2="160" y2={major ? '42' : '29'} transform={`rotate(${index * 6} 160 160)`} />
      })}
      <text x="160" y="63">12</text>
      <text x="257" y="168">3</text>
      <text x="160" y="270">6</text>
      <text x="63" y="168">9</text>
      <line className="hand hour-hand" x1="160" y1="174" x2="160" y2="98" transform={`rotate(${hourAngle} 160 160)`} />
      <line className="hand minute-hand" x1="160" y1="180" x2="160" y2="63" transform={`rotate(${minuteAngle} 160 160)`} />
      <line className="hand second-hand" x1="160" y1="187" x2="160" y2="52" transform={`rotate(${secondAngle} 160 160)`} />
      <circle className="clock-pin" cx="160" cy="160" r="7" />
    </svg>
  )
}

export default function App() {
  const now = useNow()
  const nearest = useMemo(() => findNearest(now, zones), [now])
  const place = choosePlace(nearest)

  if (!place) {
    return <main className="unavailable"><h1>Time data unavailable</h1></main>
  }

  const city = placeName(place)

  return (
    <main className="clock-app">
      <header>
        <div className="brand"><ClockMark /><span>5PM CLOCK</span></div>
        <span className="live"><i /> LIVE</span>
      </header>

      <section className="clock-layout" aria-labelledby="place-name">
        <div className="digital-side">
          <p className="kicker">Closest to 5 PM, right now</p>
          <div className="digital" aria-label={`${place.clock} local time`}>
            <span>{place.clock.slice(0, 5)}</span><small>{place.clock.slice(5)}</small>
          </div>
          <div className="place">
            <h1 id="place-name">{city}</h1>
            <p>{place.countryName}</p>
          </div>
          <div className="facts">
            <span>{formatDistance(place.signedDistanceMs)}</span>
            <span>{place.offsetLabel}</span>
          </div>
        </div>

        <div className="analogue-side">
          <AnalogueClock time={place.clock} place={city} />
        </div>
      </section>
    </main>
  )
}
