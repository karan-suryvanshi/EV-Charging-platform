export async function runBookingSubmission(lock, submit) {
  if (lock.current) return { ignored: true }

  lock.current = true
  try {
    return await submit()
  } finally {
    lock.current = false
  }
}

export function bookingErrorMessage(data, fallback = 'Booking failed. Please try again.') {
  return data?.detail || data?.message || data?.title || fallback
}
