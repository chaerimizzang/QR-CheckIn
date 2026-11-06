import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { SESSION_LABEL, STATUS_LABEL } from '../util'

export function Layout({ title, back, children, actions }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="page">
      <header className="topbar">
        <div className="topbar-left">
          {back && (
            <button className="icon-btn" onClick={() => navigate(back)} aria-label="뒤로">
              ←
            </button>
          )}
          <Link to="/" className="brand">QR 출석</Link>
        </div>
        {user && (
          <div className="topbar-right">
            <span className="muted small">{user.name} · {user.role === 'ADMIN' ? '관리자' : '수강생'}</span>
            <button className="link-btn" onClick={() => { logout(); navigate('/login') }}>로그아웃</button>
          </div>
        )}
      </header>
      <main className="content">
        {(title || actions) && (
          <div className="page-head">
            {title && <h1>{title}</h1>}
            {actions}
          </div>
        )}
        {children}
      </main>
    </div>
  )
}

export function ErrorBox({ error }) {
  if (!error) return null
  return (
    <div className="alert alert-error">
      <strong>{error.message}</strong>
      {error.detail?.hint && <div className="small">{error.detail.hint}</div>}
    </div>
  )
}

export function StatusBadge({ status }) {
  if (!status) return <span className="badge badge-none">미정</span>
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>
}

export function SessionBadge({ status }) {
  return <span className={`badge badge-s-${status.toLowerCase()}`}>{SESSION_LABEL[status]}</span>
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>
}

export function Loading() {
  return <div className="empty">불러오는 중…</div>
}
