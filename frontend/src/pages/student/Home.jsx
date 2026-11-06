import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'

/** S-02 수강생 홈 */
export default function StudentHome() {
  const [courses, setCourses] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/courses').then(setCourses).catch(setError) }, [])

  return (
    <Layout>
      <Link to="/scan" className="scan-hero">
        <span className="scan-icon">▣</span>
        <span>QR 스캔해서 출석하기</span>
      </Link>
      <h2 className="section-title">내 교육</h2>
      <ErrorBox error={error} />
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 등록된 교육이 없어요. 강사님께 가입한 이메일을 알려 드리고 수강생 등록을 요청해 주세요.</Empty>
      ) : (
        <div className="list">
          {courses.map((c) => (
            <Link key={c.id} to={`/student/courses/${c.id}`} className="card list-item">
              <div>
                <div className="item-title">{c.title}</div>
                <div className="muted small">{c.ownerName} · 내 출결 보기</div>
              </div>
              <span className="chevron">›</span>
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
