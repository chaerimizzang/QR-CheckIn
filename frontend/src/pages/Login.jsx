import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { homePath, useAuth } from '../auth'
import { ErrorBox } from '../components/ui'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next')
  const [email, setEmail] = useState(params.get('email') || '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={next || homePath(user)} replace />

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const u = await login(email, password)
      navigate(next || homePath(u), { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const signupLink = `/signup${next ? `?next=${encodeURIComponent(next)}` : ''}`

  return (
    <div className="auth-wrap">
      <form className="card auth-card" onSubmit={submit}>
        <div className="auth-logo">QR 출석</div>
        <p className="muted center">강의실에서 QR을 찍으면 출석이 기록돼요</p>
        {params.get('expired') && <div className="alert alert-info">로그인이 만료되었어요. 다시 로그인해 주세요.</div>}
        {next?.startsWith('/check-in') && <div className="alert alert-info">로그인하면 바로 출석 처리를 이어서 할게요.</div>}
        <ErrorBox error={error} />
        <label>
          이메일
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </label>
        <label>
          비밀번호
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
        <p className="center small">
          계정이 없나요? <Link to={signupLink}>회원가입</Link>
        </p>
      </form>
    </div>
  )
}
