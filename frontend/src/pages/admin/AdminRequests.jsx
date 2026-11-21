import { useEffect, useMemo, useState } from 'react'
import { get, post } from '../../api'
import RequestCard from '../../components/RequestCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'

/** 교수: 내 교육들에 들어온 출석 인정 신청 승인·반려 */
export default function AdminRequests() {
  const [list, setList] = useState(null)
  const [tab, setTab] = useState('PENDING')
  const [course, setCourse] = useState('')
  const [comments, setComments] = useState({})
  const [busyId, setBusyId] = useState(null)
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

  async function review(r, action) {
    const comment = (comments[r.id] ?? '').trim()
    if (action === 'reject' && !comment) {
      setError({ message: '반려할 때는 사유를 적어 주세요(학생에게 보여요)' })
      return
    }
    setBusyId(r.id); setError(null)
    try {
      const updated = await post(`/requests/${r.id}/${action}`, { comment: comment || null })
      setList((prev) => prev.map((x) => (x.id === r.id ? updated : x)))
      setComments((c) => ({ ...c, [r.id]: '' }))
    } catch (err) { setError(err); load() } finally { setBusyId(null) }
  }

  return (
    <Layout title="출석 인정">
      <ErrorBox error={error} />
      <div className="tabs">
        <button className={tab === 'PENDING' ? 'on' : ''} onClick={() => setTab('PENDING')}>대기 {pendingCount > 0 && `(${pendingCount})`}</button>
        <button className={tab === 'DONE' ? 'on' : ''} onClick={() => setTab('DONE')}>처리 완료</button>
      </div>
      {courses.length > 1 && (
        <label>수업
          <select className="input" value={course} onChange={(e) => setCourse(e.target.value)}>
            <option value="">전체</option>
            {courses.map(([id, title]) => <option key={id} value={id}>{title}</option>)}
          </select>
        </label>
      )}

      {!list ? <Loading /> : shown.length === 0 ? (
        <Empty>{tab === 'PENDING' ? '처리할 신청이 없어요' : '처리한 신청이 없어요'}</Empty>
      ) : (
        <div className="list">
          {shown.map((r) => (
            <RequestCard key={r.id} r={r} showStudent onError={setError}>
              {r.status === 'PENDING' && (
                <div className="req-actions">
                  <input placeholder="의견(반려할 때는 필수)" maxLength={300} value={comments[r.id] ?? ''}
                    onChange={(e) => setComments({ ...comments, [r.id]: e.target.value })} disabled={busyId === r.id} />
                  <button className="btn btn-primary btn-sm" disabled={busyId === r.id} onClick={() => review(r, 'approve')}>승인</button>
                  <button className="btn btn-sm" disabled={busyId === r.id} onClick={() => review(r, 'reject')}>반려</button>
                </div>
              )}
            </RequestCard>
          ))}
        </div>
      )}
    </Layout>
  )
}
