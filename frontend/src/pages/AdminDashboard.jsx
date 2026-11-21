import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { API_URL } from '../api'
import { AdminBookingActions } from './AdminBookingActions'
import { submitAdminBookingDecision } from './bookingAdminActions'

export default function AdminDashboard() {
  const navigate = useNavigate()
  const [stations, setStations] = useState([])
  const [loading, setLoading] = useState(true)
  const [analytics, setAnalytics] = useState(null)
  const [analyticsLoading, setAnalyticsLoading] = useState(true)
  const [analyticsError, setAnalyticsError] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [stationSearch, setStationSearch] = useState('')
  const [debouncedStationSearch, setDebouncedStationSearch] = useState('')
  const [stationSourceFilter, setStationSourceFilter] =
    useState('ALL')
  const [stationBookingFilter, setStationBookingFilter] = useState('ALL')
  const [stationCurrentPage, setStationCurrentPage] = useState(0)
  const [stationTotalPages, setStationTotalPages] = useState(0)
  const [stationTotalElements, setStationTotalElements] = useState(0)
  const [stationReloadVersion, setStationReloadVersion] = useState(0)
  const stationsPerPage = 20
  const [bookings, setBookings] = useState([])
  const [bookingsLoading, setBookingsLoading] = useState(true)
  const [bookingsError, setBookingsError] = useState('')
  const [bookingCurrentPage, setBookingCurrentPage] = useState(1)
  const bookingsPerPage = 5
  const [users, setUsers] = useState([])
  const [usersLoading, setUsersLoading] = useState(true)
  const [usersError, setUsersError] = useState('')
  const [userCurrentPage, setUserCurrentPage] = useState(1)
  const usersPerPage = 5
  const [userSearch, setUserSearch] = useState('')
  const [bookingSearch, setBookingSearch] = useState('')
  const [bookingStatusFilter, setBookingStatusFilter] = useState('REQUESTED')
  const [bookingActionPending, setBookingActionPending] = useState(false)
  const bookingActionLock = useRef(false)
  const [bookingActionError, setBookingActionError] = useState('')
  const [bookingActionSuccess, setBookingActionSuccess] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editingStationId, setEditingStationId] = useState(null)
  const [editingStationSource, setEditingStationSource] = useState(null)
  const [formData, setFormData] = useState({
    name: '',
    location: '',
    chargerType: '',
    pricePerUnit: '',
    available: true,
    latitude: '',
    longitude: '',
    operator: '',
    ownership: '',
    state: '',
    district: '',
    cityVillage: '',
    ratingKw: '',
    secondaryRatingKw: '',
    connectorCount: '',
  })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const user = JSON.parse(localStorage.getItem('user') || 'null')

    if (!localStorage.getItem('token') || user?.role !== 'ADMIN') {
      navigate('/', { replace: true })
      return
    }

    async function loadAnalytics() {
      setAnalyticsLoading(true)
      setAnalyticsError('')

      try {
        const token = localStorage.getItem('token')
        const response = await fetch(`${API_URL}/admin/analytics`, {
          headers: { Authorization: 'Bearer ' + token },
        })
        if (!response.ok) {
          throw new Error('Could not load dashboard analytics')
        }
        setAnalytics(await response.json())
      } catch (err) {
        setAnalyticsError(err.message || 'Could not load dashboard analytics')
      } finally {
        setAnalyticsLoading(false)
      }
    }

    async function loadBookings() {
      setBookingsLoading(true)
      setBookingsError('')

      try {
        const token = localStorage.getItem('token')

        const response = await fetch(
          `${API_URL}/bookings/admin/all`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        )

        if (!response.ok) {
          throw new Error('Could not load bookings')
        }

        const data = await response.json()
        setBookings(data)
      } catch (err) {
        setBookingsError(err.message || 'Something went wrong')
      } finally {
        setBookingsLoading(false)
      }
    }

    async function loadUsers() {
      setUsersLoading(true)
      setUsersError('')

      try {
        const token = localStorage.getItem('token')

        const response = await fetch(
          `${API_URL}/admin/users`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        )

        if (!response.ok) {
          throw new Error('Could not load users')
        }

        const data = await response.json()
        setUsers(data)
      } catch (err) {
        setUsersError(err.message || 'Something went wrong')
      } finally {
        setUsersLoading(false)
      }
    }

    loadAnalytics()
    loadBookings()
    loadUsers()
  }, [navigate])

  useEffect(() => {
    const timer = window.setTimeout(
      () => setDebouncedStationSearch(stationSearch.trim()),
      300
    )
    return () => window.clearTimeout(timer)
  }, [stationSearch])

  useEffect(() => {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    const token = localStorage.getItem('token')
    if (!token || user?.role !== 'ADMIN') return

    const controller = new AbortController()
    const params = new URLSearchParams({
      source: stationSourceFilter,
      page: String(stationCurrentPage),
      size: String(stationsPerPage),
    })
    if (debouncedStationSearch) params.set('q', debouncedStationSearch)
    if (stationBookingFilter !== 'ALL') {
      params.set('bookingEnabled', String(stationBookingFilter === 'ENABLED'))
    }

    async function loadStationData() {
      setLoading(true)
      setError('')
      try {
        const stationsResponse = await fetch(
          `${API_URL}/stations/discovery?${params}`,
          { signal: controller.signal }
        )
        if (!stationsResponse.ok) throw new Error('Could not load stations')

        const page = await stationsResponse.json()
        setStations(page.content || [])
        setStationTotalPages(page.totalPages || 0)
        setStationTotalElements(page.totalElements || 0)
        if (page.totalPages > 0 && stationCurrentPage >= page.totalPages) {
          setStationCurrentPage(page.totalPages - 1)
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || 'Could not load station data')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    loadStationData()
    return () => controller.abort()
  }, [
    debouncedStationSearch,
    stationCurrentPage,
    stationReloadVersion,
    stationSourceFilter,
    stationBookingFilter,
  ])

  function handleInputChange(event) {
    const { name, value, type, checked } = event.target

    setFormData((previous) => ({
      ...previous,
      [name]: type === 'checkbox' ? checked : value,
    }))
  }

  function handleEditStation(station) {
    setEditingStationId(station.internalId)
    setEditingStationSource(station.source)
    setError('')
    setSuccess('')

    setFormData({
      name: station.name || '',
      location: station.location || '',
      chargerType: station.chargerType || '',
      pricePerUnit: String(station.pricePerUnit ?? ''),
      available: station.available ?? true,
      latitude: station.latitude == null ? '' : String(station.latitude),
      longitude: station.longitude == null ? '' : String(station.longitude),
      operator: station.cpoName || '',
      ownership: station.ownership || '',
      state: station.state || '',
      district: station.district || '',
      cityVillage: station.cityVillage || '',
      ratingKw: station.ratingKw == null ? '' : String(station.ratingKw),
      secondaryRatingKw: station.secondaryRatingKw || '',
      connectorCount: station.connectorCount == null ? '' : String(station.connectorCount),
    })

    setShowForm(true)
  }

  async function handleAddStation(event) {
    event.preventDefault()
    setError('')

    const latitude = formData.latitude.trim()
    const longitude = formData.longitude.trim()
    const isPublicStation = editingStationSource === 'PUBLIC_DIRECTORY'
    const isEditing = editingStationId !== null

    let payload
    if (isPublicStation) {
      const ratingValue = formData.ratingKw.trim()
      const connectorCountValue = formData.connectorCount.trim()
      const ratingKw = ratingValue === '' ? null : Number(ratingValue)
      const connectorCount = connectorCountValue === '' ? null : Number(connectorCountValue)
      if (ratingValue && (!Number.isFinite(ratingKw) || ratingKw < 0)) {
        setError('Rating must be a valid non-negative number.')
        return
      }
      if (connectorCountValue && (!Number.isInteger(connectorCount) || connectorCount < 0)) {
        setError('Connector count must be a non-negative whole number.')
        return
      }
      payload = {
        cpoName: formData.operator.trim() || null,
        ownership: formData.ownership.trim() || null,
        state: formData.state.trim() || null,
        district: formData.district.trim() || null,
        cityVillage: formData.cityVillage.trim() || null,
        location: formData.location.trim() || null,
        latitude: latitude === '' ? null : Number(latitude),
        longitude: longitude === '' ? null : Number(longitude),
        chargerType: formData.chargerType.trim() || null,
        ratingKw,
        secondaryRatingKw: formData.secondaryRatingKw.trim() || null,
        connectorCount,
      }
    } else {
      const priceValue = formData.pricePerUnit.trim()
      const pricePerUnit = Number(priceValue)
      if (!formData.name.trim() || !formData.location.trim() || !formData.chargerType.trim()) {
        setError('Station name, location, and charger type are required.')
        return
      }
      if (!priceValue || !Number.isFinite(pricePerUnit) || pricePerUnit < 0) {
        setError('Price per unit must be a valid non-negative number.')
        return
      }
      payload = {
        ...formData,
        name: formData.name.trim(),
        location: formData.location.trim(),
        chargerType: formData.chargerType.trim(),
        pricePerUnit,
        latitude: latitude === '' ? null : Number(latitude),
        longitude: longitude === '' ? null : Number(longitude),
      }
    }

    if (Boolean(latitude) !== Boolean(longitude)) {
      setError('Enter both latitude and longitude, or leave both blank.')
      return
    }

    if (latitude && (!Number.isFinite(Number(latitude)) || Number(latitude) < -90 || Number(latitude) > 90)) {
      setError('Latitude must be between -90 and 90.')
      return
    }

    if (longitude && (!Number.isFinite(Number(longitude)) || Number(longitude) < -180 || Number(longitude) > 180)) {
      setError('Longitude must be between -180 and 180.')
      return
    }

    setSuccess('')
    setSaving(true)

    try {
      const token = localStorage.getItem('token')

      const stationEndpoint = isPublicStation
        ? `${API_URL}/public-stations/${editingStationId}`
        : `${API_URL}/stations${editingStationId ? `/${editingStationId}` : ''}`
      const response = await fetch(
        stationEndpoint,
        {
          method: isEditing ? 'PUT' : 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify(payload),
        }
      )

      const data = await response.json().catch(() => null)

      if (!response.ok) {
        throw new Error(data?.detail || data?.message || `Could not ${isEditing ? 'update' : 'create'} station.`)
      }

      setStationReloadVersion((version) => version + 1)
      setSuccess(isEditing ? 'Station updated successfully.' : 'Station created successfully.')
      setEditingStationId(null)
      setEditingStationSource(null)
      setStationSourceFilter(isPublicStation ? 'PUBLIC' : 'MANAGED')
      setStationSearch('')
      setDebouncedStationSearch('')
      setStationCurrentPage(0)
      setFormData({
        name: '',
        location: '',
        chargerType: '',
        pricePerUnit: '',
        available: true,
        latitude: '',
        longitude: '',
        operator: '',
        ownership: '',
        state: '',
        district: '',
        cityVillage: '',
        ratingKw: '',
        secondaryRatingKw: '',
        connectorCount: '',
      })
      setShowForm(false)
    } catch (err) {
      setError(err.message || (isPublicStation ? 'Could not save public station.' : 'Could not save managed station.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteStation(station) {
    const isPublicStation = station.source === 'PUBLIC_DIRECTORY'
    if (!isPublicStation && station.source !== 'MANAGED') {
      setError('Unknown station source; no station was deleted.')
      return
    }

    const confirmed = window.confirm(
      `Delete "${station.name || 'unnamed station'}"? This removes only the selected station record.`
    )

    if (!confirmed) return

    setError('')
    setSuccess('')

    try {
      const token = localStorage.getItem('token')

      const endpoint = isPublicStation ? 'public-stations' : 'stations'
      const response = await fetch(`${API_URL}/${endpoint}/${station.internalId}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      if (!response.ok) {
        const data = await response.json().catch(() => null)
        throw new Error(data?.detail || data?.message || 'Could not delete station.')
      }

      setStationReloadVersion((version) => version + 1)
      setSuccess('Station deleted successfully.')
    } catch (err) {
      setError(err.message || 'Could not delete station.')
    }
  }

  async function handleToggleBookingEnabled(station) {
    setError('')
    setSuccess('')
    const token = localStorage.getItem('token')
    try {
      const response = await fetch(
        `${API_URL}/admin/stations/${station.source}/${station.internalId}/booking-enabled`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + token,
          },
          body: JSON.stringify({ bookingEnabled: !station.bookingEnabled }),
        }
      )
      const data = await response.json().catch(() => null)
      if (!response.ok) {
        throw new Error(data?.detail || data?.message || 'Could not change booking status.')
      }
      setSuccess(`Booking ${data.bookingEnabled ? 'enabled' : 'disabled'} for this station.`)
      setStationReloadVersion((version) => version + 1)
    } catch (err) {
      setError(err.message || 'Could not change booking status.')
    }
  }

  async function handleBookingStatusChange(booking, newStatus) {
    if (bookingActionLock.current) return
    const action = newStatus === 'CONFIRMED' ? 'approve' : 'cancel'
    const confirmation = action === 'approve'
      ? 'Approve this booking request? Check station availability manually before confirming; availability is not verified automatically.'
      : 'Cancel this booking request?'
    if (!window.confirm(confirmation)) return

    bookingActionLock.current = true
    setBookingActionPending(true)
    setBookingActionError('')
    setBookingActionSuccess('')
    try {
      await submitAdminBookingDecision({
        bookingId: booking.id,
        status: newStatus,
        token: localStorage.getItem('token'),
        refreshBookings: async () => {
          const response = await fetch(`${API_URL}/bookings/admin/all`, {
            headers: {
              Authorization: `Bearer ${localStorage.getItem('token')}`,
            },
          })
          if (!response.ok) {
            throw new Error('Booking was updated, but the list could not be refreshed.')
          }
          setBookings(await response.json())
        },
      })
      setBookingActionSuccess(`Booking #${booking.id} ${action === 'approve' ? 'approved' : 'cancelled'}.`)
    } catch (error) {
      setBookingActionError(error.message || 'Failed to update booking status.')
    } finally {
      bookingActionLock.current = false
      setBookingActionPending(false)
    }
  }

  const filteredBookings = bookings.filter((booking) => {
    const search = bookingSearch.toLowerCase().trim()

    const matchesSearch =
      String(booking.id).includes(search) ||
      (booking.customerName || '').toLowerCase().includes(search) ||
      (booking.station?.name || booking.publicStation?.cpoName || '').toLowerCase().includes(search)

    const matchesStatus =
      bookingStatusFilter === 'ALL' ||
      booking.status === bookingStatusFilter

    return matchesSearch && matchesStatus
  })

  const totalBookingPages = Math.ceil(
    filteredBookings.length / bookingsPerPage
  )

  const startBookingIndex =
    (bookingCurrentPage - 1) * bookingsPerPage

  const paginatedBookings = filteredBookings.slice(
    startBookingIndex,
    startBookingIndex + bookingsPerPage
  )

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    window.dispatchEvent(new Event('auth-change'))
    navigate('/login', { replace: true })
  }

  const filteredUsers = users.filter((user) => {
    const search = userSearch.toLowerCase().trim()

    return (
      String(user.id).includes(search) ||
      (user.name || '').toLowerCase().includes(search) ||
      (user.email || '').toLowerCase().includes(search)
    )
  })

  const totalUserPages = Math.ceil(
    filteredUsers.length / usersPerPage
  )

  const startUserIndex =
    (userCurrentPage - 1) * usersPerPage

  const paginatedUsers = filteredUsers.slice(
    startUserIndex,
    startUserIndex + usersPerPage
  )

  return (
    <main className="min-h-screen bg-slate-950 p-6 text-white">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-emerald-400">
            EV Admin Dashboard
          </h1>
          <p className="mt-2 text-slate-400">
            Manage your charging stations
          </p>
        </div>

        <button
          onClick={logout}
          className="rounded-lg border border-slate-600 px-4 py-2 hover:bg-slate-800"
        >
          Logout
        </button>
      </header>

      <section className="mx-auto mt-8 grid max-w-6xl gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <p className="text-sm text-slate-400">Total Stations</p>
          <h2 className="mt-2 text-3xl font-bold text-emerald-400">
            {analyticsLoading ? '...' : analyticsError ? '—' : analytics?.totalStations ?? '—'}
          </h2>
        </div>

        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <p className="text-sm text-slate-400">Public Stations</p>
          <h2 className="mt-2 text-3xl font-bold text-emerald-400">
            {analyticsLoading ? '...' : analyticsError ? '—' : analytics?.publicStations ?? '—'}
          </h2>
        </div>

        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <p className="text-sm text-slate-400">Managed Stations</p>
          <h2 className="mt-2 text-3xl font-bold text-emerald-400">
            {analyticsLoading ? '...' : analyticsError ? '—' : analytics?.managedStations ?? '—'}
          </h2>
        </div>

        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <p className="text-sm text-slate-400">Total Users</p>
          <h2 className="mt-2 text-3xl font-bold text-blue-400">
            {analyticsLoading ? '...' : analyticsError ? '—' : analytics?.totalUsers ?? '—'}
          </h2>
        </div>

        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <p className="text-sm text-slate-400">Total Bookings</p>
          <h2 className="mt-2 text-3xl font-bold text-violet-400">
            {analyticsLoading ? '...' : analyticsError ? '—' : analytics?.totalBookings ?? '—'}
          </h2>
        </div>
      </section>

      <section className="mx-auto mt-8 max-w-6xl">
        <div className="mb-5">
          <h2 className="text-2xl font-semibold">Analytics &amp; Reports</h2>
          <p className="mt-1 text-sm text-slate-400">
            Database-backed totals and trends. Revenue and active-user status are shown only when supported by recorded data.
          </p>
        </div>

        {analyticsError && (
          <p role="alert" className="mb-4 rounded-lg border border-red-800 bg-red-950/40 p-3 text-red-300">
            {analyticsError}
          </p>
        )}
        {analyticsLoading ? (
          <div role="status" className="rounded-xl border border-slate-700 bg-slate-900 p-8 text-center text-slate-400">
            Loading analytics...
          </div>
        ) : !analyticsError && analytics ? (
          <div className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">User Analytics</h3>
                <dl className="mt-4 grid grid-cols-3 gap-3 text-center">
                  <div>
                    <dt className="text-xs text-slate-400">Normal users</dt>
                    <dd className="mt-1 text-xl font-bold text-violet-300">{analytics.normalUsers}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-400">Admins</dt>
                    <dd className="mt-1 text-xl font-bold text-blue-300">{analytics.adminCount}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-400">Active users</dt>
                    <dd className="mt-1 text-sm font-semibold text-slate-300">
                      {analytics.activeUsersAvailable ? analytics.activeUsers : 'Unavailable'}
                    </dd>
                  </div>
                </dl>
                {!analytics.activeUsersAvailable && (
                  <p className="mt-4 text-xs text-slate-500">
                    User records do not include an active-status field.
                  </p>
                )}
              </article>

              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">Booking Analytics</h3>
                <dl className="mt-4 grid grid-cols-3 gap-3 text-center">
                  <div>
                    <dt className="text-xs text-slate-400">All</dt>
                    <dd className="mt-1 text-xl font-bold text-blue-300">{analytics.totalBookings}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-400">Requests</dt>
                    <dd className="mt-1 text-xl font-bold text-amber-300">{analytics.requestedBookings}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-400">Confirmed</dt>
                    <dd className="mt-1 text-xl font-bold text-emerald-300">{analytics.confirmedBookings}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-400">Cancelled</dt>
                    <dd className="mt-1 text-xl font-bold text-orange-300">{analytics.cancelledBookings}</dd>
                  </div>
                </dl>
              </article>

              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">Revenue Analytics</h3>
                <p className="mt-4 text-lg font-semibold text-amber-300">
                  {analytics.revenueAvailable
                    ? analytics.revenue
                    : 'Revenue data unavailable'}
                </p>
                {!analytics.revenueAvailable && (
                  <p className="mt-2 text-xs text-slate-500">
                    {analytics.revenueUnavailableReason}
                  </p>
                )}
              </article>
            </div>

            <div className="grid gap-5 lg:grid-cols-2">
              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">Monthly Booking Trends</h3>
                {analytics.monthlyReports.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-400">No monthly booking data available.</p>
                ) : (
                  <div className="mt-4 space-y-3">
                    {analytics.monthlyReports.map((report) => {
                      const maximum = Math.max(
                        1,
                        ...analytics.monthlyReports.map((entry) => entry.totalBookings)
                      )
                      const width = `${(report.totalBookings / maximum) * 100}%`
                      return (
                        <div key={report.month} className="grid grid-cols-[5rem_1fr_2.5rem] items-center gap-3 text-sm">
                          <span className="text-slate-400">{report.month}</span>
                          <div className="h-2 overflow-hidden rounded-full bg-slate-800">
                            <div className="h-full rounded-full bg-emerald-400" style={{ width }} />
                          </div>
                          <span className="text-right text-slate-300">{report.totalBookings}</span>
                        </div>
                      )
                    })}
                  </div>
                )}
              </article>

              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">Top Public Cities</h3>
                {analytics.topPublicCities.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-400">No city data available.</p>
                ) : (
                  <ol className="mt-4 space-y-3">
                    {analytics.topPublicCities.map((place) => (
                      <li key={place.place} className="flex items-center justify-between gap-4 text-sm">
                        <span className="truncate text-slate-300">{place.place}</span>
                        <span className="shrink-0 rounded-full bg-emerald-500/10 px-3 py-1 text-emerald-300">
                          {place.stationCount.toLocaleString()} stations
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </article>
            </div>

            <div className="grid gap-5 lg:grid-cols-2">
              <article className="rounded-xl border border-slate-700 bg-slate-900 p-5">
                <h3 className="font-semibold">Managed Station Locations</h3>
                <p className="mt-1 text-xs text-slate-500">
                  Managed stations store an unstructured location, not a city field.
                </p>
                {analytics.topManagedLocations.length === 0 ? (
                  <p className="py-6 text-center text-sm text-slate-400">No managed station locations available.</p>
                ) : (
                  <ol className="mt-4 space-y-3">
                    {analytics.topManagedLocations.map((place) => (
                      <li key={place.place} className="flex items-center justify-between gap-4 text-sm">
                        <span className="truncate text-slate-300">{place.place}</span>
                        <span className="shrink-0 rounded-full bg-blue-500/10 px-3 py-1 text-blue-300">
                          {place.stationCount.toLocaleString()} stations
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </article>

              <article className="overflow-hidden rounded-xl border border-slate-700 bg-slate-900">
                <div className="p-5">
                  <h3 className="font-semibold">Monthly Reports</h3>
                </div>
                {analytics.monthlyReports.length === 0 ? (
                  <p className="px-5 pb-5 text-sm text-slate-400">No monthly report data available.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead className="border-y border-slate-700 text-xs text-slate-400">
                        <tr>
                          <th className="px-4 py-3">Month</th>
                          <th className="px-4 py-3">Bookings</th>
                          <th className="px-4 py-3">Requests</th>
                          <th className="px-4 py-3">Confirmed</th>
                          <th className="px-4 py-3">Cancelled</th>
                          <th className="px-4 py-3">Revenue</th>
                        </tr>
                      </thead>
                      <tbody>
                        {analytics.monthlyReports.map((report) => (
                          <tr key={report.month} className="border-b border-slate-800">
                            <td className="px-4 py-3 text-slate-300">{report.month}</td>
                            <td className="px-4 py-3">{report.totalBookings}</td>
                            <td className="px-4 py-3 text-amber-300">{report.requestedBookings}</td>
                            <td className="px-4 py-3 text-emerald-300">{report.confirmedBookings}</td>
                            <td className="px-4 py-3 text-orange-300">{report.cancelledBookings}</td>
                            <td className="px-4 py-3 text-slate-400">
                              {report.revenueAvailable ? report.revenue : 'Unavailable'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </article>
            </div>
          </div>
        ) : null}
      </section>

      <section className="mx-auto mt-8 max-w-6xl">
        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Charging Station Management</h2>
              <p className="mt-1 text-sm text-slate-400">
                Public records remain separate from application-managed stations. All changes target one source-specific database record.
              </p>
            </div>

            <button
              onClick={() => {
                setError('')
                setSuccess('')
                setEditingStationId(null)
                setEditingStationSource(null)
                setFormData({
                  name: '',
                  location: '',
                  chargerType: '',
                  pricePerUnit: '',
                  available: true,
                  latitude: '',
                  longitude: '',
                  operator: '',
                  ownership: '',
                  state: '',
                  district: '',
                  cityVillage: '',
                  ratingKw: '',
                  secondaryRatingKw: '',
                  connectorCount: '',
                })
                setShowForm((previous) => !previous)
              }}
              className="shrink-0 rounded-lg bg-emerald-500 px-4 py-2 font-semibold text-slate-950 hover:bg-emerald-400"
            >
              {showForm ? 'Close Form' : '+ Add New Station'}
            </button>
          </div>

          {success && (
            <p role="status" className="mb-4 rounded-lg border border-emerald-700 bg-emerald-950/40 p-3 text-emerald-300">
              {success}
            </p>
          )}
          {error && (
            <p role="alert" className="mb-4 rounded-lg border border-red-800 bg-red-950/40 p-3 text-red-300">
              {error}
            </p>
          )}

          {showForm && (
            <form
              onSubmit={handleAddStation}
              className="mb-6 grid gap-4 rounded-xl border border-slate-700 bg-slate-950 p-5 sm:grid-cols-2"
            >
              {editingStationSource === 'PUBLIC_DIRECTORY' && (
                <p className="text-sm text-amber-300 sm:col-span-2">
                  Editing the selected station. Import identity and batch metadata remain protected.
                </p>
              )}
              {(editingStationSource === 'PUBLIC_DIRECTORY'
                ? [
                    { name: 'operator', label: 'Operator / CPO Name', maxLength: 255 },
                    { name: 'ownership', label: 'Ownership', maxLength: 255 },
                    { name: 'state', label: 'State', maxLength: 255 },
                    { name: 'district', label: 'District', maxLength: 255 },
                    { name: 'cityVillage', label: 'City / Village', maxLength: 255 },
                    { name: 'location', label: 'Location / Address', maxLength: 4000, wide: true },
                    { name: 'latitude', label: 'Latitude', type: 'number', min: -90, max: 90 },
                    { name: 'longitude', label: 'Longitude', type: 'number', min: -180, max: 180 },
                    { name: 'chargerType', label: 'Charger Type', maxLength: 255 },
                    { name: 'ratingKw', label: 'Rating (kW)', type: 'number', min: 0 },
                    { name: 'secondaryRatingKw', label: 'Secondary Rating', maxLength: 255 },
                    { name: 'connectorCount', label: 'Connector Count', type: 'number', min: 0, step: 1 },
                  ]
                : [
                    { name: 'name', label: 'Station Name', required: true, maxLength: 255 },
                    { name: 'location', label: 'Location', required: true, maxLength: 255 },
                    { name: 'chargerType', label: 'Charger Type', required: true, maxLength: 255 },
                    { name: 'pricePerUnit', label: 'Price Per Unit', type: 'number', required: true, min: 0 },
                    { name: 'latitude', label: 'Latitude (optional)', type: 'number', min: -90, max: 90 },
                    { name: 'longitude', label: 'Longitude (optional)', type: 'number', min: -180, max: 180 },
                  ]).map((field) => (
                <label key={field.name} className={`block text-sm text-slate-300 ${field.wide ? 'sm:col-span-2' : ''}`}>
                  {field.label}
                  <input
                    name={field.name}
                    type={field.type || 'text'}
                    required={field.required || false}
                    step={field.step || (field.type === 'number' ? 'any' : undefined)}
                    min={field.min}
                    max={field.max}
                    maxLength={field.maxLength}
                    aria-label={field.label}
                    value={formData[field.name]}
                    onChange={handleInputChange}
                    className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white outline-none focus:border-emerald-400"
                  />
                </label>
              ))}

              {editingStationSource === 'PUBLIC_DIRECTORY' ? (
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Enter both coordinates when supplied. Public availability and pricing are not verified and cannot be edited here.
                </p>
              ) : (
                <>
                  <p className="text-xs text-slate-500 sm:col-span-2">
                    Coordinates are optional; enter both together when known.
                  </p>
                  <label className="flex items-center gap-3 text-sm text-slate-300 sm:col-span-2">
                    <input
                      type="checkbox"
                      name="available"
                      checked={formData.available}
                      onChange={handleInputChange}
                      className="h-4 w-4 accent-emerald-500"
                    />
                    Station is available
                  </label>
                </>
              )}

              <button
                type="submit"
                disabled={saving}
                className="rounded-lg bg-emerald-500 px-4 py-3 font-semibold text-slate-950 disabled:opacity-60 sm:col-span-2"
              >
                {saving
                  ? 'Saving...'
                  : editingStationId
                    ? 'Update Station'
                    : 'Save Managed Station'}
              </button>
            </form>
          )}

          <p className="mt-2 text-slate-400">
            {loading
              ? 'Loading stations...'
              : `Showing ${stations.length} of ${stationTotalElements} matching stations`}
          </p>

          <div className="mb-5 flex flex-col gap-3 sm:flex-row">
            <input
              type="text"
              placeholder="Search station name, city or state..."
              value={stationSearch}
              onChange={(e) => {
                setStationSearch(e.target.value)
                setStationCurrentPage(0)
              }}
              className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 text-white placeholder-slate-400 outline-none focus:border-emerald-500"
            />

            <select
              value={stationSourceFilter}
              onChange={(e) => {
                setStationSourceFilter(e.target.value)
                setStationCurrentPage(0)
              }}
              className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 text-white outline-none focus:border-emerald-500"
            >
              <option value="ALL">All Stations</option>
              <option value="PUBLIC">Public Stations</option>
              <option value="MANAGED">Managed Stations</option>
            </select>

            <select
              aria-label="Filter by booking status"
              value={stationBookingFilter}
              onChange={(event) => {
                setStationBookingFilter(event.target.value)
                setStationCurrentPage(0)
              }}
              className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 text-white outline-none focus:border-emerald-500"
            >
              <option value="ALL">All Booking Statuses</option>
              <option value="ENABLED">Booking Enabled</option>
              <option value="DISABLED">Booking Disabled</option>
            </select>
          </div>

          {error ? (
            <p className="py-8 text-center text-red-400">Station data could not be displayed.</p>
          ) : loading ? (
            <p className="py-8 text-center text-slate-400">Loading stations...</p>
          ) : (
            <div className="mt-5 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-700 text-slate-400">
                  <tr>
                    <th className="p-3">Name</th>
                    <th className="p-3">City / Location</th>
                    <th className="p-3">State</th>
                    <th className="p-3">Charger</th>
                    <th className="p-3">Price / Unit</th>
                    <th className="p-3">Availability</th>
                    <th className="p-3">Booking Status</th>
                    <th className="p-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {stations.length === 0 && (
                    <tr>
                      <td colSpan={8} className="p-6 text-center text-slate-400">
                        {stationTotalElements === 0
                          ? 'No stations match the selected category and search.'
                          : 'No stations are available on this page.'}
                      </td>
                    </tr>
                  )}
                  {stations.map((station) => (
                    <tr key={station.id} className="border-b border-slate-800">
                      <td className="p-3">
                        <p>{station.name || 'Unnamed station'}</p>
                        {station.operator && (
                          <p className="mt-1 text-xs text-slate-500">{station.operator}</p>
                        )}
                        {station.latitude != null && station.longitude != null && (
                          <p className="mt-1 text-xs text-slate-500">
                            {station.latitude}, {station.longitude}
                          </p>
                        )}
                      </td>
                      <td className="p-3">
                        {station.cityVillage || station.location || '—'}
                        {station.cityVillage && station.location && (
                          <p className="mt-1 text-xs text-slate-500">{station.location}</p>
                        )}
                        {station.district && (
                          <p className="mt-1 text-xs text-slate-500">{station.district}</p>
                        )}
                      </td>
                      <td className="p-3">{station.state || '—'}</td>
                      <td className="p-3">{station.chargerType}</td>
                      <td className="p-3">
                        {station.pricePerUnit == null ? '—' : station.pricePerUnit}
                      </td>
                      <td className="p-3">
                        {station.available == null
                          ? 'Unknown'
                          : station.available ? 'Available' : 'Unavailable'}
                      </td>
                      <td className="p-3">
                        <button
                          type="button"
                          onClick={() => handleToggleBookingEnabled(station)}
                          className={`rounded-lg px-3 py-2 text-xs font-semibold ${
                            station.bookingEnabled
                              ? 'bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20'
                              : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
                          }`}
                        >
                          {station.bookingEnabled ? 'Enabled · Disable' : 'Disabled · Enable'}
                        </button>
                      </td>
                      <td className="p-3">
                        <button
                          onClick={() => handleEditStation(station)}
                          className="mr-2 rounded-lg bg-blue-500/10 px-3 py-2 text-blue-400 hover:bg-blue-500/20"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => handleDeleteStation(station)}
                          className="rounded-lg bg-red-500/10 px-3 py-2 text-red-400 hover:bg-red-500/20"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            )}
            <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-400">
                Page {stationCurrentPage + 1} of {Math.max(stationTotalPages, 1)}
                {" "}·{" "}
                {stationTotalElements} stations
              </p>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() =>
                    setStationCurrentPage((page) => Math.max(page - 1, 0))
                  }
                  disabled={stationCurrentPage === 0 || loading}
                  className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Previous
                </button>

                <button
                  type="button"
                  onClick={() =>
                    setStationCurrentPage((page) =>
                      Math.min(page + 1, Math.max(stationTotalPages - 1, 0))
                    )
                  }
                  disabled={stationCurrentPage >= stationTotalPages - 1 || loading}
                  className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            </div>
        </div>
      </section>
      <section className="mx-auto mt-8 max-w-6xl">
        <div className="rounded-xl border border-slate-700 bg-slate-900 p-5">
          <div className="mb-5">
            <h2 className="text-xl font-semibold">
              Booking Management
            </h2>
            <p className="mt-2 text-slate-400">
              Total bookings: {bookingsLoading ? 'Loading...' : bookings.length}
            </p>
            <p className="mt-3 rounded-lg border border-amber-400/20 bg-amber-400/5 p-3 text-sm text-amber-200">
              Check station availability manually before confirming. Availability is not verified automatically.
            </p>
            {bookingActionSuccess && (
              <p role="status" className="mt-3 text-sm text-emerald-300">{bookingActionSuccess}</p>
            )}
            {bookingActionError && (
              <p role="alert" className="mt-3 text-sm text-red-300">{bookingActionError}</p>
            )}
          </div>

          {bookingsError && (
            <p className="mb-4 text-red-400">{bookingsError}</p>
          )}

          {bookingsLoading ? (
            <p className="text-slate-400">Loading bookings...</p>
          ) : !bookingsError ? (
            <>
              <div className="mb-4 grid gap-3 sm:grid-cols-2">
                <input
                  type="text"
                  placeholder="Search by booking ID, customer or station..."
                  value={bookingSearch}
                  onChange={(event) => {
                    setBookingSearch(event.target.value)
                    setBookingCurrentPage(1)
                  }}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-4 py-3 text-white outline-none focus:border-emerald-400"
                />

                <select
                  value={bookingStatusFilter}
                  onChange={(event) => {
                    setBookingStatusFilter(event.target.value)
                    setBookingCurrentPage(1)
                  }}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-4 py-3 text-white outline-none focus:border-emerald-400"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="REQUESTED">Requested</option>
                  <option value="CONFIRMED">Confirmed</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-700 text-slate-400">
                  <tr>
                    <th className="p-3">Booking ID</th>
                    <th className="p-3">Customer</th>
                    <th className="p-3">Station</th>
                    <th className="p-3">Location</th>
                    <th className="p-3">Date</th>
                    <th className="p-3">Start Time</th>
                    <th className="p-3">End Time</th>
                    <th className="p-3">Status / Action</th>
                  </tr>
                </thead>

                <tbody>
                  {filteredBookings.length === 0 ? (
                    <tr>
                      <td
                        colSpan={8}
                        className="p-6 text-center text-slate-400"
                      >
                        No bookings match your search or selected status.
                      </td>
                    </tr>
                  ) : (
                    paginatedBookings.map((booking) => (
                      <tr
                        key={booking.id}
                        className="border-b border-slate-800"
                      >
                        <td className="p-3">{booking.id}</td>
                        <td className="p-3">
                          {booking.customerName || 'N/A'}
                        </td>
                        <td className="p-3">
                          {booking.station?.name ||
                            [booking.publicStation?.cpoName, booking.publicStation?.cityVillage].filter(Boolean).join(' - ') ||
                            'N/A'}
                        </td>
                        <td className="p-3">
                          {booking.station?.location || booking.publicStation?.location || 'N/A'}
                        </td>
                        <td className="p-3">
                          {booking.bookingDate || 'N/A'}
                        </td>
                        <td className="p-3">
                          {booking.startTime || 'N/A'}
                        </td>
                        <td className="p-3">
                          {booking.endTime || 'N/A'}
                        </td>
                        <td className="p-3">
                          <AdminBookingActions
                            booking={booking}
                            disabled={bookingActionPending}
                            onApprove={() => handleBookingStatusChange(booking, 'CONFIRMED')}
                            onCancel={() => handleBookingStatusChange(booking, 'CANCELLED')}
                          />
                          {booking.status === 'REQUESTED' && booking.publicStation != null && (
                            <p className="mt-1 text-xs text-slate-500">Public station request; availability is not verified automatically.</p>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-400">
                Page {bookingCurrentPage} of {Math.max(totalBookingPages, 1)}
                {" "}·{" "}
                {filteredBookings.length} bookings
              </p>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() =>
                    setBookingCurrentPage((page) => Math.max(page - 1, 1))
                  }
                  disabled={bookingCurrentPage === 1}
                  className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Previous
                </button>

                <button
                  type="button"
                  onClick={() =>
                    setBookingCurrentPage((page) =>
                      Math.min(page + 1, Math.max(totalBookingPages, 1))
                    )
                  }
                  disabled={bookingCurrentPage >= totalBookingPages}
                  className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            </div>
            </>
          ) : null}
        </div>
      </section>
      {/* User Management */}
      <div className="mt-8 rounded-xl border border-slate-700 bg-slate-900 p-6 text-white shadow-lg">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold text-white">
              User Management
            </h2>
            <p className="mt-2 text-sm text-slate-300">
              Total Users: {usersLoading ? 'Loading...' : usersError ? 'Unavailable' : users.length}
            </p>
          </div>
        </div>

        {usersLoading ? (
          <p className="py-6 text-slate-300">Loading users...</p>
        ) : usersError ? (
          <p className="rounded-lg border border-red-800 bg-red-950 p-4 text-red-300">
            {usersError}
          </p>
        ) : users.length === 0 ? (
          <p className="py-6 text-slate-400">No users found.</p>
        ) : (
          <div>
            <div className="mb-5 flex flex-col gap-3 sm:flex-row">
              <input
                type="text"
                placeholder="Search by ID, name or email..."
                value={userSearch}
                onChange={(e) => {
                  setUserSearch(e.target.value)
                  setUserCurrentPage(1)
                }}
                className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 text-white placeholder-slate-400 outline-none focus:border-emerald-500"
              />

            </div>

            {filteredUsers.length === 0 ? (
              <p className="py-6 text-slate-400">
                No users match your search.
              </p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-700">
                <table className="w-full min-w-[600px] border-collapse text-left text-sm text-slate-200">
                  <thead className="bg-slate-800">
                    <tr>
                      <th className="border-b border-slate-700 px-4 py-3 font-semibold text-white">
                        ID
                      </th>
                      <th className="border-b border-slate-700 px-4 py-3 font-semibold text-white">
                        Name
                      </th>
                      <th className="border-b border-slate-700 px-4 py-3 font-semibold text-white">
                        Email
                      </th>
                      <th className="border-b border-slate-700 px-4 py-3 font-semibold text-white">
                        Role
                      </th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-800">
                    {paginatedUsers.map((user) => (
                      <tr
                        key={user.id}
                        className="bg-slate-900 transition-colors hover:bg-slate-800"
                      >
                        <td className="px-4 py-4 text-slate-200">
                          {user.id}
                        </td>
                        <td className="px-4 py-4 font-medium text-white">
                          {user.name}
                        </td>
                        <td className="break-all px-4 py-4 text-slate-300">
                          {user.email}
                        </td>
                        <td className="px-4 py-4">
                          <span
                            className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                              user.role === "ADMIN"
                                ? "bg-purple-500/20 text-purple-300"
                                : "bg-emerald-500/20 text-emerald-300"
                            }`}
                          >
                            {user.role}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-slate-400">
            Page {userCurrentPage} of {Math.max(totalUserPages, 1)}
            {" "}·{" "}
            {filteredUsers.length} users
          </p>

          <div className="flex gap-2">
            <button
              type="button"
              onClick={() =>
                setUserCurrentPage((page) => Math.max(page - 1, 1))
              }
              disabled={userCurrentPage === 1}
              className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Previous
            </button>

            <button
              type="button"
              onClick={() =>
                setUserCurrentPage((page) =>
                  Math.min(page + 1, Math.max(totalUserPages, 1))
                )
              }
              disabled={userCurrentPage >= totalUserPages}
              className="rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </main>
  )
}
