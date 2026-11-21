import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { API_URL } from '../api'
import { AdminBookingActions } from './AdminBookingActions'
import { submitAdminBookingDecision } from './bookingAdminActions'

describe('admin booking decisions', () => {
  it('shows Approve and Cancel only for requested bookings', () => {
    const renderActions = (status) => renderToStaticMarkup(
      <AdminBookingActions
        booking={{ id: 12, status }}
        onApprove={() => {}}
        onCancel={() => {}}
      />
    )

    const requested = renderActions('REQUESTED')
    expect(requested).toContain('Approve')
    expect(requested).toContain('Cancel')

    for (const status of ['CONFIRMED', 'CANCELLED']) {
      const decided = renderActions(status)
      expect(decided).not.toContain('Approve')
      expect(decided).not.toContain('Cancel')
      expect(decided).toContain(status)
    }
  })

  it('refreshes bookings after a successful Admin decision', async () => {
    const refreshBookings = vi.fn()
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 12, status: 'CONFIRMED' }),
    })

    const result = await submitAdminBookingDecision({
      bookingId: 12,
      status: 'CONFIRMED',
      token: 'admin-token',
      refreshBookings,
      fetchImpl,
    })

    expect(fetchImpl).toHaveBeenCalledWith(
      `${API_URL}/bookings/admin/12/status`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ status: 'CONFIRMED' }),
      })
    )
    expect(refreshBookings).toHaveBeenCalledOnce()
    expect(result.status).toBe('CONFIRMED')
  })

  it('sends cancellation decisions and reports backend detail on failure', async () => {
    const refreshBookings = vi.fn()
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ detail: 'Only requested bookings can be cancelled.' }),
    })

    await expect(submitAdminBookingDecision({
      bookingId: 14,
      status: 'CANCELLED',
      token: 'admin-token',
      refreshBookings,
      fetchImpl,
    })).rejects.toThrow('Only requested bookings can be cancelled.')

    expect(fetchImpl).toHaveBeenCalledWith(
      `${API_URL}/bookings/admin/14/status`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ status: 'CANCELLED' }),
      })
    )
    expect(refreshBookings).not.toHaveBeenCalled()
  })
})
