import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { SESSION_LABEL, STATUS_LABEL } from '../util'
import Icon from './Icon'


/** 왼쪽 메뉴 항목 — 역할별 */
const MENU = {
  ADMIN: [
    { to: '/profile', label: '프로필', icon: 'user', match: (p) => p === '/profile' },
    { to: '/admin', label: '내 교육', icon: 'home', match: (p, q) => p === '/admin' && !q.get('new') },
    { to: '/admin?new=1', label: '교육 만들기', icon: 'plus', match: (p, q) => p === '/admin' && q.get('new') === '1' },
    { to: '/me/password', label: '비밀번호 변경', icon: 'lock', match: (p) => p === '/me/password' },
  ],
  STUDENT: [
    { to: '/profile', label: '프로필', icon: 'user', match: (p) => p === '/profile' },
    { to: '/scan', label: 'QR 스캔', icon: 'scan', match: (p) => p === '/scan' },
    { to: '/student', label: '내 교육', icon: 'home', match: (p) => p === '/student' },
    { to: '/me/password', label: '비밀번호 변경', icon: 'lock', match: (p) => p === '/me/password' },
  ],
}

function SideMenu({ open, onClose }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const query = new URLSearchParams(search)

  // Esc 로 닫기, 열려 있는 동안 뒤 화면 스크롤 막기
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev }
  }, [open, onClose])

  if (!user) return null
  const items = MENU[user.role] ?? []
  return (
    <>
      <div className={`drawer-backdrop${open ? ' open' : ''}`} onClick={onClose} aria-hidden="true" />
      <nav className={`drawer${open ? ' open' : ''}`} aria-label="메뉴" aria-hidden={!open}>
        <div className="drawer-head">
          <div className="drawer-avatar" aria-hidden="true">{user.name?.slice(0, 1)}</div>
          <div className="drawer-who">
            <div className="drawer-name">{user.name}</div>
            <div className="muted small">{user.role === 'ADMIN' ? '관리자' : '수강생'}</div>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="메뉴 닫기" tabIndex={open ? 0 : -1}>
            <Icon name="close" size={22} />
          </button>
        </div>
        <div className="drawer-items">
          {items.map((it) => {
            const active = it.match(pathname, query)
            return (
              <Link key={it.to} to={it.to} onClick={onClose} tabIndex={open ? 0 : -1}
                className={`drawer-item${active ? ' active' : ''}`} aria-current={active ? 'page' : undefined}>
                <Icon name={active ? `${it.icon}-active` : it.icon} size={20} />
                {it.label}
              </Link>
            )
          })}
        </div>
        <button type="button" className="drawer-item drawer-logout" tabIndex={open ? 0 : -1}
          onClick={() => { onClose(); logout(); navigate('/login') }}>
          <Icon name="logout" size={20} />
          로그아웃
        </button>
      </nav>
    </>
  )
}

export function Layout({ title, back, children, actions }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  // 화면이 바뀌면 메뉴 닫기
  useEffect(() => { setMenuOpen(false) }, [location.pathname, location.search])

  return (
    <div className="page">
      <header className="topbar">
        <div className="topbar-left">
          {user && (
            <button className="icon-btn" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기" aria-expanded={menuOpen}>
              <Icon name="menu" size={22} />
            </button>
          )}
          {back && (
            <button className="icon-btn" onClick={() => navigate(back)} aria-label="뒤로">
              <Icon name="back" size={22} />
            </button>
          )}
          <Link to="/" className="brand">QR 출석</Link>
        </div>
      </header>
      <SideMenu open={menuOpen} onClose={() => setMenuOpen(false)} />
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
