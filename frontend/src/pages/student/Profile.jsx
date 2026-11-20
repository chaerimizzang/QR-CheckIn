import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'

/** 학생 프로필: 내 정보 + 수강 중인 수업별 출석률. 수업을 누르면 회차별 출결(S-04)로 */
export default function StudentProfile() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/me/profile').then(setData).catch(setError) }, [])

  return (
    <Layout back="/student" title="프로필">
      <ErrorBox error={error} />
      {!data ? (!error && <Loading />) : (
        <>
          <div className="card profile-card">
            <div className="profile-avatar" aria-hidden="true">{data.name?.slice(0, 1)}</div>
            <div className="profile-info">
              <div className="profile-name">{data.name}</div>
              <div className="muted small">
                {data.studentNo ? `학번 ${data.studentNo}` : '학번 미입력'} · {data.email}
              </div>
            </div>
          </div>

          <h2 className="section-title">수강 중인 수업 <span className="muted small">{data.courses.length}개</span></h2>
          {data.courses.length === 0 ? (
            <Empty>아직 수강 중인 수업이 없어요. 강사님께 가입한 이메일을 알려 드리고 등록을 요청해 주세요.</Empty>
          ) : (
            <div className="list">
              {data.courses.map((c) => {
                return (
                  <Link key={c.courseId} to={`/student/courses/${c.courseId}?from=profile`} className="card rate-card">
                    <div className="rate-head">
                      <div>
                        <div className="item-title">{c.title}</div>
                        <div className="muted small">{c.ownerName}</div>
                      </div>
                      <div className={`rate-value${c.attendanceRate == null ? ' none' : ''}`}>
                        {c.attendanceRate == null ? '—' : `${c.attendanceRate}%`}
                      </div>
                    </div>
                    <div className="rate-bar" aria-hidden="true">
                      <span style={{ width: `${c.attendanceRate ?? 0}%` }} />
                    </div>
                    <div className="rate-foot small">
                      {c.totalSessions === 0 ? (
                        <span className="muted">아직 마감된 회차가 없어요</span>
                      ) : (
                        <span>
                          출석 <b>{c.presentCount}</b> · 지각 <b>{c.lateCount}</b> · 결석 <b>{c.absentCount}</b>
                          <span className="muted"> / {c.totalSessions}회</span>
                          {c.convertedAbsences > c.absentCount && (
                            <span className="muted"> · 인정 결석 {c.convertedAbsences}</span>
                          )}
                        </span>
                      )}
                      <Icon name="chevron" size={20} className="chevron" />
                    </div>
                  </Link>
                )
              })}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}
