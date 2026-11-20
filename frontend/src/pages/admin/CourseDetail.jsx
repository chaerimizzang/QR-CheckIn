import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { del, get, post } from '../../api'
import { Empty, ErrorBox, Layout, Loading, SessionBadge } from '../../components/ui'
import { fmtDateTime, fmtPeriod, fmtSchedules, nextHourLocal } from '../../util'
import AttendanceStatistics from './AttendanceStatistics'
import Icon from '../../components/Icon'

/** A-03 교육 상세: 회차 목록 + 수강생 목록 */
export default function CourseDetail() {
  const { courseId } = useParams()
  const navigate = useNavigate()
  const [course, setCourse] = useState(null)
  const [sessions, setSessions] = useState(null)
  const [students, setStudents] = useState(null)
  const [attendanceRates, setAttendanceRates] = useState(null)
  const [tab, setTab] = useState('sessions')
  const [email, setEmail] = useState('')
  const [scheduledAt, setScheduledAt] = useState(nextHourLocal())
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const load = () => Promise.all([
  get(`/courses/${courseId}`).then(setCourse),
  get(`/courses/${courseId}/sessions`).then(setSessions),
  get(`/courses/${courseId}/enrollments`).then(setStudents),
  get(`/courses/${courseId}/attendance/rate`).then(setAttendanceRates),
]).catch(setError)
  useEffect(() => { load() }, [courseId])

  async function addSession(e) {
    e.preventDefault()
    setError(null)
    try {
      const s = await post(`/courses/${courseId}/sessions`, { scheduledAt })
      navigate(`/admin/sessions/${s.id}`) // 바로 장소 지정 화면으로
    } catch (err) { setError(err) }
  }

  async function addStudent(e) {
    e.preventDefault()
    setError(null)
    setNotice(null)
    try {
      const s = await post(`/courses/${courseId}/enrollments`, { email })
      setNotice(`${s.name} 님을 추가했어요`)
      setEmail('')
      load()
    } catch (err) { setError(err) }
  }

  async function removeStudent(s) {
    if (!window.confirm(`${s.name} 님을 수강생에서 뺄까요? 이 교육의 출결 기록도 함께 지워져요.`)) return
    try {
      await del(`/courses/${courseId}/enrollments/${s.userId}`)
      load()
    } catch (err) { setError(err) }
  }

  async function removeCourse() {
    if (!window.confirm(`'${course.title}' 교육을 삭제할까요? 회차와 출결 기록이 모두 지워져요.`)) return
    try {
      await del(`/courses/${courseId}`)
      navigate('/admin')
    } catch (err) { setError(err) }
  }

  if (!course) return <Layout back="/admin"><ErrorBox error={error} /><Loading /></Layout>

  return (
    <Layout back="/admin" title={course.title}>
      <ErrorBox error={error} />
      {notice && <div className="alert alert-ok">{notice}</div>}
      <p className="muted small course-meta">
        {fmtSchedules(course.schedules) || '시간표 미정'}
        {fmtPeriod(course.startDate, course.endDate) && ` · ${fmtPeriod(course.startDate, course.endDate)}`}
      </p>

      <div className="tabs">
  <button className={tab === 'sessions' ? 'on' : ''} onClick={() => setTab('sessions')}>회차 {sessions?.length ?? ''}</button>
  <button className={tab === 'students' ? 'on' : ''} onClick={() => setTab('students')}>수강생 {students?.length ?? ''}</button>
  <button className={tab === 'statistics' ? 'on' : ''} onClick={() => setTab('statistics')}>출석 통계</button>
</div>

      {tab === 'sessions' && (
        <>
          <form className="card row-form" onSubmit={addSession}>
            <input type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} required />
            <button className="btn btn-primary">+ 회차 추가</button>
          </form>
          {!sessions ? <Loading /> : sessions.length === 0 ? <Empty>회차를 추가해 주세요.</Empty> : (
            <div className="list">
              {sessions.map((s) => (
                <Link key={s.id} to={`/admin/sessions/${s.id}`} className="card list-item">
                  <div>
                    <div className="item-title">{s.sessionNo}회차 <SessionBadge status={s.status} /></div>
                    <div className="muted small">
                      {fmtDateTime(s.scheduledAt)} · 반경 {s.radiusM}m · {s.checkinMinutes}분
                      {s.latitude == null && <span className="warn"> · 장소 미지정</span>}
                    </div>
                  </div>
                  <Icon name="chevron" size={20} className="chevron" />
                </Link>
              ))}
            </div>
          )}
        </>
      )}

      {tab === 'students' && (
        <>
          <form className="card row-form" onSubmit={addStudent}>
            <input type="email" placeholder="수강생이 가입한 이메일" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <button className="btn btn-primary">+ 추가</button>
          </form>
          {!students ? <Loading /> : students.length === 0 ? (
            <Empty>수강생이 먼저 회원가입한 뒤, 그 이메일로 추가할 수 있어요.</Empty>
          ) : (
            <div className="card table-card">
              <table>
                <thead>
                    <tr>
                      <th>이름</th>
                      <th>학번</th>
                      <th>이메일</th>
                      <th>출석</th>
                      <th>지각</th>
                      <th>결석</th>
                      <th>환산 결석</th>
                      <th>출석률</th>
                      <th />
                    </tr>
                  </thead>
              <tbody>
                  {students.map((s) => {
                    const rate = attendanceRates?.students?.find(
                      (r) => r.userId === s.userId
                    )

                    return (
                      <tr key={s.userId}>
                        <td>{s.name}</td>
                        <td>{s.studentNo || '-'}</td>
                        <td className="small muted">{s.email}</td>

                        <td>{rate?.presentCount ?? '-'}</td>
                        <td>{rate?.lateCount ?? '-'}</td>
                        <td>{rate?.absentCount ?? '-'}</td>
                        <td>{rate?.convertedAbsences ?? '-'}</td>
                        <td>
                          {rate
                            ? `${rate.attendanceRate}%`
                            : '-'}
                        </td>

                        <td className="right">
                          <button
                            className="link-btn danger"
                            onClick={() => removeStudent(s)}
                          >
                            빼기
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === 'statistics' && (
        <AttendanceStatistics courseId={courseId} />
      )}


      <div className="danger-zone">
        <button className="link-btn danger" onClick={removeCourse}>교육 삭제</button>
      </div>
    </Layout>
  )
}
