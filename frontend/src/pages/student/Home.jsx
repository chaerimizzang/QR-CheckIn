import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import InstallPrompt from '../../components/InstallPrompt'
import { PushBanner } from '../../components/PushCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'
import { fmtSchedules } from '../../util'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'

/** S-02 수강생 홈 */
export default function StudentHome() {
  const [courses, setCourses] = useState(null)
  const [error, setError] = useState(null)
  const sem = useSemester()
  useEffect(() => { get('/courses').then(setCourses).catch(setError) }, [])

  return (
    <Layout>
      <InstallPrompt />
      <PushBanner />
      <Link to="/scan" className="scan-hero">
        <Icon name="scan-white" size={44} className="scan-icon" />
        <span>QR 스캔해서 출석하기</span>
      </Link>
      <div className="section-row">
        <h2 className="section-title">내 교육</h2>
        <Link to="/attendance" className="small link-ico">내 출석률 보기 <Icon name="chevron-primary" size={14} /></Link>
      </div>
      <ErrorBox error={error} />
      <SemesterPicker sem={sem} />
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 등록된 교육이 없어요. 강사님께 가입한 이메일을 알려 드리고 수강생 등록을 요청해 주세요.</Empty>
      ) : sem.filter(courses).length === 0 ? (
        <Empty>이 학기에는 듣는 교육이 없어요. 위에서 다른 학기를 골라 보세요.</Empty>
      ) : (
        <div className="list">
          {sem.filter(courses).map((c) => (
            <Link key={c.id} to={`/student/courses/${c.id}`} className="card list-item">
              <div>
                <div className="item-title">{c.title}{c.finalizedAt && <span className="badge badge-final">출결 확정</span>}</div>
                <div className="muted small">{sem.value === 'ALL' && `${c.semesterLabel} · `}{c.ownerName}{fmtSchedules(c.schedules) && ` · ${fmtSchedules(c.schedules)}`}{c.room && ` · ${c.room}`}</div>
              </div>
              <Icon name="chevron" size={20} className="chevron" />
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
