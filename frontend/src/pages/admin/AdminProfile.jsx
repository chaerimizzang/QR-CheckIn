import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import Icon from '../../components/Icon'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'

/** 관리자 프로필: 내 정보 + 개설한 교육 요약. 교육을 누르면 교육 화면으로 */
export default function AdminProfile() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/me/admin-profile').then(setData).catch(setError) }, [])

  return (
    <Layout back="/admin" title="프로필">
      <ErrorBox error={error} />
      {!data ? (!error && <Loading />) : (
        <>
          <div className="card profile-card">
            <div className="profile-avatar" aria-hidden="true">{data.name?.slice(0, 1)}</div>
            <div className="profile-info">
              <div className="profile-name">{data.name}</div>
              <div className="muted small">관리자 · {data.email}</div>
            </div>
          </div>

          <div className="stat-row">
            <div className="card stat"><div className="stat-num">{data.courseCount}</div><div className="muted small">개설한 교육</div></div>
            <div className="card stat"><div className="stat-num">{data.studentCount}</div><div className="muted small">전체 수강생</div></div>
          </div>

          <h2 className="section-title">개설한 교육</h2>
          {data.courses.length === 0 ? (
            <Empty>아직 만든 교육이 없어요.</Empty>
          ) : (
            <div className="list">
              {data.courses.map((c) => (
                <Link key={c.courseId} to={`/admin/courses/${c.courseId}`} className="card list-item">
                  <div>
                    <div className="item-title">{c.title}</div>
                    <div className="muted small">
                      수강생 {c.studentCount}명 · 회차 {c.sessionCount}개(마감 {c.closedSessionCount})
                    </div>
                  </div>
                  <Icon name="chevron" size={20} className="chevron" />
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}
