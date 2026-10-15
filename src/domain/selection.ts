import type { ZoneSource } from './time'

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

export function placeName(zone: ZoneSource) {
  return zone.mainCities[0] || zone.name.split('/').pop()?.replaceAll('_', ' ') || zone.name
}

export function comparePlaces(a: ZoneSource, b: ZoneSource) {
  const familiarity = (zone: ZoneSource) => preferredCityRank.get(placeName(zone)) ?? preferredCities.length
  const undesirable = (zone: ZoneSource) =>
    Number(zone.continentName === 'Antarctica') * 10 +
    Number(!zone.countryName) * 5 +
    Number(!zone.mainCities.length) * 2
  return familiarity(a) - familiarity(b) ||
    undesirable(a) - undesirable(b) ||
    b.mainCities.length - a.mainCities.length ||
    a.countryName.localeCompare(b.countryName)
}

export function choosePlace<T extends ZoneSource>(results: T[]): T | undefined {
  return [...results].sort(comparePlaces)[0]
}
