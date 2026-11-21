import { describe, expect, it, vi } from 'vitest'
import { bookingErrorMessage, runBookingSubmission } from './bookingSubmission'

describe('booking submission', () => {
  it('ignores a concurrent second submit until the first request settles', async () => {
    const lock = { current: false }
    let resolveRequest
    const submit = vi.fn(() => new Promise((resolve) => { resolveRequest = resolve }))

    const first = runBookingSubmission(lock, submit)
    const second = await runBookingSubmission(lock, submit)

    expect(second).toEqual({ ignored: true })
    expect(submit).toHaveBeenCalledOnce()

    resolveRequest({ id: 7 })
    await expect(first).resolves.toEqual({ id: 7 })
    expect(lock.current).toBe(false)
  })

  it('releases the submission lock when the request fails', async () => {
    const lock = { current: false }
    await expect(runBookingSubmission(lock, async () => { throw new Error('failed') }))
      .rejects.toThrow('failed')
    expect(lock.current).toBe(false)
  })

  it('uses Spring error details for user-facing feedback', () => {
    expect(bookingErrorMessage({ detail: 'Bookings are disabled.' })).toBe('Bookings are disabled.')
    expect(bookingErrorMessage({ message: 'Conflict' })).toBe('Conflict')
    expect(bookingErrorMessage(null)).toBe('Booking failed. Please try again.')
  })
})
