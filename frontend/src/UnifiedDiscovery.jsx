import { useState, useEffect, useCallback, useRef } from 'react'
import { lazy, Suspense } from 'react'
import { API_URL } from './api'

const MapView = lazy(() => import('./MapView'))

const PAGE_SIZE_OPTIONS = [12, 24, 48]

export default function UnifiedDiscovery({ onBookStation = null }) {
  const [stations, setStations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(12)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  // Filters
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [source, setSource] = useState('ALL')
  const [selectedState, setSelectedState] = useState('')
  const [selectedDistrict, setSelectedDistrict] = useState('')
  const [selectedCity, setSelectedCity] = useState('')
  const [bookingEnabledOnly, setBookingEnabledOnly] = useState(false)

  // View mode: 'grid' or 'map'
  const [viewMode, setViewMode] = useState('grid')
  const [mapStations, setMapStations] = useState([])
  const [mapLoading, setMapLoading] = useState(false)
  const [mapError, setMapError] = useState('')
  const [mapRequestVersion, setMapRequestVersion] = useState(0)
  const [filterError, setFilterError] = useState('')

  // Filter options from backend
  const [filterOptions, setFilterOptions] = useState({
    states: [],
    districts: [],
    cities: [],
    sources: ['ALL', 'MANAGED', 'PUBLIC'],
  })

  const requestController = useRef(null)

  function handleBookStation(station) {
    if (station.bookingEnabled !== true || station.bookable !== true || station.internalId == null) return
    onBookStation?.({ ...station, id: station.internalId })
  }

  // Debounce search text
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(search.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search])

  // Fetch cascading filter options
  useEffect(() => {
    const controller = new AbortController()
    async function fetchFilters() {
      setFilterError('')
      try {
        const params = new URLSearchParams()
        if (selectedState) params.set('state', selectedState)
        if (selectedDistrict) params.set('district', selectedDistrict)

        const res = await fetch(`${API_URL}/stations/discovery/filters?${params.toString()}`, {
          signal: controller.signal,
        })
        if (!res.ok) {
          throw new Error(`Failed to load location filters (HTTP status ${res.status})`)
        }
        const data = await res.json()
        if (!controller.signal.aborted) setFilterOptions(data)
      } catch (err) {
        if (err.name !== 'AbortError' && !controller.signal.aborted) {
          setFilterError(err.message || 'Could not load location filters.')
        }
      }
    }
    fetchFilters()
    return () => {
      controller.abort()
    }
  }, [selectedState, selectedDistrict])

  // Load paginated discovery stations
  const loadStations = useCallback(async () => {
    requestController.current?.abort()
    const controller = new AbortController()
    requestController.current = controller

    setLoading(true)
    setError('')

    try {
      const params = new URLSearchParams({
        page: String(page),
        size: String(pageSize),
        source: source,
      })
      if (debouncedSearch) params.set('q', debouncedSearch)
      if (selectedState) params.set('state', selectedState)
      if (selectedDistrict) params.set('district', selectedDistrict)
      if (selectedCity) params.set('city', selectedCity)
      if (bookingEnabledOnly) params.set('bookingEnabled', 'true')

      const response = await fetch(`${API_URL}/stations/discovery?${params.toString()}`, {
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(`Failed to load stations (HTTP status ${response.status})`)
      }

      const data = await response.json()
      if (controller.signal.aborted) return

      setStations(Array.isArray(data?.content) ? data.content : [])
      setTotalPages(Number(data?.totalPages ?? 0))
      setTotalElements(Number(data?.totalElements ?? 0))
    } catch (err) {
      if (err.name === 'AbortError' || controller.signal.aborted) return
      setStations([])
      setTotalPages(0)
      setTotalElements(0)
      setError(err.message || 'Could not connect to the backend.')
    } finally {
      if (requestController.current === controller) {
        requestController.current = null
        setLoading(false)
      }
    }
  }, [
    page,
    pageSize,
    source,
    debouncedSearch,
    selectedState,
    selectedDistrict,
    selectedCity,
    bookingEnabledOnly,
  ])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadStations()
    }, 0)
    return () => {
      window.clearTimeout(timer)
      requestController.current?.abort()
      requestController.current = null
    }
  }, [loadStations])

  // Load map points when in Map view
  useEffect(() => {
    if (viewMode !== 'map') return
    const controller = new AbortController()

    async function fetchMapPoints() {
      setMapLoading(true)
      setMapError('')
      try {
        const params = new URLSearchParams({ limit: '100000', source })
        if (bookingEnabledOnly) params.set('bookingEnabled', 'true')
        if (debouncedSearch) params.set('q', debouncedSearch)
        if (selectedState) params.set('state', selectedState)
        if (selectedDistrict) params.set('district', selectedDistrict)
        if (selectedCity) params.set('city', selectedCity)

        const res = await fetch(`${API_URL}/stations/discovery/map?${params.toString()}`, {
          signal: controller.signal,
        })
        if (!res.ok) {
          throw new Error(`Failed to load map stations (HTTP status ${res.status})`)
        }
        const data = await res.json()
        if (!Array.isArray(data)) {
          throw new Error('Map station response was not a station list.')
        }
        if (!controller.signal.aborted) setMapStations(data)
      } catch (err) {
        if (err.name !== 'AbortError' && !controller.signal.aborted) {
          setMapStations([])
          setMapError(err.message || 'Could not load map stations.')
        }
      } finally {
        if (!controller.signal.aborted) setMapLoading(false)
      }
    }

    fetchMapPoints()
    return () => {
      controller.abort()
    }
  }, [
    viewMode,
    source,
    debouncedSearch,
    selectedState,
    selectedDistrict,
    selectedCity,
    bookingEnabledOnly,
    mapRequestVersion,
  ])

  function clearAllFilters() {
    setSearch('')
    setDebouncedSearch('')
    setSource('ALL')
    setSelectedState('')
    setSelectedDistrict('')
    setSelectedCity('')
    setBookingEnabledOnly(false)
    setPage(0)
  }

  return (
    <section className="space-y-6">
      {/* Search & Filter Header Bar */}
      <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-white/10 pb-4">
          <div>
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <span>⚡</span> Unified Charging Station Discovery
            </h2>
            <p className="mt-1 text-xs text-slate-400">
              Explore charging stations from across India
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setViewMode('grid')}
              className={`rounded-xl px-4 py-2 text-xs font-bold transition ${
                viewMode === 'grid'
                  ? 'bg-emerald-400 text-slate-950 shadow-md'
                  : 'border border-white/10 text-slate-300 hover:bg-white/5'
              }`}
            >
              ⊞ Grid View
            </button>
            <button
              type="button"
              onClick={() => setViewMode('map')}
              className={`rounded-xl px-4 py-2 text-xs font-bold transition ${
                viewMode === 'map'
                  ? 'bg-emerald-400 text-slate-950 shadow-md'
                  : 'border border-white/10 text-slate-300 hover:bg-white/5'
              }`}
            >
              🗺️ Map View
            </button>
          </div>
        </div>

        {/* Filter Form Controls */}
        <div className="mt-4 grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6">
          {/* Keyword Search */}
          <div className="xl:col-span-2">
            <label htmlFor="discoverySearch" className="mb-1 block text-xs font-semibold text-slate-400">
              Search Station / Location
            </label>
            <input
              id="discoverySearch"
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="e.g. Pune, address, landmark..."
              className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
            />
          </div>

          {/* Station category */}
          <div>
            <label htmlFor="discoverySource" className="mb-1 block text-xs font-semibold text-slate-400">
              Station Category
            </label>
            <select
              id="discoverySource"
              value={source}
              onChange={(e) => {
                setSource(e.target.value)
                if (e.target.value === 'PUBLIC')                 setBookingEnabledOnly(false)
                setPage(0)
              }}
              className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
            >
              <option value="ALL">All Stations (Combined)</option>
              <option value="MANAGED">Managed Stations</option>
              <option value="PUBLIC">Public Stations</option>
            </select>
          </div>

          {/* State */}
          <div>
            <label htmlFor="discoveryState" className="mb-1 block text-xs font-semibold text-slate-400">
              State
            </label>
            <select
              id="discoveryState"
              value={selectedState}
              onChange={(e) => {
                setSelectedState(e.target.value)
                setSelectedDistrict('')
                setSelectedCity('')
                setPage(0)
              }}
              className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
            >
              <option value="">All States</option>
              {(filterOptions.states || []).map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          {/* District */}
          <div>
            <label htmlFor="discoveryDistrict" className="mb-1 block text-xs font-semibold text-slate-400">
              District
            </label>
            <select
              id="discoveryDistrict"
              value={selectedDistrict}
              onChange={(e) => {
                setSelectedDistrict(e.target.value)
                setSelectedCity('')
                setPage(0)
              }}
              disabled={!selectedState}
              className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 disabled:opacity-40"
            >
              <option value="">All Districts</option>
              {(filterOptions.districts || []).map((d) => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
          </div>

          {/* City */}
          <div>
            <label htmlFor="discoveryCity" className="mb-1 block text-xs font-semibold text-slate-400">
              City / Village
            </label>
            <select
              id="discoveryCity"
              value={selectedCity}
              onChange={(e) => {
                setSelectedCity(e.target.value)
                setPage(0)
              }}
              disabled={!selectedDistrict}
              className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 disabled:opacity-40"
            >
              <option value="">All Cities</option>
              {(filterOptions.cities || []).map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Booking status filter */}
        <div className="mt-3 grid gap-3 sm:grid-cols-2 md:grid-cols-4 items-center">
          <div className="flex items-center gap-2 pt-4">
            <input
              id="bookingEnabledOnly"
              type="checkbox"
              checked={bookingEnabledOnly}
              onChange={(e) => {
                setBookingEnabledOnly(e.target.checked)
                setPage(0)
              }}
              className="h-4 w-4 rounded accent-emerald-400"
            />
            <label htmlFor="bookingEnabledOnly" className="text-xs font-semibold text-slate-300 cursor-pointer">
              Booking enabled stations
            </label>
          </div>

          <div className="pt-4 flex justify-end">
            <button
              type="button"
              onClick={clearAllFilters}
              className="rounded-xl border border-white/10 px-4 py-2 text-xs font-semibold text-emerald-300 hover:bg-white/5"
            >
              Reset Filters
            </button>
          </div>
        </div>
        {filterError && (
          <p role="alert" className="mt-3 text-xs text-amber-300">{filterError}</p>
        )}

        {/* Counter & Status */}
        <div className="mt-4 flex flex-wrap items-center justify-between border-t border-white/10 pt-3 text-xs text-slate-400">
          <div>
            Found <span className="font-bold text-emerald-300">{totalElements.toLocaleString()}</span> stations
            {totalElements > 0 && (
              <span> · Page {page + 1} of {Math.max(totalPages, 1)}</span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span>Per page:</span>
            {PAGE_SIZE_OPTIONS.map((sz) => (
              <button
                key={sz}
                type="button"
                onClick={() => {
                  setPageSize(sz)
                  setPage(0)
                }}
                className={`px-2 py-0.5 rounded ${
                  pageSize === sz ? 'bg-emerald-400 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                }`}
              >
                {sz}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Loading & Error States */}
      {loading && (
        <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-12 text-center text-slate-400">
          <p className="animate-pulse">Loading charging stations across India...</p>
        </div>
      )}

      {error && (
        <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-5 text-red-300">
          <p>{error}</p>
          <button type="button" onClick={loadStations} className="mt-2 text-xs underline font-bold">
            Retry Search
          </button>
        </div>
      )}

      {/* MAP VIEW */}
      {viewMode === 'map' && (
        <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 shadow-xl">
          {mapLoading ? (
            <p className="py-10 text-center text-sm text-slate-400">Loading interactive map pins...</p>
          ) : mapError ? (
            <div role="alert" className="py-10 text-center text-sm text-red-300">
              <p>{mapError}</p>
              <button
                type="button"
                onClick={() => setMapRequestVersion((version) => version + 1)}
                className="mt-3 underline"
              >
                Retry map
              </button>
            </div>
          ) : (
            <Suspense fallback={<p className="py-10 text-center text-sm text-slate-400">Loading interactive map...</p>}>
              <MapView
                stations={mapStations}
                onBookStation={handleBookStation}
              />
            </Suspense>
          )}
        </div>
      )}

      {/* GRID VIEW */}
      {viewMode === 'grid' && !loading && !error && (
        <>
          {stations.length === 0 ? (
            <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-12 text-center">
              <span className="text-4xl">🔍</span>
              <h3 className="mt-3 text-lg font-bold text-white">No charging stations matched your criteria</h3>
              <p className="mt-1 text-xs text-slate-400">Try adjusting your keyword, state, or charger type filters.</p>
              <button
                type="button"
                onClick={clearAllFilters}
                className="mt-4 rounded-xl bg-emerald-400 px-4 py-2 text-xs font-bold text-slate-950"
              >
                Clear all filters
              </button>
            </div>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {stations.map((st) => {
                const isManaged = st.source === 'MANAGED'

                return (
                  <article
                    key={st.id}
                    className={`rounded-2xl border p-5 flex flex-col justify-between transition hover:-translate-y-1 ${
                      isManaged
                        ? 'border-emerald-500/40 bg-[#0e1e27] hover:border-emerald-400 shadow-lg shadow-emerald-950/20'
                        : 'border-white/10 bg-[#0e1727] hover:border-sky-400/30'
                    }`}
                  >
                    <div>
                        <div className="mb-3 flex items-start justify-end gap-2">
                          <span
                            className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            isManaged
                              ? st.available
                                ? 'bg-emerald-500/20 text-emerald-300'
                                : 'bg-red-500/20 text-red-300'
                              : 'bg-slate-800 text-slate-300'
                          }`}
                        >
                          {isManaged
                            ? st.available
                              ? 'Available'
                              : 'Offline'
                            : st.available == null
                              ? 'Availability unverified'
                              : 'Availability unverified'}
                        </span>
                      </div>

                      {/* Station Title */}
                      <h3 className="text-base font-bold text-white leading-snug">{st.name}</h3>

                      <p className="mt-2 text-xs leading-relaxed text-slate-400 min-h-8">
                        📍 {st.location || 'Location details unavailable'}
                      </p>
                      {isManaged && (
                        <p className="mt-1 text-xs font-semibold text-emerald-400">
                          {st.operator || 'Host Managed'}
                        </p>
                      )}

                      {/* Managed Reviews / Rating */}
                      {isManaged && (
                        <div className="mt-2 flex items-center gap-1.5 text-xs text-amber-400">
                          <span>
                            {'★'.repeat(Math.round(st.averageRating || 0))}
                            {'☆'.repeat(5 - Math.round(st.averageRating || 0))}
                          </span>
                          <span className="text-slate-400">
                            {st.reviewCount > 0
                              ? `${st.averageRating} (${st.reviewCount} reviews)`
                              : 'No reviews yet'}
                          </span>
                        </div>
                      )}

                      {/* Specs Row */}
                      <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
                        <div className="rounded-lg bg-[#111d30] p-2">
                          <p className="text-[10px] text-slate-500">Charger Type</p>
                          <p className="mt-0.5 font-semibold text-slate-200 truncate">
                            {st.chargerType || 'Standard'}
                          </p>
                        </div>
                        <div className="rounded-lg bg-[#111d30] p-2">
                          <p className="text-[10px] text-slate-500">
                            {isManaged ? 'Price / unit' : 'Power Rating'}
                          </p>
                          <p className="mt-0.5 font-semibold text-emerald-300">
                            {isManaged
                              ? `₹${st.pricePerUnit ?? '—'}`
                              : st.ratingKw
                                ? `${st.ratingKw} kW`
                                : 'Public EV'}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="mt-5 pt-3 border-t border-white/5">
                      {st.bookable && onBookStation ? (
                        <button
                          type="button"
                          onClick={() => handleBookStation(st)}
                          className="w-full rounded-xl bg-emerald-400 px-4 py-2.5 text-xs font-bold text-slate-950 transition hover:bg-emerald-300 shadow-md"
                        >
                          {isManaged ? 'Book Now →' : 'Request Booking →'}
                        </button>
                      ) : st.bookable ? (
                        <a
                          href="/login"
                          className="block w-full rounded-xl bg-emerald-400 px-4 py-2.5 text-center text-xs font-bold text-slate-950 transition hover:bg-emerald-300"
                        >
                          Sign in to {isManaged ? 'book' : 'request booking'} →
                        </a>
                      ) : !st.bookingEnabled ? (
                        <button
                          type="button"
                          disabled
                          className="w-full rounded-xl border border-white/10 bg-slate-900 px-4 py-2 text-xs font-medium text-slate-400 cursor-not-allowed"
                        >
                          Booking Disabled
                        </button>
                      ) : (
                        <p className="rounded-xl border border-white/10 bg-slate-900 px-4 py-2 text-center text-xs text-slate-400">
                          Station currently unavailable
                        </p>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          )}

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="mt-8 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-white/10 bg-[#0e1727] p-4 text-xs text-slate-300">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-lg border border-white/10 px-3 py-2 font-semibold hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                ← Previous Page
              </button>

              <div className="flex items-center gap-1">
                <span>Page</span>
                <span className="font-bold text-emerald-300">{page + 1}</span>
                <span>of</span>
                <span className="font-bold text-white">{totalPages}</span>
              </div>

              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                className="rounded-lg border border-white/10 px-3 py-2 font-semibold hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Next Page →
              </button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
