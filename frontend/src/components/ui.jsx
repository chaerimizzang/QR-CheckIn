import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { get } from '../api'
import { useAuth } from '../auth'
import { SESSION_LABEL, STATUS_LABEL } from '../util'
import Icon from './Icon'


/** 왼쪽 메뉴 항목 — 홈(관리자: 내 교육·교육 만들기 / 수강생: 내 교육·QR 스캔)·프로필·시간표·출석 인정 */
const COMMON = [
  { to: '/profile', label: '프로필', icon: 'user', match: (p) => p === '/profile' },
  { to: '/timetable', label: '시간표', icon: 'calendar', match: (p) => p === '/timetable' },
  { to: '/requests', label: '출석 인정', icon: 'clipboard-check', match: (p) => p === '/requests', count: true },
]
const MENU = {
  ADMIN: [{ to: '/admin', label: '홈', icon: 'home', match: (p) => p === '/admin' }, ...COMMON],
  // 학생: 홈 · 프로필 · 시간표 · 출결 현황 · 출석 인정
  STUDENT: [{ to: '/student', label: '홈', icon: 'home', match: (p) => p === '/student' }, ...COMMON.slice(0, 2),
    { to: '/attendance', label: '출결 현황', icon: 'chart-column', match: (p) => p === '/attendance' }, ...COMMON.slice(2)],
}

function SideMenu({ open, onClose }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const query = new URLSearchParams(search)
  const [pending, setPending] = useState(0)

  // 교수: 메뉴를 열 때 처리할 출석 인정 신청 수를 받아 온다
  useEffect(() => {
    if (!open || user?.role !== 'ADMIN') return
    get('/requests/pending-count').then((r) => setPending(r.pending)).catch(() => {})
  }, [open, user?.role])

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
  const showCount = (it) => it.count && user.role === 'ADMIN' && pending > 0
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
                {showCount(it) && <span className="menu-count" aria-label={`처리할 신청 ${pending}건`}>{pending}</span>}
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
