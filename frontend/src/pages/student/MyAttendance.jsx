import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, HeroStats, Layout, Loading } from '../../components/ui'
import { SESSION_LABEL, STATUS_LABEL, WARNING } from '../../util'

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

  const rate = warning?.attendanceRate
  const level = warning?.warningLevel
  const allowed = warning?.remainingAbsences != null ? warning.remainingAbsences + warning.convertedAbsences : null
  const chipText = level === 'FAIL' ? 'F 기준 초과'
    : warning?.remainingAbsences == null ? null
    : `${level === 'DANGER' ? '위험 · ' : level === 'WARNING' ? '주의 · ' : ''}결석 ${Math.max(warning.remainingAbsences, 0)}회 남음`

  const hero = data && (
    <>
      <div className="ring-row">
        <div className="ring" style={{ background: `conic-gradient(#5b8cff 0 ${rate ?? 0}%, var(--navy-line) ${rate ?? 0}% 100%)` }}>
          <div className="ring-in"><b>{rate == null ? '—' : `${Math.round(rate)}%`}</b><span>출석률</span></div>
        </div>
        <div className="ring-side">
          {chipText && <span className={`hero-chip level-${(level ?? 'none').toLowerCase()}`}>{chipText}</span>}
          {allowed != null && <p>휴강을 뺀 전체 수업 중<br />결석 {allowed}회까지 괜찮아요</p>}
        </div>
      </div>
      <HeroStats items={[
        { label: '출석', value: count('PRESENT') },
        { label: '지각', value: count('LATE') },
        { label: '결석', value: count('ABSENT') },
      ]} />
      <p className="hero-note">지각 3번은 결석 1번으로 계산돼요</p>
    </>
  )

  const md = (iso) => { const d = new Date(iso); return { m: `${d.getMonth() + 1}월`, d: d.getDate() } }
  const hm = (iso) => { const d = new Date(iso); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }
  const wd = (iso) => '일월화수목금토'[new Date(iso).getDay()]

  return (
    <Layout back={back} title={data?.courseTitle} hero={hero}>
      <ErrorBox error={error} />
      {!data ? <Loading /> : (
        <>
          {level === 'FAIL' && WARNING.FAIL && (
            <div className={`warn-chip warn-banner ${WARNING.FAIL.cls}`}>
              <b>{WARNING.FAIL.label}</b> {WARNING.FAIL.text}
              <span className="small"> · 사유가 있었다면 [출석 인정 신청]을 해 보세요</span>
            </div>
          )}
          <div className="list-head"><h2>날짜별 출결</h2><span className="muted small">날짜순</span></div>
          {data.sessions.length === 0 ? <Empty>아직 수업 날짜가 없어요.</Empty> : (
            <div className="list">
              {[...data.sessions].sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt)).map((s) => (
                <div key={s.sessionId} className="card date-row">
                  <div className="date-box"><span>{md(s.scheduledAt).m}</span><b>{md(s.scheduledAt).d}</b></div>
                  <div className="date-main">
                    <div className="t">{wd(s.scheduledAt)} {hm(s.scheduledAt)}</div>
                    {canRequest(s) ? (
                      <Link className="small" to={`/requests?new=1&course=${courseId}&session=${s.sessionId}`}>출석 인정 신청</Link>
                    ) : (
                      <div className="muted small">
                        {canceled(s) ? (s.cancelReason || '휴강')
                          : s.checkedAt ? `${hm(s.checkedAt)} 체크인`
                          : s.status ? '체크인 없음' : ''}
                      </div>
                    )}
                  </div>
                  {s.status && !canceled(s)
                    ? <span className={`badge badge-lg badge-${s.status.toLowerCase()}`}>{STATUS_LABEL[s.status]}</span>
                    : <span className={`badge badge-lg badge-s-${s.sessionStatus.toLowerCase()}`}>{neverHeld(s) ? '미진행' : SESSION_LABEL[s.sessionStatus]}</span>}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}
