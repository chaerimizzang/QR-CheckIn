import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { del, get, post, put } from '../../api'
import { Empty, ErrorBox, HeroTiles, Layout, Loading, SessionBadge } from '../../components/ui'
import WeekList, { groupByWeek } from '../../components/WeekList'
import { fmtDateTime, fmtPeriod, fmtSchedules, nextHourLocal, WARNING } from '../../util'
import StudentCsvTools from '../../components/StudentCsvTools'
import AttendanceStatistics from './AttendanceStatistics'

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
  const [roomEdit, setRoomEdit] = useState(null) // 강의실 바꾸는 중이면 입력값

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

  async function finalizeCourse() {
    setError(null); setNotice(null)
    try {
      const c = await get(`/courses/${courseId}/finalize-check`)
      if (c.openSessions > 0) { setError({ message: '출석이 진행 중인 회차가 있어요. 먼저 마감해 주세요' }); return }
      if (c.pendingRequests > 0) {
        setError({ message: `처리하지 않은 출석 인정 신청이 ${c.pendingRequests}건 있어요. 메뉴 [출석 인정]에서 먼저 승인·반려해 주세요` })
        return
      }
      const extra = c.unopenedSessions > 0 ? `\n열지 않은 예정 회차 ${c.unopenedSessions}개는 삭제돼요.` : ''
      if (!window.confirm(`'${course.title}' 출결을 확정할까요?${extra}`)) return
      await post(`/courses/${courseId}/finalize`)
      setNotice('출결을 확정했어요')
      load()
    } catch (err) { setError(err) }
  }

  async function reopenCourse() {
    if (!window.confirm('출결 확정을 취소할까요?')) return
    try { await post(`/courses/${courseId}/reopen`); setNotice('출결 확정을 취소했어요'); load() } catch (err) { setError(err) }
  }

  async function removeCourse() {
    if (!window.confirm(`'${course.title}' 교육을 삭제할까요? 회차와 출결 기록이 모두 지워져요.`)) return
    try {
      await del(`/courses/${courseId}`)
      navigate('/admin')
    } catch (err) { setError(err) }
  }

  // 강의실만 바꾼다 — 교육 수정 API 는 이름·기간·학기를 함께 받으므로 지금 값을 그대로 보낸다
  async function saveRoom(e) {
    e.preventDefault()
    setError(null)
    try {
      const c = await put(`/courses/${courseId}`, {
        title: course.title, startDate: course.startDate, endDate: course.endDate,
        semesterYear: course.semesterYear, semesterTerm: course.semesterTerm, room: roomEdit,
      })
      setCourse(c); setRoomEdit(null)
    } catch (err) { setError(err) }
  }

  if (!course) return <Layout back="/admin"><ErrorBox error={error} /><Loading /></Layout>

  return (
    <Layout back="/admin" title={course.title}
      subtitle={`${fmtSchedules(course.schedules) || '시간표 미정'} · ${course.room || '강의실 미정'} · 수강생 ${course.studentCount}명`}
      hero={sessions && (
        <HeroTiles items={[
          { label: `전체 · ${groupByWeek(sessions, (x) => new Date(x.scheduledAt), course.startDate).at(-1)?.week ?? 0}주`, value: `${sessions.filter((x) => x.status !== 'CANCELED').length}회` },
          { label: '마감', value: `${sessions.filter((x) => x.status === 'CLOSED').length}회` },
          { label: '휴강', value: `${sessions.filter((x) => x.status === 'CANCELED').length}회` },
        ]} />
      )}>
      <ErrorBox error={error} />
      {notice && <div className="alert alert-ok">{notice}</div>}
      {course.finalizedAt && (
        <div className="final-banner">
          <div><b>출결 확정됨</b> <span className="small muted">{fmtDateTime(course.finalizedAt)}</span></div>
          <button type="button" className="link-btn small" onClick={reopenCourse}>확정 취소</button>
        </div>
      )}
      <p className="muted small course-meta">
        {course.semesterLabel} · {fmtSchedules(course.schedules) || '시간표 미정'}
        {fmtPeriod(course.startDate, course.endDate) && ` · ${fmtPeriod(course.startDate, course.endDate)}`}
        {' · '}{course.room || '강의실 미정'}
        {!course.finalizedAt && roomEdit === null && (
          <> <button type="button" className="link-btn small" onClick={() => setRoomEdit(course.room || '')}>강의실 변경</button></>
        )}
      </p>
      {roomEdit !== null && (
        <form className="card row-form" onSubmit={saveRoom}>
          <input value={roomEdit} onChange={(e) => setRoomEdit(e.target.value)} maxLength={50} placeholder="예: 공학관 302호"
            aria-label="강의실" autoFocus />
          <button className="btn btn-primary">저장</button>
          <button type="button" className="btn" onClick={() => setRoomEdit(null)}>취소</button>
        </form>
      )}

      <div className="tabs">
  <button className={tab === 'sessions' ? 'on' : ''} onClick={() => setTab('sessions')}>수업 날짜 {sessions?.length ?? ''}</button>
  <button className={tab === 'students' ? 'on' : ''} onClick={() => setTab('students')}>수강생 {students?.length ?? ''}</button>
  <button className={tab === 'statistics' ? 'on' : ''} onClick={() => setTab('statistics')}>출석 통계</button>
</div>

      {tab === 'sessions' && (
        <>
          {!course.finalizedAt && <form className="card row-form" onSubmit={addSession}>
            <input type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} required />
            <button className="btn btn-primary">+ 날짜 추가</button>
          </form>}
          {!sessions ? <Loading /> : sessions.length === 0 ? <Empty>수업 날짜를 추가해 주세요.</Empty> : (
            <WeekList weeks={groupByWeek(sessions, (s) => new Date(s.scheduledAt), course.startDate)} row={({ item: s, date }) => ({
              key: s.id,
              to: `/admin/sessions/${s.id}`,
              // 휴강이면 사유, 아직 안 한 수업이면 시작 시간(장소 미지정 표시). 출석 인원은 적지 않는다
              sub: s.status === 'CANCELED' ? (s.cancelReason || null)
                : s.status === 'SCHEDULED' ? <>{`${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')} 시작`}{s.latitude == null && <span className="warn"> · 장소 미지정</span>}</>
                : null,
              right: <SessionBadge status={s.status} />,
            })} />
          )}
        </>
      )}

      {tab === 'students' && (
        <>
          {!course.finalizedAt && <>
          <form className="card row-form" onSubmit={addStudent}>
            <input type="email" placeholder="수강생이 가입한 이메일" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <button className="btn btn-primary">+ 추가</button>
          </form>
          </>}
          <StudentCsvTools course={course} onDone={load} onError={setError} />
          {(() => {
            const risky = attendanceRates?.students?.filter((r) => r.warningLevel === 'DANGER' || r.warningLevel === 'FAIL') ?? []
            return risky.length > 0 && (
              <div className="warn-chip warn-banner warn-danger">
                <b>결석 위험 학생 {risky.length}명</b> {risky.map((r) => r.name).join(', ')}
                <span className="small"> · 전체 {attendanceRates.plannedSessions}회 중 인정 결석 {attendanceRates.allowedAbsences}회를 넘으면 F 기준이에요</span>
              </div>
            )
          })()}
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
                      <th>F 기준</th>
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
                        <td>
                          {WARNING[rate?.warningLevel]
                            ? <span className={`warn-tag ${WARNING[rate.warningLevel].cls}`}>{WARNING[rate.warningLevel].label}</span>
                            : rate?.remainingAbsences != null ? <span className="muted small">남은 결석 {Math.max(rate.remainingAbsences, 0)}회</span> : '-'}
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
        {!course.finalizedAt && (
          <button className="btn btn-sm" onClick={finalizeCourse}>학기 마무리 · 출결 확정</button>
        )}
        {!course.finalizedAt && <button className="link-btn danger" onClick={removeCourse}>교육 삭제</button>}
      </div>
    </Layout>
  )
}
