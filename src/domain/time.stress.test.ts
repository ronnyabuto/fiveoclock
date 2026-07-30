import { describe, expect, it } from 'vitest'
import { rawTimeZones } from '@vvo/tzdb'
import {
  circularSignedDistance,
  findNearest,
  localMilliseconds,
  resultForZone,
  type ZoneSource,
} from './time'

const zones = rawTimeZones as ZoneSource[]

function supportedZones() {
  return zones.filter((zone) => {
    try {
      new Intl.DateTimeFormat('en', { timeZone: zone.name }).format(0)
      return true
    } catch {
      return false
    }
  })
}

const supported = supportedZones()

describe('worldwide timezone stress suite', () => {
  it('covers every bundled representative zone supported by this runtime', () => {
    expect(zones.length).toBeGreaterThan(300)
    expect(supported.length).toBe(zones.length)

    for (const zone of supported) {
      const result = resultForZone(new Date('2026-06-30T12:34:56.789Z'), zone)
      expect(result.name).toBe(zone.name)
      expect(result.localMs).toBeGreaterThanOrEqual(0)
      expect(result.localMs).toBeLessThan(86_400_000)
      expect(result.distanceMs).toBeGreaterThanOrEqual(0)
      expect(result.distanceMs).toBeLessThanOrEqual(43_200_000)
      expect(result.clock).toMatch(/^\d{2}:\d{2}:\d{2}$/)
      expect(result.offsetLabel).toMatch(/^UTC(?:[+-]\d{2}:\d{2})?$/)
    }
  })

  it('selects the true global minimum over a year of weekly instants', () => {
    const start = Date.parse('2026-01-01T00:00:00.000Z')

    for (let week = 0; week < 52; week += 1) {
      const instant = new Date(start + week * 7 * 86_400_000 + (week % 24) * 3_600_000 + week * 997)
      const expectedMinimum = Math.min(
        ...supported.map((zone) => Math.abs(circularSignedDistance(localMilliseconds(instant, zone.name)))),
      )
      const winners = findNearest(instant, supported)

      expect(winners.length).toBeGreaterThan(0)
      expect(winners.every((winner) => winner.distanceMs === expectedMinimum)).toBe(true)
      expect(new Set(winners.map((winner) => winner.name)).size).toBe(winners.length)
    }
  })

  it('handles every known fractional-offset family', () => {
    const instant = new Date('2026-01-15T00:00:00.000Z')
    const cases = [
      ['America/St_Johns', -210],
      ['Asia/Kolkata', 330],
      ['Asia/Kathmandu', 345],
      ['Australia/Eucla', 525],
      ['Pacific/Chatham', 825],
    ] as const

    for (const [name, expectedMinutes] of cases) {
      const zone = supported.find((item) => item.name === name)
      if (!zone) throw new Error(`Expected timezone data for ${name}`)
      expect(resultForZone(instant, zone).offsetMinutes).toBe(expectedMinutes)
    }
  })

  it('remains correct through representative DST transitions', () => {
    const transitionInstants = [
      '2026-03-08T06:59:59.999Z',
      '2026-03-08T07:00:00.000Z',
      '2026-03-29T00:59:59.999Z',
      '2026-03-29T01:00:00.000Z',
      '2026-10-25T00:59:59.999Z',
      '2026-10-25T01:00:00.000Z',
      '2026-11-01T05:59:59.999Z',
      '2026-11-01T06:00:00.000Z',
    ]

    for (const iso of transitionInstants) {
      const winners = findNearest(new Date(iso), supported)
      expect(winners.length).toBeGreaterThan(0)
      expect(winners.every((winner) => winner.distanceMs <= 30 * 60 * 1000)).toBe(true)
    }
  })
})
