import { canDecideBooking } from './bookingAdminActions'

export function AdminBookingActions({
  booking,
  disabled,
  onApprove,
  onCancel,
}) {
  if (!canDecideBooking(booking)) {
    return <span>{booking.status || 'UNKNOWN'}</span>
  }

  return (
    <div className="flex flex-wrap gap-2">
      <button
        type="button"
        disabled={disabled}
        onClick={onApprove}
        className="rounded-lg bg-emerald-500 px-3 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50"
      >
        Approve
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={onCancel}
        className="rounded-lg border border-red-400/30 bg-red-400/5 px-3 py-2 text-sm font-semibold text-red-300 disabled:cursor-not-allowed disabled:opacity-50"
      >
        Cancel
      </button>
    </div>
  )
}
