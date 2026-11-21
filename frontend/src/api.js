const DEFAULT_API_URL = 'http://localhost:8080/api'

export function normalizeApiUrl(url) {
  return (url || DEFAULT_API_URL).replace(/\/+$/, '')
}

export function websocketUrl(apiUrl) {
  return `${apiUrl.replace(/^http/i, 'ws').replace(/\/api$/i, '')}/ws`
}

export const API_URL = normalizeApiUrl(import.meta.env.VITE_API_BASE_URL)

export const WEBSOCKET_URL = websocketUrl(API_URL)
