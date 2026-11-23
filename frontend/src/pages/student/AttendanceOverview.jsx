import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, HeroTiles, Layout, Loading } from '../../components/ui'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'
import { WARNING, newestSemesterFirst } from '../../util'
import Icon from '../../components/Icon'

/** 남은 결석으로 보여 줄 짧은 표시 */
function levelChip(c) {
  if (c.remainingAbsences == null || c.warningLevel === 'NONE') return null
  if (c.warningLevel === 'FAIL') return { cls: 'level-fail', text: 'F 기준 초과' }
  const left = `결석 ${Math.max(c.remainingAbsences, 0)}회 남음`
  if (c.warningLevel === 'DANGER') return { cls: 'level-danger', text: `위험 · ${left}` }
  if (c.warningLevel === 'WARNING') return { cls: 'level-warning', text: `주의 · ${left}` }
  return { cls: 'level-safe', text: left }
}

/** 출결 현황: 수강 중인 수업별 출석·지각·결석과 출석률. 수업을 누르면 날짜별 출결(S-04)로 */
export default function AttendanceOverview() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const sem = useSemester()
  useEffect(() => { get('/me/profile').then(setData).catch(setError) }, [])

  const shown = data ? sem.filter([...data.courses].sort((a, b) => newestSemesterFirst(a.semesterKey, b.semesterKey))) : []
  const rated = shown.filter((c) => c.attendanceRate != null)
  const avg = rated.length ? Math.round(rated.reduce((n, c) => n + c.attendanceRate, 0) / rated.length) : null
  const risky = shown.filter((c) => ['WARNING', 'DANGER', 'FAIL'].includes(c.warningLevel)).length

  const hero = data && (
    <HeroTiles items={[
      { label: '듣는 수업', value: `${shown.length}개` },
      { label: '평균 출석률', value: avg == null ? '—' : `${avg}%` },
      { label: '주의할 수업', value: `${risky}개`, tone: risky > 0 ? 'warn' : undefined },
    ]} />
  )

  return (
    <Layout back="/student" title="출결 현황" hero={hero}>
      <ErrorBox error={error} />
      <SemesterPicker sem={sem} />
      {!data ? (!error && <Loading />) : (
        <>
          {shown.length === 0 ? (
            <Empty>{data.courses.length === 0 ? '아직 수강 중인 수업이 없어요. 교수님께 가입한 이메일을 알려 드리고 등록을 요청해 주세요.' : '이 학기에는 듣는 수업이 없어요.'}</Empty>
          ) : (
            <div className="list">
              {shown.map((c, i, arr) => {
                const newGroup = sem.value === 'ALL' && (i === 0 || arr[i - 1].semesterLabel !== c.semesterLabel)
                const chip = levelChip(c)
                return (
                  <Fragment key={c.courseId}>
                  {newGroup && <div className="semester-head">{c.semesterLabel}</div>}
                  <Link to={`/student/courses/${c.courseId}?from=attendance`} className="card rate-card">
                    <div className="rate-head">
                      <div>
                        <div className="item-title">{c.title}{c.finalized && <span className="badge badge-final">출결 확정</span>}</div>
                        <div className="muted small">{c.ownerName}</div>
                      </div>
                      {chip && <span className={`level-chip ${chip.cls}`}>{chip.text}</span>}
                    </div>
                    {c.totalSessions === 0 ? (
                      <div className="rate-foot small"><span className="muted">아직 마감된 수업이 없어요</span>
                        <Icon name="chevron" size={20} className="chevron" /></div>
                    ) : (
                      <>
                        <div className="rate-bar">
                          <div className="track"><div className="fill" style={{ width: `${Math.min(100, c.attendanceRate ?? 0)}%` }} /></div>
                          <b>{c.attendanceRate == null ? '—' : `${c.attendanceRate}%`}</b>
                        </div>
                        <div className="rate-foot small">
                          <span className="muted">출석 {c.presentCount} · 지각 {c.lateCount} · 결석 {c.absentCount}</span>
                          <Icon name="chevron" size={20} className="chevron" />
                        </div>
                      </>
                    )}
                    {c.warningLevel === 'FAIL' && WARNING.FAIL && (
                      <div className={`warn-chip ${WARNING.FAIL.cls}`}><b>{WARNING.FAIL.label}</b> {WARNING.FAIL.text}</div>
                    )}
                  </Link>
                  </Fragment>
                )
              })}
            </div>
          )}
          <Link to="/assistant" className="btn btn-block" style={{ marginTop: 16 }}>
            <Icon name="sparkles-active" size={18} />AI 출결 도우미에게 물어보기
          </Link>
        </>
      )}
    </Layout>
  )
}
