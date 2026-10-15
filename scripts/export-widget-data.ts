import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { stdout } from 'node:process'
import { createHash } from 'node:crypto'
import { rawTimeZones } from '@vvo/tzdb'
import { choosePlace, comparePlaces, placeName } from '../src/domain/selection.ts'
import { findNearest, resultForZone, type ZoneSource } from '../src/domain/time.ts'

const root = new URL('../', import.meta.url)
const zones = rawTimeZones as ZoneSource[]
const hash = createHash('sha256')
for (const source of ['src/domain/time.ts', 'src/domain/selection.ts', 'package-lock.json', 'scripts/export-widget-data.ts']) {
  hash.update(await readFile(new URL(source, root)))
}
const sourceSha256 = hash.digest('hex')
const ranked = [...zones].sort(comparePlaces).map((zone, rank) => ({
  name: zone.name, city: placeName(zone), countryName: zone.countryName, rank,
}))
const dataset = JSON.parse(await readFile(new URL('node_modules/@vvo/tzdb/package.json', root), 'utf8'))
const instants = new Set<number>()
for (const transition of [
  '2026-03-08T07:00:00Z', '2026-03-29T01:00:00Z', '2026-04-04T15:00:00Z',
  '2026-10-03T15:30:00Z', '2026-10-25T01:00:00Z', '2026-11-01T06:00:00Z',
]) {
  for (const delta of [-1, 0, 1]) instants.add(Date.parse(transition) + delta)
}
const start = Date.parse('2026-01-15T00:00:00Z')
for (let step = 0; step < 192; step++) {
  for (const delta of [-1, 0, 1]) instants.add(start + step * 450_000 + delta)
}
for (let step = 0; instants.size < 1000; step++) {
  instants.add(Date.parse('2026-01-01T00:00:00Z') + step * 76_543_219)
}
const offsetSets: Record<string, number>[] = []
const offsetSetIds = new Map<string, number>()
const cases = [...instants].sort((a, b) => a - b).map((epochMs) => {
  const date = new Date(epochMs)
  const nearest = findNearest(date, zones)
  const winner = choosePlace(nearest)
  if (!winner) throw new Error(`No supported zones at ${date.toISOString()}`)
  const offsets = Object.fromEntries(zones.map((zone) => [zone.name, resultForZone(date, zone).offsetMinutes * 60]))
  const key = JSON.stringify(offsets)
  let offsetSetId = offsetSetIds.get(key)
  if (offsetSetId === undefined) {
    offsetSetId = offsetSets.length
    offsetSets.push(offsets)
    offsetSetIds.set(key, offsetSetId)
  }
  return {
    epochMs, offsetSetId, name: winner.name, city: placeName(winner), countryName: winner.countryName,
    offsetSeconds: winner.offsetMinutes * 60, signedDistanceMs: winner.signedDistanceMs,
    tiedNames: nearest.map((zone) => zone.name).sort(),
  }
})
for (const [path, value] of [
  ['android/app/src/main/assets/zones.json', { schemaVersion: 1, sourceSha256, tzdbVersion: dataset.version, zones: ranked }],
  ['android/app/src/sharedTest/resources/parity-fixtures.json', { schemaVersion: 1, sourceSha256, cases, offsetSets }],
] as const) {
  const destination = new URL(path, root)
  await mkdir(new URL('.', destination), { recursive: true })
  await writeFile(destination, `${JSON.stringify(value, null, 2)}\n`)
}
stdout.write(`Exported ${ranked.length} zones and ${cases.length} parity cases to ${fileURLToPath(new URL('android/', root))}\n`)
