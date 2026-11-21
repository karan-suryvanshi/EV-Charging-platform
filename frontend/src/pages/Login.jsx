
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API_URL } from '../api'

export default function Login() {
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event) {
    // Prevent normal HTML form submission and page refresh
    event.preventDefault()

    if (loading) return

    setError('')
    setLoading(true)

    try {
      const response = await fetch(`${API_URL}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      })

      const data = await response.json().catch(() => null)

      if (!response.ok) {
        throw new Error(
          data?.message ||
            data?.error ||
            `Login failed. Status: ${response.status}`
        )
      }

      if (!data?.token) {
        throw new Error(
          'Login response does not contain a token. Check the backend response.'
        )
      }

      // Save authentication data
      localStorage.setItem('token', data.token)

      if (data.user) {
        localStorage.setItem('user', JSON.stringify(data.user))
      } else {
        localStorage.removeItem('user')
      }

      // Update App.jsx authentication state without an AuthContext
      window.dispatchEvent(new Event('auth-change'))

      // Navigate to the dashboard
      if (data.user?.role === 'ADMIN') {
        navigate('/admin', { replace: true })
      } else {
        navigate('/', { replace: true })
      }
    } catch (err) {
      if (err instanceof TypeError) {
        setError(
          'Cannot connect to the server. Check whether the Spring Boot backend is running.'
        )
      } else {
        setError(err.message || 'Login failed. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 px-4 py-10 text-white">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md space-y-5 rounded-2xl border border-slate-700 bg-slate-900 p-8 shadow-xl"
      >
        <div className="text-center">
          <h1 className="text-3xl font-bold">Welcome Back</h1>
          <p className="mt-2 text-slate-400">
            Login to your EV Charging account
          </p>
        </div>

        {error && (
          <p
            role="alert"
            className="rounded-lg bg-red-950 p-3 text-sm text-red-300"
          >
            {error}
          </p>
        )}

        <div>
          <label htmlFor="email" className="mb-2 block text-sm">
            Email Address
          </label>

          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 outline-none focus:border-emerald-400"
          />
        </div>

        <div>
          <label htmlFor="password" className="mb-2 block text-sm">
            Password
          </label>

          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Enter your password"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 outline-none focus:border-emerald-400"
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-emerald-500 px-4 py-3 font-semibold text-slate-950 hover:bg-emerald-400 disabled:cursor-wait disabled:opacity-60"
        >
          {loading ? 'Logging in...' : 'Login'}
        </button>

        <p className="text-center text-sm text-slate-400">
          Don't have an account?{' '}
          <Link
            to="/register"
            className="font-semibold text-emerald-400 hover:underline"
          >
            Register
          </Link>
        </p>

        <p className="text-center text-sm">
          <Link to="/public-stations" className="font-semibold text-emerald-300 hover:underline">
            Browse public charging stations
          </Link>
        </p>
      </form>
    </main>
  )
}
