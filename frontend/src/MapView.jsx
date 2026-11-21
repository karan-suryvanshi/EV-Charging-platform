import { useEffect, useMemo, useRef, useState } from 'react'
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  Circle,
  Polyline,
  useMap,
} from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'

import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import { normalizeMapStations } from './mapStations'
import { googleMapsDirectionsUrl } from './mapDirections'

L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})

const userIcon = L.divIcon({
  className: '',
  html: '<div style="width:20px;height:20px;border-radius:50%;background:#3b82f6;border:4px solid white;box-shadow:0 0 0 5px #3b82f644"></div>',
  iconSize: [20, 20],
  iconAnchor: [10, 10],
})

const createStationIcon = () => {
  return L.divIcon({
    className: '',
    html: `<div style="
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #10b981;
      border: 3px solid white;
      box-shadow: 0 2px 6px rgba(0,0,0,0.4);
    "></div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
    popupAnchor: [0, -11],
  })
}

function FitRoute({ route }) {
  const map = useMap()

  useEffect(() => {
    if (route?.coordinates?.length) {
      map.fitBounds(route.coordinates, { padding: [40, 40] })
    }
  }, [route, map])

  return null
}

function FocusStation({ station }) {
  const map = useMap()

  useEffect(() => {
    if (station) {
      map.flyTo(
        [station.latitude, station.longitude],
        15,
        { duration: 1 }
      )
    }
  }, [station, map])

  return null
}

function FitStations({ stations }) {
  const map = useMap()
  const bounds = useMemo(() => {
    if (!stations.length) return null
    return L.latLngBounds(stations.map((station) => [station.latitude, station.longitude]))
  }, [stations])

  useEffect(() => {
    if (bounds?.isValid()) {
      map.fitBounds(bounds, { padding: [24, 24], maxZoom: 12 })
    }
  }, [bounds, map])

  return null
}

function appendPopupText(parent, label, value) {
  if (value == null || value === '') return

  const paragraph = document.createElement('p')
  paragraph.className = 'text-xs text-slate-600'
  if (label) {
    const strong = document.createElement('strong')
    strong.textContent = `${label}: `
    paragraph.append(strong)
  }
  paragraph.append(document.createTextNode(String(value)))
  parent.append(paragraph)
}

function createStationPopup(station, onBookStation, onSelectStation, onOpenDirections, closePopup) {
  const content = document.createElement('div')
  content.className = 'p-1 space-y-1.5 text-slate-900'

  const title = document.createElement('strong')
  title.className = 'block text-sm'
  title.textContent = station.name || 'Charging station'
  content.append(title)

  if (station.source !== 'PUBLIC_DIRECTORY') {
    appendPopupText(content, 'Operator', station.operator)
  }
  appendPopupText(content, 'Location', station.location)

  if (station.source === 'PUBLIC_DIRECTORY') {
    if (station.ratingKw != null) {
      appendPopupText(content, 'Power rating', `${station.ratingKw} kW`)
    }
  } else {
    appendPopupText(content, 'Charger', station.chargerType || 'Standard EV')
  }

  if (station.pricePerUnit != null) {
    appendPopupText(content, 'Price', `₹${station.pricePerUnit} / unit`)
  }

  if (station.bookable === true) {
    const label = onBookStation
      ? station.source === 'PUBLIC_DIRECTORY'
        ? 'Request Booking →'
        : 'Book Now →'
      : station.source === 'PUBLIC_DIRECTORY'
        ? 'Sign in to request booking →'
        : 'Sign in to book →'
    if (onBookStation) {
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'w-full rounded bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-emerald-700'
      button.textContent = label
      button.addEventListener('click', () => {
        closePopup()
        onBookStation(station)
      })
      content.append(button)
    } else {
      const link = document.createElement('a')
      link.href = '/login'
      link.className = 'block w-full rounded bg-emerald-600 px-3 py-1.5 text-center text-xs font-bold text-white hover:bg-emerald-700'
      link.textContent = label
      content.append(link)
    }
  }

  const directions = document.createElement('button')
  directions.type = 'button'
  directions.className = 'pt-1 text-xs font-medium text-blue-600 hover:underline'
  directions.textContent = 'Select for directions'
  directions.addEventListener('click', () => onSelectStation(station))
  content.append(directions)
  const googleDirections = document.createElement('button')
  googleDirections.type = 'button'
  googleDirections.className = 'ml-2 pt-1 text-xs font-medium text-blue-600 hover:underline'
  googleDirections.textContent = 'Open Google Maps directions'
  googleDirections.addEventListener('click', () => onOpenDirections(station))
  content.append(googleDirections)
  return content
}

function StationClusters({ stations, onBookStation, onSelectStation, onOpenDirections, onMarkersLoaded }) {
  const map = useMap()
  const callbacks = useRef({})

  useEffect(() => {
    callbacks.current = { onBookStation, onSelectStation, onOpenDirections, onMarkersLoaded }
  }, [onBookStation, onSelectStation, onOpenDirections, onMarkersLoaded])

  useEffect(() => {
    let active = true
    const cluster = L.markerClusterGroup({
      chunkedLoading: true,
      chunkInterval: 50,
      chunkDelay: 20,
      maxClusterRadius: 50,
      showCoverageOnHover: false,
      chunkProgress(processed, total) {
        if (active && processed === total) {
          callbacks.current.onMarkersLoaded(total)
        }
      },
    })

    const markers = stations.map((station) => {
      const marker = L.marker([station.latitude, station.longitude], {
        icon: createStationIcon(),
      })
      marker.bindPopup(createStationPopup(
        station,
        (selected) => callbacks.current.onBookStation?.(selected),
        (selected) => callbacks.current.onSelectStation(selected),
        (selected) => callbacks.current.onOpenDirections(selected),
        () => marker.closePopup()
      ), {
        maxWidth: 280,
        maxHeight: 200,
        autoPan: true,
        autoPanPadding: [16, 16],
      })
      marker.on('click', () => callbacks.current.onSelectStation(station))
      return marker
    })

    if (!markers.length) callbacks.current.onMarkersLoaded(0)
    cluster.addLayers(markers)
    map.addLayer(cluster)

    return () => {
      active = false
      map.removeLayer(cluster)
      cluster.clearLayers()
    }
  }, [map, stations])

  return null
}

export default function MapView({ stations = [], onBookStation = null }) {
  const [userPosition, setUserPosition] = useState(null)
  const [selectedId, setSelectedId] = useState('')
  const [stationSearch, setStationSearch] = useState('')
  const [route, setRoute] = useState(null)
  const [routeLoading, setRouteLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [renderedMarkers, setRenderedMarkers] = useState({ stations: null, count: 0 })

  const validStations = useMemo(() => normalizeMapStations(stations), [stations])

  const selectedStation = validStations.find(
    (s) => String(s.id) === String(selectedId)
  )
  const stationOptions = useMemo(() => {
    const query = stationSearch.trim().toLowerCase()
    const matches = query
      ? validStations.filter((station) =>
          [station.name, station.location, station.operator]
            .some((value) => String(value || '').toLowerCase().includes(query))
        )
      : validStations
    const options = matches.slice(0, 100)
    if (selectedStation && !options.some((station) => station.id === selectedStation.id)) {
      options.unshift(selectedStation)
    }
    return options
  }, [validStations, stationSearch, selectedStation])

  function selectStation(station) {
    setSelectedId(String(station.id))
    setStationSearch(station.name || '')
    setRoute(null)
    setMessage('Station selected. Click Get Directions.')
  }

  const renderedStationCount =
    renderedMarkers.stations === validStations ? renderedMarkers.count : 0

  function findMyLocation() {
    setMessage('')

    if (!navigator.geolocation) {
      setMessage('Your browser does not support location services.')
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setUserPosition([
          position.coords.latitude,
          position.coords.longitude,
        ])
        setRoute(null)
        setMessage('Location found. Select a station to get directions.')
      },
      (error) => {
        const errors = {
          1: 'Location permission denied. Allow location access in your browser.',
          2: 'Unable to detect location. Try again.',
          3: 'Location request timed out. Try again.',
        }
        setMessage(errors[error.code] || 'Could not get your location.')
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 }
    )
  }

  async function getDirections() {
    if (!userPosition || !selectedStation) {
      setMessage('First find your location and select a charging station.')
      return
    }

    setRouteLoading(true)
    setRoute(null)
    setMessage('Finding the driving route...')

    try {
      const [startLat, startLon] = userPosition
      const endLat = Number(selectedStation.latitude)
      const endLon = Number(selectedStation.longitude)

      const url =
        `https://router.project-osrm.org/route/v1/driving/` +
        `${startLon},${startLat};${endLon},${endLat}` +
        `?overview=full&geometries=geojson&steps=true`

      const response = await fetch(url)

      if (!response.ok) {
        throw new Error('Routing service is temporarily unavailable.')
      }

      const data = await response.json()

      if (data.code !== 'Ok' || !data.routes?.length) {
        throw new Error('No driving route found for this station.')
      }

      const bestRoute = data.routes[0]

      setRoute({
        coordinates: bestRoute.geometry.coordinates.map(
          ([longitude, latitude]) => [latitude, longitude]
        ),
        distanceKm: bestRoute.distance / 1000,
        durationMin: bestRoute.duration / 60,
        steps: (bestRoute.legs || []).flatMap((leg) =>
          (leg.steps || [])
            .filter((step) => step.maneuver?.type !== 'arrive')
            .map((step) => ({
              instruction:
                step.maneuver?.instruction ||
                step.name ||
                'Continue on the road',
              distance: step.distance,
            }))
        ),
      })

      setMessage('Route found successfully.')
    } catch (error) {
      setMessage(
        `${error.message} Check your internet connection and try again.`
      )
    } finally {
      setRouteLoading(false)
    }
  }

  function openGoogleMapsDirections(station = selectedStation) {
    const url = googleMapsDirectionsUrl(station, userPosition)
    if (url) window.open(url, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="space-y-4">
      {/* Legend */}
      <div className="flex flex-wrap items-center gap-4 rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-xs text-slate-300">
        <span className="font-semibold text-slate-400">Map Key:</span>
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-full bg-emerald-500 inline-block border border-white"></span>
          Station
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-full bg-blue-600 inline-block border border-white"></span>
          Your Location
        </span>
        <span className="ml-auto text-slate-400 font-medium">
          {renderedStationCount === validStations.length
            ? `Showing ${renderedStationCount.toLocaleString()} mapped stations`
            : `Loading map markers: ${renderedStationCount.toLocaleString()} of ${validStations.length.toLocaleString()}`}
        </span>
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={findMyLocation}
          className="rounded-xl bg-emerald-400 px-4 py-3 text-sm font-bold text-slate-950 hover:bg-emerald-300"
        >
          📍 Find My Location
        </button>

        <input
          type="search"
          value={stationSearch}
          onChange={(event) => {
            setStationSearch(event.target.value)
            setSelectedId('')
            setRoute(null)
            setMessage('')
          }}
          placeholder="Search stations by name or location"
          aria-label="Search stations by name or location"
          className="min-w-48 flex-1 rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-sm text-white outline-none focus:border-emerald-400"
        />
        <select
          value={selectedId}
          onChange={(event) => {
            const station = stationOptions.find(
              (item) => String(item.id) === event.target.value
            )
            if (station) selectStation(station)
          }}
          aria-label="Select a charging station"
          className="min-w-48 flex-1 rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-sm text-white outline-none focus:border-emerald-400"
        >
          <option value="">Select from {stationOptions.length} matching stations</option>
          {stationOptions.map((station) => (
            <option key={station.id} value={String(station.id)}>
              {station.name} {station.source !== 'PUBLIC_DIRECTORY' && station.operator ? `(${station.operator})` : ''}
            </option>
          ))}
        </select>

        <button
          type="button"
          onClick={getDirections}
          disabled={!userPosition || !selectedStation || routeLoading}
          className="rounded-xl bg-blue-500 px-4 py-3 text-sm font-bold text-white hover:bg-blue-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {routeLoading ? 'Finding route...' : '🛣️ Get Directions'}
        </button>

        {selectedStation && (
          <button
            type="button"
            onClick={() => openGoogleMapsDirections(selectedStation)}
            className="rounded-xl border border-white/10 px-4 py-3 text-sm font-bold text-slate-200 hover:bg-white/5"
          >
            Open Google Maps
          </button>
        )}
      </div>

      {message && (
        <p
          role="status"
          className="rounded-xl border border-white/10 bg-[#111d30] p-3 text-sm text-slate-300"
        >
          {message}
        </p>
      )}

      {route && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-emerald-400/20 bg-emerald-400/5 p-4">
            <p className="text-sm text-slate-400">Driving distance</p>
            <p className="mt-1 text-2xl font-extrabold text-emerald-300">
              {route.distanceKm.toFixed(1)} km
            </p>
          </div>
          <div className="rounded-xl border border-blue-400/20 bg-blue-400/5 p-4">
            <p className="text-sm text-slate-400">Estimated driving time</p>
            <p className="mt-1 text-2xl font-extrabold text-blue-300">
              {Math.floor(route.durationMin / 60) > 0
                ? `${Math.floor(route.durationMin / 60)} hr `
                : ''}
              {Math.round(route.durationMin % 60)} min
            </p>
          </div>
        </div>
      )}

      <div className="relative z-0 isolate h-[480px] w-full overflow-hidden rounded-xl border border-white/10">
        <MapContainer
          center={[22, 79]}
          zoom={5}
          maxZoom={19}
          scrollWheelZoom
          className="h-full w-full"
        >
          <FitStations stations={validStations} />
          <FocusStation station={selectedStation} />
          <StationClusters
            stations={validStations}
            onBookStation={onBookStation}
            onSelectStation={selectStation}
            onOpenDirections={openGoogleMapsDirections}
            onMarkersLoaded={(count) => setRenderedMarkers({ stations: validStations, count })}
          />

          <TileLayer
            maxZoom={19}
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {userPosition && (
            <>
              <Marker position={userPosition} icon={userIcon}>
                <Popup>Your current location</Popup>
              </Marker>
              <Circle
                center={userPosition}
                radius={100}
                pathOptions={{ color: '#3b82f6', fillOpacity: 0.12 }}
              />
            </>
          )}

          {route && (
            <>
              <Polyline
                positions={route.coordinates}
                pathOptions={{
                  color: '#10b981',
                  weight: 6,
                  opacity: 0.9,
                }}
              />
              <FitRoute route={route} />
            </>
          )}
        </MapContainer>
      </div>
    </div>
  )
}
