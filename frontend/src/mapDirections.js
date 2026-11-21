export function googleMapsDirectionsUrl(station, userPosition) {
  if (station?.latitude == null || station?.latitude === ''
    || station?.longitude == null || station?.longitude === '') return null
  const destination = [Number(station?.latitude), Number(station?.longitude)]
  if (!destination.every(Number.isFinite)) return null

  const params = new URLSearchParams({ api: '1', destination: destination.join(','), travelmode: 'driving' })
  if (Array.isArray(userPosition) && userPosition.length === 2
    && userPosition.every((coordinate) => Number.isFinite(Number(coordinate)))) {
    params.set('origin', userPosition.map(Number).join(','))
  }
  return `https://www.google.com/maps/dir/?${params.toString()}`
}
