import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../../api'
import { useAuth } from '../../auth'
import InstallPrompt from '../../components/InstallPrompt'
import { PushBanner } from '../../components/PushCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'
import { DAY_LABEL, fmtSchedules, fromMinutes, toMinutes } from '../../util'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'

const DAY_KEYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

/** 오늘 요일의 수업(시작 시각 순) */
function todayClasses(courses, now) {
  const day = DAY_KEYS[now.getDay()]
  return courses.flatMap((c) => (c.schedules ?? []).filter((s) => s.dayOfWeek === day).map((s) => {
    const start = toMinutes(s.startTime)
    return { course: c, start, end: start + s.durationMinutes }
  })).sort((a, b) => a.start - b.start)
}

/** "32분 뒤 시작" · "진행 중" · "끝남" */
function whenText(cls, nowMin) {
  if (nowMin < cls.start) {
    const left = cls.start - nowMin
    return left >= 60 ? `${Math.floor(left / 60)}시간 ${left % 60}분 뒤 시작` : `${left}분 뒤 시작`
  }
  return nowMin < cls.end ? '수업 중' : '끝난 수업'
}

/** S-02 수강생 홈 */
export default function StudentHome() {
  const { user } = useAuth()
  const [courses, setCourses] = useState(null)
  const [rates, setRates] = useState({})
  const [error, setError] = useState(null)
  const sem = useSemester()
  useEffect(() => { get('/courses').then(setCourses).catch(setError) }, [])
  // 수업별 출석률·남은 결석(출결 현황과 같은 값)
  useEffect(() => {
    get('/me/profile').then((p) => setRates(Object.fromEntries(p.courses.map((c) => [c.courseId, c])))).catch(() => {})
  }, [])

  const now = new Date()
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const shown = courses ? sem.filter(courses) : []
  // 오늘 수업은 지금 학기 수업에서만 찾는다
  const thisTerm = courses ? courses.filter((c) => !sem.current || c.semesterKey === sem.current.key) : []
  const today = todayClasses(thisTerm, now)
  // 아직 안 끝난 첫 수업, 없으면 오늘 마지막 수업
  const next = today.find((t) => t.end > nowMin) ?? today[today.length - 1]
  const dateText = `${now.getMonth() + 1}월 ${now.getDate()}일 ${DAY_LABEL[DAY_KEYS[now.getDay()]]}요일`

  const hero = (
    <div className="hero-greet">
      <p className="muted-n">{dateText}</p>
      <h2>
        {user?.name}님,{' '}
        {!courses ? '' : today.length === 0 ? <><br />오늘은 수업이 없어요</>
          : <><br />오늘 수업은 {today.length === 1 ? `${today[0].course.title} 1개` : `${today.length}개`}예요</>}
      </h2>
    </div>
  )

  return (
    <Layout hero={hero} overlap>
      <section className="next-card">
        {next ? (
          <div className="next-row">
            <div className="next-time"><b>{fromMinutes(next.start)}</b><span>{fromMinutes(next.end)}</span></div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="next-title">{next.course.title}</div>
              <div className="muted small">{next.course.room ? `${next.course.room} · ` : ''}{whenText(next, nowMin)}</div>
            </div>
          </div>
        ) : (
          <div className="muted small">{courses ? '오늘 예정된 수업이 없어요' : '불러오는 중…'}</div>
        )}
        <Link to="/scan" className="scan-btn">
          <Icon name="scan-white" size={22} />
          QR 스캔해서 출석하기
        </Link>
      </section>

      <InstallPrompt />
      <PushBanner />

      <div className="quick-row">
        <Link to="/attendance" className="quick"><Icon name="chart-column-active" size={22} />출결 현황</Link>
        <Link to="/assistant" className="quick"><Icon name="sparkles-active" size={22} />AI 출결 도우미</Link>
      </div>

      <h2 className="section-title">내 수업</h2>
      <ErrorBox error={error} />
      <SemesterPicker sem={sem} />
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 등록된 수업이 없어요. 교수님께 가입한 이메일을 알려 드리고 수강생 등록을 요청해 주세요.</Empty>
      ) : shown.length === 0 ? (
        <Empty>이 학기에는 듣는 수업이 없어요. 위에서 다른 학기를 골라 보세요.</Empty>
      ) : (
        <div className="list">
          {shown.map((c) => {
            const r = rates[c.id]
            const warn = r && (r.warningLevel === 'WARNING' || r.warningLevel === 'DANGER' || r.warningLevel === 'FAIL')
            return (
              <Link key={c.id} to={`/student/courses/${c.id}`} className="card course-card">
                <div className="grow">
                  <div className="item-title">{c.title}{c.finalizedAt && <span className="badge badge-final">출결 확정</span>}</div>
                  <div className={`small ${warn ? 'sub-warn' : 'muted'}`}>
                    {sem.value === 'ALL' && `${c.semesterLabel} · `}
                    {fmtSchedules(c.schedules) || c.ownerName}
                    {r?.remainingAbsences != null && ` · 남은 결석 ${Math.max(r.remainingAbsences, 0)}회`}
                  </div>
                </div>
                {r?.attendanceRate != null && <span className="course-rate">{r.attendanceRate}%</span>}
                <Icon name="chevron" size={20} className="chevron" />
              </Link>
            )
          })}
        </div>
      )}
    </Layout>
  )
}
