import { Client } from '@stomp/stompjs'
import { WEBSOCKET_URL } from './api'

export function isBookingStatusNotification(notification) {
  return ['BOOKING_STATUS_UPDATED', 'BOOKING_CANCELLED', 'BOOKING_CONFIRMED']
    .includes(notification?.type)
}

export function refreshBookingsForNotification(notification, refreshBookings) {
  if (isBookingStatusNotification(notification)) {
    refreshBookings()
  }
}

export function connectNotificationSocket({
  userId,
  token,
  onNotification,
}) {
  const client = new Client({
    brokerURL: WEBSOCKET_URL,

    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },

    reconnectDelay: 5000,

    onConnect: () => {
      console.log('WebSocket connected! User ID:', userId)

      client.subscribe(
        `/topic/notifications/${userId}`,
        (message) => {
          console.log('New WebSocket notification:', message.body)

          try {
            const notification = JSON.parse(message.body)
            onNotification(notification)
          } catch (error) {
            console.error('Invalid notification message:', error)
          }
        }
      )
    },

    onStompError: (frame) => {
      console.error('WebSocket STOMP error:', frame.headers.message)
      console.error('STOMP error details:', frame.body)
    },

    onWebSocketError: (error) => {
      console.error('WebSocket connection error:', error)
    },
  })

  client.activate()

  return () => {
    client.deactivate()
  }
}
