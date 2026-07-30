export const DAY_MS = 86_400_000
export const TARGET_MS = 17 * 3_600_000

export type ZoneSource = {
  name: string
  alternativeName: string
  countryName: string
  countryCode: string
  continentName: string
  mainCities: string[]
}

export type ZoneResult = ZoneSource & {
  localMs: number
  distanceMs: number
  signedDistanceMs: number
  clock: string
  date: string
  offsetLabel: string
  offsetMinutes: number
}

const timeFormatters = new Map<string, Intl.DateTimeFormat>()
const dateFormatters = new Map<string, Intl.DateTimeFormat>()
const offsetFormatters = new Map<string, Intl.DateTimeFormat>()

function formatter(cache: Map<string, Intl.DateTimeFormat>, zone: string, options: Intl.DateTimeFormatOptions) {
  const cached = cache.get(zone)
  if (cached) return cached
  const created = new Intl.DateTimeFormat('en-GB', { timeZone: zone, numberingSystem: 'latn', ...options })
  cache.set(zone, created)
  return created
}

export function circularSignedDistance(localMs: number, targetMs = TARGET_MS) {
  return ((localMs - targetMs + DAY_MS / 2) % DAY_MS + DAY_MS) % DAY_MS - DAY_MS / 2
}

export function localMilliseconds(date: Date, zone: string) {
  const parts = formatter(timeFormatters, zone, {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((part) => part.type === type)?.value ?? 0)
  return ((value('hour') * 60 + value('minute')) * 60 + value('second')) * 1000 + date.getMilliseconds()
}

export function resultForZone(date: Date, zone: ZoneSource): ZoneResult {
  const localMs = localMilliseconds(date, zone.name)
  const signedDistanceMs = circularSignedDistance(localMs)
  const offsetPart = formatter(offsetFormatters, zone.name, {
    timeZoneName: 'longOffset', hour: '2-digit',
  }).formatToParts(date).find((part) => part.type === 'timeZoneName')?.value ?? 'GMT'
  const match = offsetPart.match(/GMT([+-])(\d{2}):(\d{2})/)
  const offsetMinutes = match ? (match[1] === '+' ? 1 : -1) * (Number(match[2]) * 60 + Number(match[3])) : 0
  return {
    ...zone,
    localMs,
    signedDistanceMs,
    distanceMs: Math.abs(signedDistanceMs),
    clock: formatter(timeFormatters, zone.name, {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    }).format(date),
    date: formatter(dateFormatters, zone.name, {
      weekday: 'short', day: 'numeric', month: 'short',
    }).format(date),
    offsetLabel: offsetPart.replace('GMT', 'UTC'),
    offsetMinutes,
  }
}

export function findNearest(date: Date, zones: ZoneSource[]) {
  const valid: Array<{ zone: ZoneSource; distanceMs: number }> = []
  for (const zone of zones) {
    try {
      const distanceMs = Math.abs(circularSignedDistance(localMilliseconds(date, zone.name)))
      valid.push({ zone, distanceMs })
    } catch {
      // Runtime Intl data can lag IANA. Unsupported identifiers are safely omitted.
    }
  }
  const minimum = Math.min(...valid.map((zone) => zone.distanceMs))
  return valid.filter((item) => item.distanceMs === minimum).map((item) => resultForZone(date, item.zone))
}

export function formatDistance(ms: number) {
  if (ms === 0) return 'exactly 5:00 PM'
  const total = Math.floor(Math.abs(ms) / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes ? `${minutes}m ` : ''}${seconds}s ${ms < 0 ? 'before' : 'after'} 5`
}
