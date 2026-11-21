
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { connectNotificationSocket, refreshBookingsForNotification } from './notificationSocket'
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom'
const MapView = lazy(() => import('./MapView'))
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const UnifiedDiscovery = lazy(() => import('./UnifiedDiscovery'))
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'))
import { API_URL } from './api'
import { bookingErrorMessage, runBookingSubmission } from './bookingSubmission'

function PublicStationsPage() {
  return (
    <main className="min-h-screen bg-slate-950 px-3 py-5 text-white sm:px-6">
      <div className="mx-auto max-w-7xl">
        <nav className="mb-5 flex items-center justify-between rounded-2xl border border-white/10 bg-[#0e1727] px-5 py-4">
          <Link to="/public-stations" className="font-bold text-emerald-300">
            ChargeFinder
          </Link>
          <Link to="/login" className="text-sm font-semibold text-slate-300 hover:text-white">
            Sign in
          </Link>
        </nav>
        <Suspense fallback={<p className="py-10 text-center text-slate-400">Loading station discovery...</p>}>
          <UnifiedDiscovery />
        </Suspense>
      </div>
    </main>
  )
}

function Dashboard() {
  const [loggedInUser, setLoggedInUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('user') || 'null')
    } catch {
      return null
    }
  })
  const [stations, setStations] = useState([])
  const [mapStations, setMapStations] = useState([])
  const [mapLoading, setMapLoading] = useState(true)
  const [mapError, setMapError] = useState('')
  const [bookings, setBookings] = useState([])
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [showNotifications, setShowNotifications] = useState(false)
  const [bookingSearch, setBookingSearch] = useState('')
  const [bookingStatusFilter, setBookingStatusFilter] = useState('all')
  const [activePage, setActivePage] = useState('dashboard')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [bookingsLoading, setBookingsLoading] = useState(false)
  const [bookingsError, setBookingsError] = useState('')
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [profileNotice, setProfileNotice] = useState('')
  const [profileName, setProfileName] = useState('')

  const [selectedStation, setSelectedStation] = useState(null)
  const [bookingDate, setBookingDate] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const bookingSubmissionLock = useRef(false)
  const [bookingError, setBookingError] = useState('')
  const [notice, setNotice] = useState('')
  const [cancellingId, setCancellingId] = useState(null)

  async function loadStations() {
    try {
      setError('')
      const response = await fetch(`${API_URL}/stations`)
      if (!response.ok) throw new Error('Failed to load stations.')

      const data = await response.json()
      setStations(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(err.message || 'Could not connect to the backend.')
    } finally {
      setLoading(false)
    }
  }

  async function loadMapStations() {
    setMapLoading(true)
    setMapError('')
    try {
      const response = await fetch(`${API_URL}/stations/discovery/map?source=ALL&limit=100000`)
      if (!response.ok) throw new Error('Failed to load station map.')
      const data = await response.json()
      if (!Array.isArray(data)) {
        throw new Error('Map station response was not a station list.')
      }
      setMapStations(data)
    } catch (err) {
      setMapStations([])
      setMapError(err.message || 'Could not load station map.')
    } finally {
      setMapLoading(false)
    }
  }

  const loadBookings = useCallback(async () => {
    setBookingsLoading(true)
    setBookingsError('')

    try {
      const token = localStorage.getItem('token')

      const response = await fetch(`${API_URL}/bookings`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
        return
      }

      if (!response.ok) {
        throw new Error('Failed to load bookings.')
      }

      const data = await response.json()
      setBookings(Array.isArray(data) ? data : [])
    } catch (err) {
      setBookingsError(err.message || 'Could not load bookings.')
    } finally {
      setBookingsLoading(false)
    }
  }, [])

  useEffect(() => {
    const token = localStorage.getItem('token')
    const userData = JSON.parse(localStorage.getItem('user') || 'null')

    const userId = userData?.id ?? userData?.userId

    if (!token || !userId) {
      console.warn('WebSocket: token or user ID is missing.')
      return
    }

    const disconnect = connectNotificationSocket({
      userId,
      token,
      onNotification: (notification) => {
        setNotifications((previous) => {
          const alreadyExists = previous.some(
            (item) => item.id === notification.id
          )

          if (alreadyExists) {
            return previous
          }

          if (!notification.read) {
            setUnreadCount((count) => count + 1)
          }

          return [notification, ...previous]
        })
        refreshBookingsForNotification(notification, loadBookings)
      },
    })

    return () => disconnect()
  }, [loadBookings])

  async function loadProfile() {
    const token = localStorage.getItem('token')

    const response = await fetch(`${API_URL}/profile`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })

    if (response.status === 401 || response.status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
      return null
    }

    if (!response.ok) {
      throw new Error('Failed to load profile.')
    }

    return response.json()
  }

  async function saveProfile(event) {
    event.preventDefault()
    setProfileSaving(true)
    setProfileError('')
    setProfileNotice('')

    try {
      const token = localStorage.getItem('token')

      const response = await fetch(`${API_URL}/profile`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: profileName.trim(),
        }),
      })

      if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
        return
      }

      const data = await response.json().catch(() => ({}))

      if (!response.ok) {
        throw new Error(data.message || 'Failed to update profile.')
      }

      setProfileName(data.name || '')
      setLoggedInUser((previousUser) => {
        const updatedUser = {
          ...previousUser,
          id: data.id,
          name: data.name,
          email: data.email,
        }

        localStorage.setItem('user', JSON.stringify(updatedUser))
        return updatedUser
      })

      setProfileNotice('Profile updated successfully!')
    } catch (err) {
      setProfileError(err.message || 'Could not update profile.')
    } finally {
      setProfileSaving(false)
    }
  }

  useEffect(() => {
    const timer = setTimeout(() => {
      loadStations()
    }, 0)

    return () => clearTimeout(timer)
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadMapStations()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [])

  useEffect(() => {
    const token = localStorage.getItem('token')

    if (!token) return

    const loadNotifications = async () => {
      try {
        const response = await fetch(
          `${API_URL}/notifications`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        )

        if (!response.ok) {
          throw new Error('Failed to load notifications')
        }

        const data = await response.json()

        // Use the polled data as the authoritative source of truth.
        // This avoids duplicate entries when WebSocket and polling
        // both deliver the same notification.
        setNotifications(data)
        setUnreadCount(data.filter((notification) => !notification.read).length)
      } catch (error) {
        console.error('Notification loading error:', error)
      }
    }

    loadNotifications()
  }, [])

  const markNotificationAsRead = async (notificationId) => {
    const token = localStorage.getItem('token')

    if (!token) return

    try {
      const response = await fetch(
        `${API_URL}/notifications/${notificationId}/read`,
        {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      )

      if (!response.ok) {
        throw new Error('Failed to mark notification as read')
      }

      const updatedNotification = await response.json()

      setNotifications((previous) =>
        previous.map((notification) =>
          notification.id === updatedNotification.id
            ? updatedNotification
            : notification
        )
      )

      setUnreadCount(
        (previous) =>
          Math.max(
            0,
            previous -
              (notifications.find((item) => item.id === notificationId)?.read
                ? 0
                : 1)
          )
      )
    } catch (error) {
      console.error('Mark notification as read error:', error)
    }
  }

  useEffect(() => {
    if (activePage === 'bookings' || activePage === 'dashboard') {
      const timer = setTimeout(() => {
        loadBookings()
      }, 0)

      return () => clearTimeout(timer)
    }
  }, [activePage, loadBookings])

  useEffect(() => {
    if (activePage === 'profile') {
      loadProfile()
        .then((data) => {
          if (!data) return

          setProfileName(data.name || '')

          setLoggedInUser((previousUser) => {
            const updatedUser = {
              ...previousUser,
              id: data.id,
              name: data.name,
              email: data.email,
            }

            localStorage.setItem('user', JSON.stringify(updatedUser))
            return updatedUser
          })
        })
        .catch((err) => {
          setProfileError(err.message || 'Could not load profile.')
        })
        .finally(() => {
          setProfileLoading(false)
        })
    }
  }, [activePage])

  const recentStations = useMemo(
    () =>
      [...stations]
        .sort((left, right) => Number(right.id ?? 0) - Number(left.id ?? 0))
        .slice(0, 4),
    [stations]
  )

  const availableCount = stations.filter(
    (station) => station.available === true
  ).length

  const filteredBookings = useMemo(() => {
    const search = bookingSearch.toLowerCase().trim()

    return bookings.filter((booking) => {
      const bookingId = String(booking.id || '')
      const stationName = String(
        booking.station?.name ||
        [booking.publicStation?.cpoName, booking.publicStation?.cityVillage].filter(Boolean).join(' - ')
      ).toLowerCase()
      const customerName = String(booking.customerName || '').toLowerCase()
      const status = String(booking.status || '').toUpperCase()

      const matchesSearch =
        bookingId.includes(search) ||
        stationName.includes(search) ||
        customerName.includes(search)

      const matchesStatus =
        bookingStatusFilter === 'all' ||
        status === bookingStatusFilter

      return matchesSearch && matchesStatus
    })
  }, [bookings, bookingSearch, bookingStatusFilter])

  const confirmedCount = bookings.filter(
    (booking) => String(booking.status).toUpperCase() === 'CONFIRMED'
  ).length
  const requestedCount = bookings.filter(
    (booking) => String(booking.status).toUpperCase() === 'REQUESTED'
  ).length

  const cancelledCount = bookings.filter(
    (booking) => String(booking.status).toUpperCase() === 'CANCELLED'
  ).length

  function openBookingForm(station) {
    if (station.bookingEnabled !== true) {
      setNotice('Booking is disabled for this station.')
      return
    }
    if (station.bookable !== true) {
      setNotice(station.bookingRestrictionReason || 'This station is currently unavailable for booking.')
      return
    }
    setSelectedStation(station)
    setBookingDate('')
    setStartTime('')
    setEndTime('')
    setBookingError('')
    setNotice('')
  }

  function closeBookingForm() {
    if (submitting) return
    setSelectedStation(null)
    setBookingError('')
  }

  async function handleBooking(event) {
    event.preventDefault()
    if (bookingSubmissionLock.current) return
    setBookingError('')
    setNotice('')

    if (!selectedStation) return

    if (startTime >= endTime) {
      setBookingError('End time must be later than start time.')
      return
    }

    try {
      setSubmitting(true)
      await runBookingSubmission(bookingSubmissionLock, async () => {
        const bookingPath = selectedStation.source === 'PUBLIC_DIRECTORY'
          ? 'public-station'
          : 'station'
        const response = await fetch(
          `${API_URL}/bookings/${bookingPath}/${selectedStation.internalId ?? selectedStation.id}`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${localStorage.getItem('token')}`,
            },
            body: JSON.stringify({
              bookingDate,
              startTime: `${startTime}:00`,
              endTime: `${endTime}:00`,
            }),
          }
        )

        const data = await response.json().catch(() => null)
        if (!response.ok) throw new Error(bookingErrorMessage(data))

        setSelectedStation(null)
        setNotice(data?.status === 'REQUESTED'
          ? `Booking request received. Live station availability is unverified. Request ID: ${data?.id ?? 'Created'}`
          : `Booking confirmed! Booking ID: ${data?.id ?? 'Created'}`)
        setActivePage('bookings')
        await loadBookings()
        return data
      })
    } catch (err) {
      setBookingError(err.message || 'Could not connect to the backend.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancelBooking(bookingId) {
    if (!window.confirm(`Are you sure you want to cancel booking #${bookingId}?`)) {
      return
    }

    setCancellingId(bookingId)
    setBookingsError('')

    try {
      const response = await fetch(
        `${API_URL}/bookings/${bookingId}/cancel`,
        {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${localStorage.getItem('token')}`,
          },
        }
      )

      const data = await response.json().catch(() => null)

      if (!response.ok) {
        throw new Error(bookingErrorMessage(data, 'Could not cancel this booking.'))
      }

      await loadBookings()
      setNotice(`Booking #${bookingId} cancelled.`)
    } catch (err) {
      setBookingsError(err.message || 'Could not cancel the booking.')
    } finally {
      setCancellingId(null)
    }
  }

  const today = new Date()
  const todayString = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-')

  function formatTime(time) {
    return time ? String(time).slice(0, 5) : '—'
  }

  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: '▦' },
    { id: 'stations', label: 'Charging Stations', icon: '⚡' },
    { id: 'map', label: 'Live Map', icon: '🗺️' },
    { id: 'bookings', label: 'My Bookings', icon: '▣' },
    { id: 'profile', label: 'My Profile', icon: '👤' },
  ]

  return (
    <div className="min-h-screen bg-[#080d18] text-slate-100 md:flex">
      <aside className="border-b border-white/10 bg-[#0c1423] p-4 md:sticky md:top-0 md:h-screen md:w-64 md:shrink-0 md:border-b-0 md:border-r md:p-5">
        <button
          type="button"
          onClick={() => setActivePage('dashboard')}
          className="flex items-center gap-3 px-2 py-3"
        >
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-400 text-2xl text-slate-950">
            ⚡
          </span>
          <span className="text-left">
            <span className="block text-lg font-extrabold tracking-tight">
              ChargeFinder
            </span>
            <span className="block text-xs text-slate-500">
              EV Management
            </span>
          </span>
        </button>

        <p className="mb-3 mt-8 px-3 text-xs font-bold uppercase tracking-[0.2em] text-slate-500">
          Workspace
        </p>

        <nav className="flex gap-2 overflow-x-auto md:flex-col">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                if (item.id === 'profile' && activePage !== 'profile') {
                  setProfileLoading(true)
                  setProfileError('')
                  setProfileNotice('')
                }
                setActivePage(item.id)
                setNotice('')
              }}
              className={`flex shrink-0 items-center gap-3 rounded-xl px-4 py-3 text-left text-sm font-semibold transition ${
                activePage === item.id
                  ? 'bg-emerald-400 text-slate-950 shadow-lg shadow-emerald-950/30'
                  : 'text-slate-400 hover:bg-white/5 hover:text-white'
              }`}
            >
              <span className="text-lg">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="mt-8 hidden rounded-2xl border border-emerald-400/15 bg-emerald-400/5 p-4 md:block">
          <div className="mb-2 flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            <span className="text-sm font-semibold text-emerald-300">
              EV Network
            </span>
          </div>
          <p className="text-xs leading-5 text-slate-500">
            Manage charging stations and reservations from one place.
          </p>
        </div>

      </aside>

      <main className="min-w-0 flex-1">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-white/10 bg-[#0b1220]/80 px-5 py-4 sm:px-8">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-emerald-400">
              EV CHARGING PLATFORM
            </p>
            <h1 className="mt-1 text-xl font-bold sm:text-2xl">
              {navItems.find((item) => item.id === activePage)?.label}
            </h1>
          </div>

          <div className="flex items-center gap-3">
            <span className="hidden rounded-full border border-emerald-400/20 bg-emerald-400/5 px-3 py-2 text-xs font-medium text-emerald-300 sm:inline-flex sm:items-center sm:gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              Charging network
            </span>
            <div className="relative">
              <button
                type="button"
                onClick={() => {
                  setShowNotifications((prev) => !prev)
                }}
                className="relative rounded-xl border border-white/10 bg-[#111c2e] p-2.5 text-white hover:bg-white/10"
                aria-label={`Notifications: ${unreadCount} unread`}
              >
                <span className="text-xl">🔔</span>

                {unreadCount > 0 && (
                  <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                    {unreadCount}
                  </span>
                )}
              </button>
              {showNotifications && (
                <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-xl border border-white/10 bg-[#111c2e] p-4 shadow-xl">
                  <h3 className="mb-3 font-semibold text-white">
                    Notifications
                  </h3>

                  {notifications.length === 0 ? (
                    <p className="text-sm text-slate-400">
                      No notifications yet.
                    </p>
                  ) : (
                    <div className="space-y-3">
                      {notifications.map((notification) => (
                        <div
                          key={notification.id}
                          className="rounded-lg border border-white/10 p-3"
                          onClick={() => {
                            if (!notification.read) {
                              markNotificationAsRead(notification.id)
                            }
                          }}
                        >
                          <p className="text-sm text-white">
                            {notification.message}
                          </p>

                          <p className="mt-1 text-xs text-slate-400">
                            {new Date(notification.createdAt).toLocaleString()}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
            <div className="flex items-center gap-3">
              <div className="hidden text-right sm:block">
                <p className="text-sm font-semibold text-white">
                  {loggedInUser?.name || loggedInUser?.fullName || 'EV User'}
                </p>
                <p className="text-xs text-slate-400">
                  {loggedInUser?.email || 'Logged in'}
                </p>
              </div>

              <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-[#111c2e] font-bold text-emerald-300">
                {(loggedInUser?.name || loggedInUser?.fullName || 'U')
                  .charAt(0)
                  .toUpperCase()}
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                setLoggedInUser(null)
                localStorage.removeItem('token')
                localStorage.removeItem('user')
                window.location.href = '/login'
              }}
              className="rounded-xl border border-red-400/20 px-4 py-2 text-sm font-semibold text-red-300 hover:bg-red-400/10"
            >
              Logout
            </button>
          </div>
        </header>

        <div className="mx-auto max-w-[1500px] p-5 sm:p-8">
          {notice && (
            <div
              role="status"
              className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-emerald-400/20 bg-emerald-400/10 p-4 text-sm text-emerald-300"
            >
              <span>✓ {notice}</span>
              <button
                type="button"
                onClick={() => setNotice('')}
                className="font-bold underline"
              >
                Dismiss
              </button>
            </div>
          )}

          {activePage === 'dashboard' && (
            <>
              <section className="relative mb-8 overflow-hidden rounded-3xl border border-emerald-400/15 bg-gradient-to-br from-[#102a2b] via-[#10202b] to-[#111827] p-6 sm:p-9">
                <div className="pointer-events-none absolute -right-10 -top-20 h-64 w-64 rounded-full bg-emerald-400/10 blur-3xl" />
                <div className="relative max-w-2xl">
                  <span className="inline-flex items-center gap-2 rounded-full border border-emerald-300/20 bg-emerald-300/10 px-3 py-1.5 text-xs font-semibold text-emerald-300">
                    <span className="h-2 w-2 rounded-full bg-emerald-400" />
                    SMART CHARGING NETWORK
                  </span>
                  <h2 className="mt-5 text-3xl font-extrabold leading-tight sm:text-5xl">
                    Power your journey.
                    <span className="block text-emerald-300">
                      Charge smarter.
                    </span>
                  </h2>
                  <p className="mt-4 max-w-xl text-sm leading-6 text-slate-300 sm:text-base">
                    Discover charging stations, check availability, and manage
                    your EV charging reservations from one dashboard.
                  </p>
                  <button
                    type="button"
                    onClick={() => setActivePage('stations')}
                    className="mt-6 rounded-xl bg-emerald-400 px-5 py-3 text-sm font-bold text-slate-950 transition hover:bg-emerald-300"
                  >
                    Explore Stations <span className="ml-2">→</span>
                  </button>
                </div>
                <div className="pointer-events-none absolute bottom-5 right-7 hidden text-[130px] opacity-10 lg:block">
                  ⚡
                </div>
              </section>

              <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
                <div>
                  <h2 className="text-xl font-bold">Network Overview</h2>
                  <p className="mt-1 text-sm text-slate-500">
                    Summary from your connected backend
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setLoading(true)
                    loadStations()
                    loadBookings()
                  }}
                  className="rounded-xl border border-white/10 px-4 py-2 text-sm text-slate-300 transition hover:bg-white/5"
                >
                  ↻ Refresh data
                </button>
              </div>

              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <StatCard icon="⚡" label="Total Stations" value={loading ? '…' : stations.length} detail="Registered charging stations" accent="emerald" />
                <StatCard icon="◉" label="Available Stations" value={loading ? '…' : availableCount} detail="Marked as available" accent="blue" />
                <StatCard icon="▣" label="Total Bookings" value={bookingsLoading ? '…' : bookings.length} detail="All reservation records" accent="violet" />
                <StatCard icon="✓" label="Confirmed Bookings" value={bookingsLoading ? '…' : confirmedCount} detail={`${cancelledCount} cancelled bookings`} accent="amber" />
              </div>

              {(error || bookingsError) && (
                <div className="mt-5 rounded-xl border border-red-400/20 bg-red-400/5 p-4 text-sm text-red-300">
                  {error || bookingsError}
                </div>
              )}

              <div className="mt-9 grid gap-6 xl:grid-cols-[1.4fr_1fr]">
                <section className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 sm:p-6">
                  <div className="mb-5 flex items-center justify-between gap-3">
                    <div>
                      <h3 className="text-lg font-bold">Recent Stations</h3>
                      <p className="mt-1 text-sm text-slate-500">A quick look at your network</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => setActivePage('stations')}
                      className="text-sm font-semibold text-emerald-300 hover:text-emerald-200"
                    >
                      View all →
                    </button>
                  </div>

                  {loading ? (
                    <p className="py-8 text-center text-sm text-slate-500">Loading stations...</p>
                  ) : stations.length === 0 ? (
                    <p className="py-8 text-center text-sm text-slate-500">No stations available yet.</p>
                  ) : (
                    <div className="space-y-3">
                      {recentStations.map((station) => (
                        <div key={station.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/5 bg-[#111d30] p-4">
                          <div className="flex min-w-0 items-center gap-3">
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-400/10 text-xl text-emerald-300">⚡</div>
                            <div className="min-w-0">
                              <p className="truncate font-semibold">{station.name}</p>
                              <p className="mt-1 truncate text-xs text-slate-500">{station.location || 'Location not provided'}</p>
                              <p className="mt-1 text-xs text-slate-400">{station.chargerType || 'Charger type N/A'} · ₹{station.pricePerUnit ?? '—'} / unit</p>
                            </div>
                          </div>
                          <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${station.available ? 'bg-emerald-400/10 text-emerald-300' : 'bg-red-400/10 text-red-300'}`}>
                            {station.available ? 'Available' : 'Unavailable'}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                <section className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 sm:p-6">
                  <div className="mb-5">
                    <h3 className="text-lg font-bold">Booking Summary</h3>
                    <p className="mt-1 text-sm text-slate-500">Current reservation status</p>
                  </div>

                  {bookingsLoading ? (
                    <p className="py-8 text-center text-sm text-slate-500">Loading bookings...</p>
                  ) : bookingsError ? (
                    <p className="py-8 text-center text-sm text-red-300">{bookingsError}</p>
                  ) : (
                    <div className="space-y-5">
                      <SummaryBar label="Confirmed" count={confirmedCount} total={bookings.length} color="bg-emerald-400" />
                      <SummaryBar label="Cancelled" count={cancelledCount} total={bookings.length} color="bg-red-400" />
                      <SummaryBar label="Requests" count={requestedCount} total={bookings.length} color="bg-amber-400" />
                      <button
                        type="button"
                        onClick={() => setActivePage('bookings')}
                        className="w-full rounded-xl border border-white/10 px-4 py-3 text-sm font-semibold text-slate-200 transition hover:border-emerald-400/30 hover:bg-white/5"
                      >
                        Manage My Bookings →
                      </button>
                    </div>
                  )}
                </section>
              </div>

              {/* MAP SECTION */}
              <section className="mt-6 rounded-2xl border border-white/10 bg-[#0e1727] p-5 sm:p-6">
                <div className="mb-5">
                  <h3 className="text-lg font-bold">Charging Stations Map</h3>
                  <p className="mt-1 text-sm text-slate-500">
                    Explore charging stations on the map
                  </p>
                </div>

                {mapLoading ? (
                  <p className="py-8 text-center text-sm text-slate-500">
                    Loading station locations...
                  </p>
                ) : mapError ? (
                  <p role="alert" className="py-8 text-center text-sm text-red-300">{mapError}</p>
                ) : (
                  <Suspense fallback={<div className="rounded-xl bg-[#111d30] p-4 text-slate-400">Loading map…</div>}>
                    <MapView stations={mapStations} onBookStation={openBookingForm} />
                  </Suspense>
                )}
              </section>
            </>
          )}

          {activePage === 'stations' && (
            <Suspense fallback={<p className="py-10 text-center text-slate-400">Loading station discovery...</p>}>
              <UnifiedDiscovery onBookStation={openBookingForm} />
            </Suspense>
          )}

          {activePage === 'map' && (
            <section className="space-y-6">
              <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 shadow-xl">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <span>🗺️</span> Interactive EV Charging Map
                </h2>
                <p className="mt-1 text-xs text-slate-400">
                  Real-time GPS map of managed charging hubs and nationwide stations with driving routes
                </p>
              </div>

              <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 shadow-xl">
                <Suspense fallback={<div className="rounded-xl bg-[#111d30] p-4 text-slate-400">Loading map...</div>}>
                  {mapLoading ? (
                    <p className="py-8 text-center text-sm text-slate-500">Loading station locations...</p>
                  ) : mapError ? (
                    <p role="alert" className="py-8 text-center text-sm text-red-300">{mapError}</p>
                  ) : (
                    <MapView stations={mapStations} onBookStation={openBookingForm} />
                  )}
                </Suspense>
              </div>
            </section>
          )}

          {activePage === 'bookings' && (
            <>
              <section className="mb-7 flex flex-wrap items-end justify-between gap-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.2em] text-emerald-400">Your charging activity</p>
                  <h2 className="mt-2 text-3xl font-extrabold">My Bookings</h2>
                  <p className="mt-2 text-sm text-slate-500">View and manage your charging reservations.</p>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <input
                      type="search"
                      value={bookingSearch}
                      onChange={(event) => setBookingSearch(event.target.value)}
                      placeholder="Search booking ID, station or customer..."
                      aria-label="Search bookings"
                      className="w-full rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-sm outline-none focus:border-emerald-400"
                    />

                    <select
                      value={bookingStatusFilter}
                      onChange={(event) => setBookingStatusFilter(event.target.value)}
                      aria-label="Filter bookings by status"
                      className="w-full rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-sm outline-none focus:border-emerald-400"
                    >
                      <option value="all">All booking statuses</option>
                      <option value="REQUESTED">Requested</option>
                      <option value="CONFIRMED">Confirmed</option>
                      <option value="CANCELLED">Cancelled</option>
                    </select>
                  </div>
                </div>
                <button type="button" onClick={() => setActivePage('stations')} className="rounded-xl bg-emerald-400 px-5 py-3 text-sm font-bold text-slate-950 hover:bg-emerald-300">+ New booking</button>
              </section>

              {bookingsError && (
                <div role="alert" className="mb-5 rounded-xl border border-red-400/20 bg-red-400/5 p-4 text-sm text-red-300">
                  {bookingsError}
                  <button type="button" onClick={loadBookings} className="ml-3 underline">Retry</button>
                </div>
              )}

              {bookingsLoading ? (
                <p className="py-10 text-center text-slate-400">Loading bookings...</p>
              ) : bookings.length === 0 ? (
                <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-10 text-center">
                  <div className="text-5xl">🔌</div>
                  <h3 className="mt-4 text-xl font-bold">No bookings yet</h3>
                  <p className="mt-2 text-sm text-slate-500">Your charging reservations will appear here.</p>
                  <button type="button" onClick={() => setActivePage('stations')} className="mt-6 rounded-xl bg-emerald-400 px-5 py-3 text-sm font-bold text-slate-950">Find a charging station</button>
                </div>
              ) : filteredBookings.length === 0 ? (
                <div className="rounded-2xl border border-white/10 bg-[#0e1727] p-8 text-center">
                  <h3 className="text-lg font-bold">No matching bookings</h3>
                  <p className="mt-2 text-sm text-slate-400">
                    Try a different search term or booking status.
                  </p>
                </div>
              ) : (
                <div className="grid gap-5 lg:grid-cols-2">
                  {filteredBookings.map((booking) => {
                    const status = String(booking.status || '').toUpperCase()
                    const isConfirmed = status === 'CONFIRMED'
                    const isPublicRequest = booking.publicStation != null
                    const stationName = booking.station?.name ||
                      [booking.publicStation?.cpoName, booking.publicStation?.cityVillage].filter(Boolean).join(' - ') ||
                      (booking.station?.id ? `Station #${booking.station.id}` : 'Station details unavailable')
                    const stationLocation = booking.station?.location || booking.publicStation?.location

                    return (
                      <article key={booking.id} className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 sm:p-6">
                        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-white/10 pb-4">
                          <div>
                            <p className="text-xs text-slate-500">BOOKING ID</p>
                            <h3 className="mt-1 text-xl font-bold">#{booking.id}</h3>
                          </div>
                          <span className={`rounded-full px-3 py-1.5 text-xs font-bold ${isConfirmed ? 'bg-emerald-400/10 text-emerald-300' : status === 'CANCELLED' ? 'bg-red-400/10 text-red-300' : 'bg-amber-400/10 text-amber-300'}`}>
                            {status || 'UNKNOWN'}
                          </span>
                        </div>

                        <div className="mt-5">
                          <p className="text-xs text-slate-500">CHARGING STATION</p>
                          <p className="mt-1 font-semibold">
                            {stationName}
                          </p>
                          {stationLocation && <p className="mt-1 text-sm text-slate-500">📍 {stationLocation}</p>}
                          {isPublicRequest && (
                            <p className="mt-2 text-xs text-amber-300">Request received; live availability is unverified.</p>
                          )}
                        </div>

                        <div className="mt-4">
                          <p className="text-xs text-slate-500">CUSTOMER</p>
                          <p className="mt-1 font-medium">{booking.customerName || '—'}</p>
                        </div>

                        <div className="mt-4 grid grid-cols-2 gap-3">
                          <div className="rounded-xl bg-[#111d30] p-3">
                            <p className="text-xs text-slate-500">Date</p>
                            <p className="mt-1 text-sm font-semibold">{booking.bookingDate || '—'}</p>
                          </div>
                          <div className="rounded-xl bg-[#111d30] p-3">
                            <p className="text-xs text-slate-500">Time</p>
                            <p className="mt-1 text-sm font-semibold">{formatTime(booking.startTime)} – {formatTime(booking.endTime)}</p>
                          </div>
                        </div>

                        {status !== 'CANCELLED' && (
                          <button type="button" disabled={cancellingId === booking.id} onClick={() => handleCancelBooking(booking.id)} className="mt-5 w-full rounded-xl border border-red-400/20 bg-red-400/5 px-4 py-3 text-sm font-bold text-red-300 transition hover:bg-red-400/10 disabled:opacity-50">
                            {cancellingId === booking.id ? 'Cancelling...' : 'Cancel booking'}
                          </button>
                        )}
                      </article>
                    )
                  })}
                </div>
              )}
            </>
          )}

          {activePage === 'profile' && (
            <section>
              <div className="mb-7">
                <p className="text-xs font-bold uppercase tracking-[0.2em] text-emerald-400">
                  Account Settings
                </p>
                <h2 className="mt-2 text-3xl font-extrabold">My Profile</h2>
                <p className="mt-2 text-sm text-slate-500">
                  View and update your account information.
                </p>
              </div>

              <div className="max-w-2xl rounded-2xl border border-white/10 bg-[#0e1727] p-6 sm:p-8">
                {profileLoading ? (
                  <p className="py-8 text-center text-slate-400">
                    Loading profile...
                  </p>
                ) : (
                  <>
                    <div className="flex items-center gap-4 border-b border-white/10 pb-6">
                      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-400/10 text-2xl font-bold text-emerald-300">
                        {(profileName || 'U').charAt(0).toUpperCase()}
                      </div>

                      <div>
                        <h3 className="text-xl font-bold text-white">
                          {profileName || 'EV User'}
                        </h3>
                        <p className="mt-1 text-sm text-slate-400">
                          Registered User
                        </p>
                      </div>
                    </div>

                    <form onSubmit={saveProfile} className="mt-6 space-y-5">
                      <div>
                        <label
                          htmlFor="profileName"
                          className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-400"
                        >
                          Full Name
                        </label>
                        <input
                          id="profileName"
                          type="text"
                          value={profileName}
                          onChange={(event) => setProfileName(event.target.value)}
                          maxLength={100}
                          required
                          className="w-full rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 text-white outline-none focus:border-emerald-400"
                          placeholder="Enter your full name"
                        />
                      </div>

                      <div className="rounded-xl bg-[#111d30] p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                          Email Address
                        </p>
                        <p className="mt-2 break-all font-medium text-white">
                          {loggedInUser?.email || 'Not available'}
                        </p>
                        <p className="mt-1 text-xs text-slate-500">
                          Email address cannot be changed here.
                        </p>
                      </div>

                      <div className="rounded-xl bg-[#111d30] p-4">
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                          User ID
                        </p>
                        <p className="mt-2 font-medium text-white">
                          {loggedInUser?.id ?? 'Not available'}
                        </p>
                      </div>

                      {profileError && (
                        <p role="alert" className="text-sm text-red-400">
                          {profileError}
                        </p>
                      )}

                      {profileNotice && (
                        <p role="status" className="text-sm text-emerald-400">
                          {profileNotice}
                        </p>
                      )}

                      <button
                        type="submit"
                        disabled={profileSaving || !profileName.trim()}
                        className="w-full rounded-xl bg-emerald-400 px-5 py-3 font-bold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {profileSaving ? 'Saving...' : 'Save Changes'}
                      </button>
                    </form>
                  </>
                )}
              </div>
            </section>
          )}
        </div>

        <footer className="border-t border-white/10 px-5 py-5 text-center text-xs text-slate-600 sm:px-8">
          ChargeFinder
        </footer>
      </main>

      {selectedStation && createPortal((
        <div
          className="fixed inset-0 z-[1000] flex items-center justify-center overflow-y-auto bg-black/75 p-4 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget) closeBookingForm()
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="booking-title"
            className="my-auto w-full max-w-lg rounded-2xl border border-white/10 bg-[#0e1727] p-6 shadow-2xl sm:p-8"
          >
            <div className="mb-6 flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-bold tracking-[0.2em] text-emerald-400">
                  {selectedStation.source === 'PUBLIC_DIRECTORY' ? 'BOOKING REQUEST' : 'RESERVE YOUR SLOT'}
                </p>
                <h3 id="booking-title" className="mt-2 text-2xl font-extrabold">
                  {selectedStation.source === 'PUBLIC_DIRECTORY' ? 'Request Charging' : 'Book Charging'}
                </h3>
                <p className="mt-2 font-semibold">{selectedStation.name}</p>
                <p className="mt-1 text-sm text-slate-500">{selectedStation.location}</p>
              </div>
              <button type="button" onClick={closeBookingForm} disabled={submitting} aria-label="Close booking form" className="rounded-lg px-3 py-2 text-xl text-slate-400 hover:bg-white/5 hover:text-white">×</button>
            </div>

            <form onSubmit={handleBooking} className="space-y-5">
              <p className="rounded-xl bg-[#111d30] p-3 text-sm text-slate-300">
                Booking under your account: {loggedInUser?.name || 'account holder'}
              </p>
              {selectedStation.source === 'PUBLIC_DIRECTORY' && (
                <p className="rounded-xl border border-amber-400/20 bg-amber-400/5 p-3 text-sm text-amber-200">
                  This submits a booking request only. Live station availability is not verified.
                </p>
              )}

              <div>
                <label htmlFor="bookingDate" className="mb-2 block text-sm text-slate-400">Booking date</label>
                <input id="bookingDate" type="date" required min={todayString} value={bookingDate} onChange={(event) => setBookingDate(event.target.value)} className="w-full rounded-xl border border-white/10 bg-[#111d30] px-4 py-3 outline-none focus:border-emerald-400" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="startTime" className="mb-2 block text-sm text-slate-400">Start time</label>
                  <input id="startTime" type="time" required value={startTime} onChange={(event) => setStartTime(event.target.value)} className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-3 outline-none focus:border-emerald-400" />
                </div>
                <div>
                  <label htmlFor="endTime" className="mb-2 block text-sm text-slate-400">End time</label>
                  <input id="endTime" type="time" required value={endTime} onChange={(event) => setEndTime(event.target.value)} className="w-full rounded-xl border border-white/10 bg-[#111d30] px-3 py-3 outline-none focus:border-emerald-400" />
                </div>
              </div>

              {bookingError && <p role="alert" className="rounded-xl border border-red-400/20 bg-red-400/5 p-3 text-sm text-red-300">{bookingError}</p>}

              <button type="submit" disabled={submitting} className="w-full rounded-xl bg-emerald-400 px-4 py-3 font-bold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-wait disabled:opacity-60">
                {submitting
                  ? 'Submitting...'
                  : selectedStation.source === 'PUBLIC_DIRECTORY'
                    ? 'Submit Booking Request'
                    : 'Confirm booking'}
              </button>
            </form>
          </section>
        </div>
      ), document.body)}
    </div>
  )
}

function StatCard({ icon, label, value, detail, accent }) {
  const accents = {
    emerald: 'border-emerald-400/15 bg-emerald-400/10 text-emerald-300',
    blue: 'border-blue-400/15 bg-blue-400/10 text-blue-300',
    violet: 'border-violet-400/15 bg-violet-400/10 text-violet-300',
    amber: 'border-amber-400/15 bg-amber-400/10 text-amber-300',
  }

  return (
    <article className="rounded-2xl border border-white/10 bg-[#0e1727] p-5 transition hover:border-white/20">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm text-slate-400">{label}</p>
          <p className="mt-3 text-3xl font-extrabold tracking-tight">{value}</p>
        </div>
        <div className={`flex h-11 w-11 items-center justify-center rounded-xl border text-xl ${accents[accent]}`}>{icon}</div>
      </div>
      <p className="mt-4 border-t border-white/5 pt-3 text-xs text-slate-500">{detail}</p>
    </article>
  )
}

function SummaryBar({ label, count, total, color }) {
  const percentage = total > 0 ? Math.round((count / total) * 100) : 0

  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-3 text-sm">
        <span className="text-slate-400">{label}</span>
        <span className="font-semibold text-slate-200">
          {count} <span className="text-slate-500">({percentage}%)</span>
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-white/5">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${percentage}%` }} />
      </div>
    </div>
  )
}

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token'))

  useEffect(() => {
    function updateToken() {
      setToken(localStorage.getItem('token'))
    }

    window.addEventListener('auth-change', updateToken)
    return () => window.removeEventListener('auth-change', updateToken)
  }, [])

  return (
    <BrowserRouter>
      <Suspense fallback={<main className="min-h-screen bg-slate-950 p-8 text-center text-slate-400">Loading page...</main>}>
      <Routes>
        <Route path="/public-stations" element={<PublicStationsPage />} />

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/register"
          element={<Register />}
        />

        <Route
          path="/admin"
          element={
            token
              ? <AdminDashboard />
              : <Navigate to="/login" replace />
          }
        />

        <Route
          path="/"
          element={
            token
              ? <Dashboard />
              : <Navigate to="/login" replace />
          }
        />

        <Route
          path="*"
          element={<Navigate to="/" replace />}
        />
      </Routes>
      </Suspense>
    </BrowserRouter>
  )
}

export default App
