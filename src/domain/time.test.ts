import { describe, expect, it } from 'vitest'
import { circularSignedDistance, findNearest, formatDistance, TARGET_MS, type ZoneSource } from './time'

const zone = (name: string): ZoneSource => ({
  name, alternativeName: name, countryName: '', countryCode: '', continentName: '', mainCities: [],
})

describe('nearest-to-five calculation', () => {
  it('is exact at 17:00', () => expect(circularSignedDistance(TARGET_MS)).toBe(0))
  it('uses circular distance around midnight', () => {
    expect(circularSignedDistance(0)).toBe(7 * 3_600_000)
    expect(circularSignedDistance(23 * 3_600_000)).toBe(6 * 3_600_000)
  })
  it('retains all tied zones', () => {
    const result = findNearest(new Date('2026-06-30T17:00:00.000Z'), [zone('UTC'), zone('Etc/UTC'), zone('Asia/Kathmandu')])
    expect(result.map((item) => item.name)).toEqual(['UTC', 'Etc/UTC'])
  })
  it('handles quarter-hour zones using Intl', () => {
    const [result] = findNearest(new Date('2026-06-30T11:15:00.000Z'), [zone('Asia/Kathmandu')])
    expect(result.clock).toBe('17:00:00')
  })
  it('describes direction', () => {
    expect(formatDistance(-61_000)).toBe('1m 1s before 5')
    expect(formatDistance(2_000)).toBe('2s after 5')
  })
})
