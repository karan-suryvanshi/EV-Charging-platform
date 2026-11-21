export function normalizeMapStations(stations) {
  if (!Array.isArray(stations)) {
    throw new Error('Map station response was not a station list.')
  }

  return stations.flatMap((station) => {
    if (!station || typeof station !== 'object') return []

    const latitudeValue = station.latitude
    const longitudeValue = station.longitude
    if (
      latitudeValue == null ||
      longitudeValue == null ||
      (typeof latitudeValue === 'string' && latitudeValue.trim() === '') ||
      (typeof longitudeValue === 'string' && longitudeValue.trim() === '')
    ) {
      return []
    }

    const latitude = Number(latitudeValue)
    const longitude = Number(longitudeValue)
    if (
      !Number.isFinite(latitude) ||
      !Number.isFinite(longitude) ||
      latitude < -90 ||
      latitude > 90 ||
      longitude < -180 ||
      longitude > 180
    ) {
      return []
    }

    return [{ ...station, latitude, longitude }]
  })
}
