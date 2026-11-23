import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import { SESSION_LABEL, STATUS_LABEL, WARNING, fmtDateTime } from '../../util'

/** 출석 인정을 신청할 수 있는 회차: 결석·지각이거나, 아직 열리지 않은(미리 신청) 회차 */
/** 날짜가 지났는데 출석을 열지 않은 회차(진행하지 않은 수업) */
const neverHeld = (s) => s.sessionStatus === 'SCHEDULED' && new Date(s.scheduledAt) < new Date()
const canceled = (s) => s.sessionStatus === 'CANCELED'
const canRequest = (s) => !canceled(s) &&
  (s.status === 'ABSENT' || s.status === 'LATE' || (!s.status && s.sessionStatus !== 'OPEN' && !neverHeld(s)))

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

  const count = (st) => data?.sessions.filter((s) => !canceled(s) && s.status === st).length ?? 0

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
          </div>
          {data.sessions.length === 0 ? <Empty>아직 회차가 없어요.</Empty> : (
            <div className="list">
              {[...data.sessions].sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt)).map((s) => (
                <div key={s.sessionId} className="card list-item">
                  <div>
                    <div className="item-title">{s.sessionNo}회차</div>
                    <div className="muted small">
                      {fmtDateTime(s.scheduledAt)}{canceled(s) && s.cancelReason && ` · ${s.cancelReason}`}
                    </div>
                  </div>
                  <div className="att-right">
                    {canRequest(s) && (
                      <Link className="link-btn small" to={`/requests?new=1&course=${courseId}&session=${s.sessionId}`}>
                        출석 인정 신청
                      </Link>
                    )}
                    {s.status && !canceled(s)
                      ? <span className={`badge badge-lg badge-${s.status.toLowerCase()}`}>{STATUS_LABEL[s.status]}</span>
                      : <span className={`badge badge-lg badge-s-${s.sessionStatus.toLowerCase()}`}>{neverHeld(s) ? '미진행' : SESSION_LABEL[s.sessionStatus]}</span>}
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
