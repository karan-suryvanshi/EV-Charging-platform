import { describe, expect, it } from 'vitest'
import { googleMapsDirectionsUrl } from './mapDirections'

describe('Google Maps directions', () => {
  it('uses a known origin and station destination', () => {
    const url = new URL(googleMapsDirectionsUrl({ latitude: 18.52, longitude: 73.85 }, [19.07, 72.87]))
    expect(url.hostname).toBe('www.google.com')
    expect(url.searchParams.get('api')).toBe('1')
    expect(url.searchParams.get('origin')).toBe('19.07,72.87')
    expect(url.searchParams.get('destination')).toBe('18.52,73.85')
    expect(url.searchParams.get('travelmode')).toBe('driving')
  })

  it('lets Google Maps use device location if the app has none', () => {
    const url = new URL(googleMapsDirectionsUrl({ latitude: 18.52, longitude: 73.85 }))
    expect(url.searchParams.get('origin')).toBeNull()
    expect(url.searchParams.get('destination')).toBe('18.52,73.85')
  })

  it('rejects invalid station coordinates', () => {
    expect(googleMapsDirectionsUrl({ latitude: null, longitude: 'bad' })).toBeNull()
    expect(googleMapsDirectionsUrl({ latitude: null, longitude: 73.85 })).toBeNull()
  })
})
