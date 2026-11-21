import { useEffect, useMemo, useState } from 'react'
import { get, post } from '../../api'
import RequestCard from '../../components/RequestCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'

/** 교수: 출석 인정 신청 — 목록은 간단히, 누르면 내용 확인 후 승인·반려 */
export default function AdminRequests() {
  const [list, setList] = useState(null)
  const [tab, setTab] = useState('PENDING')
  const [course, setCourse] = useState('')
  const [openId, setOpenId] = useState(null)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const load = () => get('/requests').then(setList).catch(setError)
  useEffect(() => { load() }, [])

  const courses = useMemo(() => {
    const m = new Map()
    list?.forEach((r) => m.set(r.courseId, r.courseTitle))
    return [...m.entries()]
  }, [list])

  const shown = (list ?? []).filter((r) =>
    (tab === 'PENDING' ? r.status === 'PENDING' : r.status !== 'PENDING') && (!course || String(r.courseId) === course))
  const pendingCount = list?.filter((r) => r.status === 'PENDING').length ?? 0

  function toggle(id) {
    setOpenId(openId === id ? null : id)
    setComment('')
    setError(null)
  }

  async function review(r, action) {
    if (action === 'reject' && !comment.trim()) {
      setError({ message: '반려 사유를 적어 주세요' })
      return
    }
    setBusy(true); setError(null)
    try {
      const updated = await post(`/requests/${r.id}/${action}`, { comment: comment.trim() || null })
      setList((prev) => prev.map((x) => (x.id === r.id ? updated : x)))
      setOpenId(null); setComment('')
    } catch (err) { setError(err); load() } finally { setBusy(false) }
  }

  return (
    <Layout title="출석 인정">
      <ErrorBox error={error} />
      <div className="tabs">
        <button className={tab === 'PENDING' ? 'on' : ''} onClick={() => setTab('PENDING')}>대기{pendingCount > 0 && ` ${pendingCount}`}</button>
        <button className={tab === 'DONE' ? 'on' : ''} onClick={() => setTab('DONE')}>처리 완료</button>
      </div>
      {courses.length > 1 && (
        <select className="input req-filter" value={course} onChange={(e) => setCourse(e.target.value)} aria-label="수업">
          <option value="">전체 수업</option>
          {courses.map(([id, title]) => <option key={id} value={id}>{title}</option>)}
        </select>
      )}

      {!list ? <Loading /> : shown.length === 0 ? (
        <Empty>{tab === 'PENDING' ? '처리할 신청이 없어요' : '처리한 신청이 없어요'}</Empty>
      ) : (
        <div className="list">
          {shown.map((r) => (
            <RequestCard key={r.id} r={r} showStudent open={openId === r.id} onToggle={() => toggle(r.id)} onError={setError}>
              {r.status === 'PENDING' && (
                <div className="req-review-form">
                  <input placeholder="의견 (반려할 때 필수)" maxLength={300} value={comment}
                    onChange={(e) => setComment(e.target.value)} disabled={busy} />
                  <div className="req-actions">
                    <button className="btn btn-primary" disabled={busy} onClick={() => review(r, 'approve')}>승인</button>
                    <button className="btn" disabled={busy} onClick={() => review(r, 'reject')}>반려</button>
                  </div>
                </div>
              )}
            </RequestCard>
          ))}
        </div>
      )}
    </Layout>
  )
}
