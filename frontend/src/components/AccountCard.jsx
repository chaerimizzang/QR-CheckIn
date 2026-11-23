import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { post, put } from '../api'
import { useAuth } from '../auth'
import { ErrorBox } from './ui'

/** 프로필의 [계정] 카드: 이름·학과 변경 · 비밀번호 변경 */
export default function AccountCard({ name, email, department, onNameChange, onDepartmentChange }) {
  const { user, updateUser } = useAuth()
  const [params, setParams] = useSearchParams()
  const [editingName, setEditingName] = useState(false)
  const [pwOpen, setPwOpen] = useState(params.get('password') === '1')
  const [nameInput, setNameInput] = useState(name)
  const [editingDept, setEditingDept] = useState(false)
  const [deptInput, setDeptInput] = useState(department || '')
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [error, setError] = useState(null)
  const [done, setDone] = useState('')
  const [busy, setBusy] = useState(false)

  function startName() {
    setNameInput(name); setEditingName(true); setError(null); setDone('')
  }

  async function saveName(e) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      const me = await put('/me/name', { name: nameInput })
      updateUser({ ...user, name: me.name })
      onNameChange?.(me.name)
      setEditingName(false)
      setDone('이름을 바꿨어요')
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  function startDept() {
    setDeptInput(department || ''); setEditingDept(true); setError(null); setDone('')
  }

  async function saveDept(e) {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      const me = await put('/me/department', { department: deptInput })
      updateUser({ ...user, department: me.department })
      onDepartmentChange?.(me.department)
      setEditingDept(false)
      setDone(me.department ? '학과를 바꿨어요' : '학과를 지웠어요')
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  function togglePw() {
    const next = !pwOpen
    setPwOpen(next); setError(null); setDone('')
    setPw({ currentPassword: '', newPassword: '', confirmPassword: '' })
    if (!next && params.get('password')) { params.delete('password'); setParams(params, { replace: true }) }
  }

  async function savePw(e) {
    e.preventDefault()
    setError(null)
    if (pw.newPassword !== pw.confirmPassword) {
      setError({ message: '새 비밀번호 두 칸이 서로 달라요' }); return
    }
    setBusy(true)
    try {
      await post('/me/password', pw)
      setPwOpen(false)
      setPw({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setDone('비밀번호를 바꿨어요. 다음 로그인부터 새 비밀번호를 쓰세요')
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  const set = (k) => (e) => setPw({ ...pw, [k]: e.target.value })

  return (
    <div className="card account-card">
      <h2 className="account-title">계정</h2>
      {done && <div className="alert alert-ok">{done}</div>}
      <ErrorBox error={error} />

      <div className="account-rows">
      <div className="account-row">
        <div className="account-label">이름</div>
        {editingName ? (
          <form className="account-edit" onSubmit={saveName}>
            <input value={nameInput} onChange={(e) => setNameInput(e.target.value)} maxLength={50} autoFocus
              aria-label="새 이름" disabled={busy} />
            <div className="btn-row">
              <button className="btn btn-primary btn-sm" disabled={busy || !nameInput.trim() || nameInput.trim() === name}>저장</button>
              <button type="button" className="btn btn-sm" onClick={() => setEditingName(false)} disabled={busy}>취소</button>
            </div>
          </form>
        ) : (
          <>
            <div className="account-value">{name}</div>
            <button type="button" className="link-btn small" onClick={startName}>변경</button>
          </>
        )}
      </div>

      <div className="account-row">
        <div className="account-label">학과</div>
        {editingDept ? (
          <form className="account-edit" onSubmit={saveDept}>
            <input value={deptInput} onChange={(e) => setDeptInput(e.target.value)} maxLength={50} autoFocus
              aria-label="학과" placeholder="예: 컴퓨터인공지능공학과" disabled={busy} />
            <div className="btn-row">
              <button className="btn btn-primary btn-sm" disabled={busy || deptInput.trim() === (department || '')}>저장</button>
              <button type="button" className="btn btn-sm" onClick={() => setEditingDept(false)} disabled={busy}>취소</button>
            </div>
          </form>
        ) : (
          <>
            <div className={`account-value${department ? '' : ' muted'}`}>{department || '미입력'}</div>
            <button type="button" className="link-btn small" onClick={startDept}>변경</button>
          </>
        )}
      </div>

      <div className="account-row">
        <div className="account-label">이메일</div>
        <div className="account-value muted">{email}</div>
      </div>

      <div className="account-row">
        <div className="account-label">비밀번호</div>
        <div className="account-value muted">••••••••</div>
        <button type="button" className="link-btn small" onClick={togglePw}>{pwOpen ? '닫기' : '변경'}</button>
      </div>
      </div>

      {pwOpen && (
        <form className="account-pw" onSubmit={savePw}>
          <label>현재 비밀번호
            <input type="password" value={pw.currentPassword} onChange={set('currentPassword')} required
              autoComplete="current-password" disabled={busy} />
          </label>
          <label>새 비밀번호
            <input type="password" value={pw.newPassword} onChange={set('newPassword')} required minLength={8} maxLength={64} placeholder="8자 이상"
              autoComplete="new-password" disabled={busy} />
          </label>
          <label>새 비밀번호 확인
            <input type="password" value={pw.confirmPassword} onChange={set('confirmPassword')} required
              autoComplete="new-password" disabled={busy} />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? '바꾸는 중…' : '비밀번호 바꾸기'}</button>
        </form>
      )}
    </div>
  )
}
