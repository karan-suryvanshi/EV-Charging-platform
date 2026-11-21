
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API_URL } from '../api'

export default function Register() {
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setMessage('')
    setLoading(true)

    try {
      const response = await fetch(`${API_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password }),
      })

      const data = await response.json().catch(() => null)

      if (!response.ok) {
        throw new Error(
          data?.message ||
          (data && Object.values(data).find((value) => typeof value === 'string')) ||
          `Registration failed. Status: ${response.status}` 
        )
      }

      setMessage('Registration successful! Redirecting to login...')
      setTimeout(() => navigate('/login'), 1200)
    } catch (err) {
      setError(err.message || 'Unable to connect to the server.')
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
          <h1 className="text-3xl font-bold">Create Account</h1>
          <p className="mt-2 text-slate-400">
            Join the EV Charging platform
          </p>
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-red-950 p-3 text-sm text-red-300">
            {error}
          </p>
        )}

        {message && (
          <p role="status" className="rounded-lg bg-emerald-950 p-3 text-sm text-emerald-300">
            {message}
          </p>
        )}

        <div>
          <label htmlFor="name" className="mb-2 block text-sm">
            Full Name
          </label>
          <input
            id="name"
            type="text"
            autoComplete="name"
            required
            maxLength={100}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Enter your full name"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 outline-none focus:border-emerald-400"
          />
        </div>

        <div>
          <label htmlFor="email" className="mb-2 block text-sm">
            Email Address
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            maxLength={150}
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
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="At least 8 characters"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-4 py-3 outline-none focus:border-emerald-400"
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-emerald-500 px-4 py-3 font-semibold text-slate-950 hover:bg-emerald-400 disabled:opacity-60"
        >
          {loading ? 'Creating account...' : 'Register'}
        </button>

        <p className="text-center text-sm text-slate-400">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-emerald-400 hover:underline">
            Login
          </Link>
        </p>
      </form>
    </main>
  )
}
