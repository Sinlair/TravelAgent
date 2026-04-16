import type { ApiResponse } from '../types/api'

async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  const payload = (await response.json()) as ApiResponse<T>
  if (payload.code !== '0000') {
    throw new Error(payload.info)
  }
  return payload.data
}

export async function apiGet<T>(url: string): Promise<T> {
  return unwrap<T>(await fetch(url))
}

export async function apiPost<T>(url: string, body: unknown): Promise<T> {
  return unwrap<T>(
    await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })
  )
}

export async function apiPatch<T>(url: string, body: unknown): Promise<T> {
  return unwrap<T>(
    await fetch(url, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })
  )
}

export async function apiDelete<T>(url: string): Promise<T> {
  return unwrap<T>(
    await fetch(url, {
      method: 'DELETE'
    })
  )
}
