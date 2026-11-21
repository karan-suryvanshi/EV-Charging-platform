import { describe, expect, it, vi } from 'vitest'
import { refreshBookingsForNotification } from './notificationSocket'

describe('booking status notifications', () => {
  it('refreshes booking history for booking status notifications', () => {
    const refreshBookings = vi.fn()

    refreshBookingsForNotification(
      { type: 'BOOKING_STATUS_UPDATED' },
      refreshBookings
    )

    expect(refreshBookings).toHaveBeenCalledOnce()
  })

  it('does not refresh booking history for unrelated notifications', () => {
    const refreshBookings = vi.fn()

    refreshBookingsForNotification(
      { type: 'PROFILE_UPDATED' },
      refreshBookings
    )

    expect(refreshBookings).not.toHaveBeenCalled()
  })
})
