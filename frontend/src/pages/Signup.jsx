import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { post } from '../api'
import { homePath, useAuth } from '../auth'
import { ErrorBox } from '../components/ui'

export default function Signup() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next')
  const [form, setForm] = useState({ email: '', password: '', name: '', studentNo: '', role: 'STUDENT' })
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await post('/auth/signup', form)
      const u = await login(form.email, form.password) // 가입 후 바로 로그인
      navigate(next || homePath(u), { replace: true })
    } catch (err) {
      const fields = err.detail?.fields
      if (fields) err.detail.hint = Object.values(fields).join(' · ')
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <form className="card auth-card" onSubmit={submit}>
        <div className="auth-logo">회원가입</div>
        <ErrorBox error={error} />
        <div className="segmented">
          <button type="button" className={form.role === 'STUDENT' ? 'on' : ''} onClick={() => setForm({ ...form, role: 'STUDENT' })}>수강생</button>
          <button type="button" className={form.role === 'ADMIN' ? 'on' : ''} onClick={() => setForm({ ...form, role: 'ADMIN' })}>관리자(강사)</button>
        </div>
        <label>
          이름
          <input value={form.name} onChange={set('name')} required maxLength={50} />
        </label>
        {form.role === 'STUDENT' && (
          <label>
            학번
            <input value={form.studentNo} onChange={set('studentNo')} maxLength={30} inputMode="numeric" />
          </label>
        )}
        <label>
          이메일
          <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
        </label>
        <label>
          비밀번호 <span className="muted small">(8자 이상)</span>
          <input type="password" value={form.password} onChange={set('password')} minLength={8} autoComplete="new-password" required />
        </label>
        {/* 계획서 9.3 개인정보(위치정보) 고지·동의 */}
        <label className="check">
          <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
          <span className="small">
            출결 확인을 위해 이름·학번·이메일과 <b>출석 인증 순간의 위치</b>를 수집하는 데 동의합니다.
            위치는 스캔할 때 한 번만 쓰고 저장하지 않아요.
          </span>
        </label>
        <button className="btn btn-primary btn-block" disabled={busy || !agree}>{busy ? '가입 중…' : '가입하기'}</button>
        <p className="center small">
          이미 계정이 있나요? <Link to={`/login${next ? `?next=${encodeURIComponent(next)}` : ''}`}>로그인</Link>
        </p>
      </form>
    </div>
  )
}
