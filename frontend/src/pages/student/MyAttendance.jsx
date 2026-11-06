import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading, SessionBadge, StatusBadge } from '../../components/ui'
import { fmtDateTime, fmtTime } from '../../util'

/** S-04 내 출결 */
export default function MyAttendance() {
  const { courseId } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get(`/me/attendances?courseId=${courseId}`).then(setData).catch(setError) }, [courseId])

  const count = (st) => data?.sessions.filter((s) => s.status === st).length ?? 0

  return (
    <Layout back="/student" title={data?.courseTitle}>
      <ErrorBox error={error} />
      {!data ? <Loading /> : (
        <>
          <div className="summary card">
            <span>출석 <b>{count('PRESENT')}</b></span>
            <span>지각 <b>{count('LATE')}</b></span>
            <span>결석 <b>{count('ABSENT')}</b></span>
          </div>
          {data.sessions.length === 0 ? <Empty>아직 회차가 없어요.</Empty> : (
            <div className="list">
              {data.sessions.map((s) => (
                <div key={s.sessionId} className="card list-item">
                  <div>
                    <div className="item-title">{s.sessionNo}회차</div>
                    <div className="muted small">{fmtDateTime(s.scheduledAt)}</div>
                  </div>
                  <div className="right">
                    {s.status ? <StatusBadge status={s.status} /> : <SessionBadge status={s.sessionStatus} />}
                    {s.checkedAt && <div className="muted small">{fmtTime(s.checkedAt)}</div>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}
