import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading, SessionBadge, StatusBadge } from '../../components/ui'
import { WARNING, fmtDateTime, fmtTime } from '../../util'

/** 출석 인정을 신청할 수 있는 회차: 결석·지각이거나, 아직 열리지 않은(미리 공결) 회차 */
const canRequest = (s) => s.status === 'ABSENT' || s.status === 'LATE' || (!s.status && s.sessionStatus !== 'OPEN')

/** S-04 내 출결 */
export default function MyAttendance() {
  const { courseId } = useParams()
  const [params] = useSearchParams()
  const from = params.get('from')
  const back = from === 'attendance' || from === 'profile' ? '/attendance' : '/student'
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [warning, setWarning] = useState(null)
  useEffect(() => { get(`/me/attendances?courseId=${courseId}`).then(setData).catch(setError) }, [courseId])
  // 결석 경고(F 기준) — 프로필의 수업별 요약에서 이 수업 것만 쓴다
  useEffect(() => {
    get('/me/profile').then((p) => setWarning(p.courses.find((c) => String(c.courseId) === String(courseId)) ?? null))
      .catch(() => {})
  }, [courseId])

  const count = (st) => data?.sessions.filter((s) => s.status === st).length ?? 0

  return (
    <Layout back={back} title={data?.courseTitle}>
      <ErrorBox error={error} />
      {!data ? <Loading /> : (
        <>
          {WARNING[warning?.warningLevel] && (
            <div className={`warn-chip warn-banner ${WARNING[warning.warningLevel].cls}`}>
              <b>{WARNING[warning.warningLevel].label}</b> {WARNING[warning.warningLevel].text}
              <span className="small"> · 인정 결석 {warning.convertedAbsences}회. 사유가 있었다면 [출석 인정 신청]을 해 보세요</span>
            </div>
          )}
          <div className="summary card">
            <span>출석 <b>{count('PRESENT')}</b></span>
            <span>지각 <b>{count('LATE')}</b></span>
            <span>결석 <b>{count('ABSENT')}</b></span>
            {count('EXCUSED') > 0 && <span>공결 <b>{count('EXCUSED')}</b></span>}
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
                    {canRequest(s) && (
                      <Link className="link-btn small" to={`/requests?new=1&course=${courseId}&session=${s.sessionId}`}>
                        출석 인정 신청
                      </Link>
                    )}
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
