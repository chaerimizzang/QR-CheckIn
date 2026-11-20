import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import AccountCard from '../../components/AccountCard'
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

          <AccountCard name={data.name} email={data.email} onNameChange={(name) => setData({ ...data, name })} />

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
                      <div className="rate-value">
                        <span className="muted">출석률</span> {c.attendanceRate == null ? '—' : `${c.attendanceRate}%`}
                      </div>
                    </div>
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
                            마감 {c.totalSessions}회{c.convertedAbsences > c.absentCount && ` · 인정 결석 ${c.convertedAbsences}`}
                          </span>
                          <Icon name="chevron" size={20} className="chevron" />
                        </div>
                      </>
                    )}
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
