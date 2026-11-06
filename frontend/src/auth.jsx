import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post, session, setUnauthorizedHandler } from './api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(session.user())
  const navigate = useNavigate()

  const logout = useCallback(() => {
    session.clear()
    setUser(null)
  }, [])

  // 토큰이 만료되면(401) 로그인 화면으로 보내고, 로그인 후 원래 화면으로 돌아오게 한다
  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout()
      const next = window.location.pathname + window.location.search
      navigate(`/login?next=${encodeURIComponent(next)}&expired=1`, { replace: true })
    })
  }, [logout, navigate])

  const login = useCallback(async (email, password) => {
    const res = await post('/auth/login', { email, password })
    session.save(res.accessToken, res.user)
    setUser(res.user)
    return res.user
  }, [])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

export const homePath = (user) => (user?.role === 'ADMIN' ? '/admin' : '/student')
