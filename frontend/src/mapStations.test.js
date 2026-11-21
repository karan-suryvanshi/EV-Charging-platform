import { describe, expect, it } from 'vitest'
import { normalizeMapStations } from './mapStations'

describe('map station coordinate normalization', () => {
  it('converts valid numeric strings and preserves numeric coordinates', () => {
    expect(
      normalizeMapStations([
        { id: 'public-1', latitude: '16.29', longitude: '80.43' },
        { id: 'managed-1', latitude: 18.52, longitude: 73.85 },
      ])
    ).toEqual([
      { id: 'public-1', latitude: 16.29, longitude: 80.43 },
      { id: 'managed-1', latitude: 18.52, longitude: 73.85 },
    ])
  })

  it('omits missing, blank, non-finite, and out-of-range coordinates', () => {
    expect(
      normalizeMapStations([
        { id: 'null', latitude: null, longitude: 10 },
        { id: 'missing', latitude: 10 },
        { id: 'blank', latitude: '', longitude: 10 },
        { id: 'nan', latitude: 'not-a-number', longitude: 10 },
        { id: 'latitude-range', latitude: 91, longitude: 10 },
        { id: 'longitude-range', latitude: 10, longitude: 181 },
      ])
    ).toEqual([])
  })

  it('rejects an unexpected paginated or otherwise malformed response shape', () => {
    expect(() => normalizeMapStations({ content: [] })).toThrow(
      'Map station response was not a station list.'
    )
  })
})
