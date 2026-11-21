import { describe, expect, it } from 'vitest'
import { normalizeApiUrl, websocketUrl } from './api'

describe('API endpoint configuration', () => {
  it('removes trailing slashes from the configured API URL', () => {
    expect(normalizeApiUrl('http://localhost:8082/api///')).toBe(
      'http://localhost:8082/api'
    )
  })

  it('uses the local backend API when no URL is configured', () => {
    expect(normalizeApiUrl('')).toBe('http://localhost:8080/api')
  })

  it('derives the WebSocket endpoint from the API URL', () => {
    expect(websocketUrl('https://charging.example/api')).toBe(
      'wss://charging.example/ws'
    )
  })
})
