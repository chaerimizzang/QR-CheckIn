// 백엔드 API 호출. 화면과 API 가 같은 주소(8080 또는 터널 주소)에서 제공되므로 상대 경로를 쓴다.
const TOKEN_KEY = 'qrcheckin.token'
const USER_KEY = 'qrcheckin.user'

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `요청 실패 (${status})`)
    this.status = status
    this.code = body?.code || 'UNKNOWN'
    this.detail = body?.detail || {}
  }
}

export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  user: () => {
    try { return JSON.parse(localStorage.getItem(USER_KEY)) } catch { return null }
  },
  save(token, user) {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) { onUnauthorized = fn }

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {}
  const token = session.token()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(`/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, { code: 'NETWORK', message: '서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요.' })
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized()
    throw new ApiError(res.status, data)
  }
  return data
}

export const get = (p) => api(p)
export const post = (p, body) => api(p, { method: 'POST', body: body ?? {} })
export const put = (p, body) => api(p, { method: 'PUT', body })
export const del = (p) => api(p, { method: 'DELETE' })
