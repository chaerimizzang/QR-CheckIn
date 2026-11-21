import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'
import { WARNING, newestSemesterFirst } from '../../util'
import Icon from '../../components/Icon'

/** 출결 현황: 수강 중인 수업별 출석·지각·결석과 출석률. 수업을 누르면 날짜별 출결(S-04)로 */
export default function AttendanceOverview() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const sem = useSemester()
  useEffect(() => { get('/me/profile').then(setData).catch(setError) }, [])

  const shown = data ? sem.filter([...data.courses].sort((a, b) => newestSemesterFirst(a.semesterKey, b.semesterKey))) : []

  return (
    <Layout back="/student" title="출결 현황">
      <ErrorBox error={error} />
      <SemesterPicker sem={sem} />
      {!data ? (!error && <Loading />) : (
        <>
          {shown.length === 0 ? (
            <Empty>{data.courses.length === 0 ? '아직 수강 중인 수업이 없어요. 강사님께 가입한 이메일을 알려 드리고 등록을 요청해 주세요.' : '이 학기에는 듣는 수업이 없어요.'}</Empty>
          ) : (
            <div className="list">
              {shown.map((c, i, arr) => {
                const newGroup = sem.value === 'ALL' && (i === 0 || arr[i - 1].semesterLabel !== c.semesterLabel)
                return (
                  <Fragment key={c.courseId}>
                  {newGroup && <div className="semester-head">{c.semesterLabel}</div>}
                  <Link key={c.courseId} to={`/student/courses/${c.courseId}?from=attendance`} className="card rate-card">
                    <div className="rate-head">
                      <div>
                        <div className="item-title">{c.title}{c.finalized && <span className="badge badge-final">출결 확정</span>}</div>
                        <div className="muted small">{c.ownerName}</div>
                      </div>
                      <div className="rate-value">
                        <span className="muted">출석률</span> {c.attendanceRate == null ? '—' : `${c.attendanceRate}%`}
                      </div>
                    </div>
                    {WARNING[c.warningLevel] && (
                      <div className={`warn-chip ${WARNING[c.warningLevel].cls}`}>
                        <b>{WARNING[c.warningLevel].label}</b> {WARNING[c.warningLevel].text}
                      </div>
                    )}
                    {c.totalSessions === 0 ? (
                      <div className="rate-foot small"><span className="muted">아직 마감된 회차가 없어요</span>
                        <Icon name="chevron" size={20} className="chevron" /></div>
                    ) : (
                      <>
                        <div className="count-row">
                          <div className="count-box"><b>{c.presentCount}</b><span>출석</span></div>
                          <div className="count-box"><b>{c.lateCount}</b><span>지각</span></div>
                          <div className="count-box"><b>{c.absentCount}</b><span>결석</span></div>
                        </div>
                        <div className="rate-foot small">
                          <span className="muted">
                            마감 {c.totalSessions}회{c.excusedCount > 0 && ` · 공결 ${c.excusedCount}`}{c.convertedAbsences > c.absentCount && ` · 인정 결석 ${c.convertedAbsences}`}
                          </span>
                          <Icon name="chevron" size={20} className="chevron" />
                        </div>
                      </>
                    )}
                  </Link>
                  </Fragment>
                )
              })}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}
