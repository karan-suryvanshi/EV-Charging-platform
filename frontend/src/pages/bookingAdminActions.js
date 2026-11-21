import { API_URL } from '../api'

export function canDecideBooking(booking) {
  return booking?.status === 'REQUESTED'
}

export async function submitAdminBookingDecision({
  bookingId,
  status,
  token,
  refreshBookings,
  fetchImpl = fetch,
}) {
  const response = await fetchImpl(
    `${API_URL}/bookings/admin/${bookingId}/status`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ status }),
    }
  )
  const updatedBooking = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(
      updatedBooking?.detail ||
      updatedBooking?.message ||
      'Could not update booking status.'
    )
  }

  await refreshBookings()
  return updatedBooking
}
