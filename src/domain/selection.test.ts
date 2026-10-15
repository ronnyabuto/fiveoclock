import { describe, expect, it } from 'vitest'
import { choosePlace, comparePlaces, placeName } from './selection'
import type { ZoneSource } from './time'

const zone = (name: string, overrides: Partial<ZoneSource> = {}): ZoneSource => ({
  name, alternativeName: name, countryName: 'Country', countryCode: 'XX',
  continentName: 'Europe', mainCities: [], ...overrides,
})

describe('city selection', () => {
  it('prefers familiar cities before metadata quality', () => {
    const london = zone('Europe/London', { mainCities: ['London'], countryName: '' })
    expect(choosePlace([zone('Europe/Other', { mainCities: ['Other'] }), london])).toBe(london)
  })

  it('applies metadata penalties, city count, country order, then stable input order', () => {
    const normal = zone('Other', { mainCities: ['Other'] })
    expect(comparePlaces(normal, { ...normal, continentName: 'Antarctica' })).toBeLessThan(0)
    expect(comparePlaces(normal, { ...normal, countryName: '' })).toBeLessThan(0)
    expect(comparePlaces(normal, { ...normal, mainCities: [] })).toBeLessThan(0)
    expect(comparePlaces({ ...normal, mainCities: ['Other', 'Another'] }, normal)).toBeLessThan(0)
    expect(comparePlaces({ ...normal, countryName: 'A' }, { ...normal, countryName: 'B' })).toBeLessThan(0)
    const tied = { ...normal, name: 'Another' }
    expect(choosePlace([tied, normal])).toBe(tied)
  })

  it('retains fallback names and handles no candidates', () => {
    expect(placeName(zone('America/Some_City'))).toBe('Some City')
    expect(choosePlace([])).toBeUndefined()
  })
})
