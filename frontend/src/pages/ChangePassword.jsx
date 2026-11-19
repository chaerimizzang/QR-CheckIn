
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../api'
import { homePath, useAuth } from '../auth'

export default function ChangePassword() {
  const navigate = useNavigate()
  const { user } = useAuth()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (newPassword.length < 8) {
      setError('새 비밀번호는 8자 이상이어야 합니다.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.')
      return
    }

    setLoading(true)

    try {
      await post('/me/password', {
        currentPassword,
        newPassword,
        confirmPassword,
      })

      alert('비밀번호가 변경되었습니다.')
      navigate(homePath(user))
    } catch (err) {
      setError(err.message || '비밀번호 변경에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className="card" style={{
        maxWidth: 480,
        margin: '40px auto',
        padding: 24
      }}>
        <h2>비밀번호 변경</h2>

        <form onSubmit={handleSubmit}>
          <label>현재 비밀번호</label>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
          />

          <label>새 비밀번호</label>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />

          <label>새 비밀번호 확인</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            autoComplete="new-password"
          />

          {error && (
            <p style={{ color: '#dc2626' }}>{error}</p>
          )}

          <button type="submit" disabled={loading}>
            {loading ? '변경 중...' : '비밀번호 변경하기'}
          </button>

          <button
            type="button"
            onClick={() => navigate(homePath(user))}
            style={{ marginLeft: 8 }}
          >
            취소
          </button>
        </form>
      </div>
    </div>
  )
}
