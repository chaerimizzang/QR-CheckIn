import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { get, post } from '../../api'
import { useAuth } from '../../auth'
import InstallPrompt from '../../components/InstallPrompt'
import { PushBanner } from '../../components/PushCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'
import { DAY_LABEL, TERMS, fmtSchedules, monthEnd, monthStart, semesterKey, semesterLabel, semesterOf, toMinutes } from '../../util'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'
import WeeklyPlanFields from '../../components/WeeklyPlanFields'

const DAY_KEYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
const hm = (d) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

/** 오늘 회차 한 줄의 오른쪽: 마감 · 진행 중(QR 화면) · 휴강 · 출석 시작 */
function TodayAction({ s }) {
  if (s.status === 'CLOSED') return <span className="badge badge-s-closed">마감</span>
  if (s.status === 'CANCELED') return <span className="badge badge-s-canceled">휴강</span>
  if (s.status === 'OPEN') return <Link className="btn btn-primary btn-sm" to={`/admin/sessions/${s.id}/qr`}>QR 화면</Link>
  return <Link className="btn btn-primary btn-sm" to={`/admin/sessions/${s.id}`}>출석 시작</Link>
}

/** A-02 교육 목록(교수 홈) */
export default function Courses() {
  const [courses, setCourses] = useState(null)
  const { user } = useAuth()
  const [detail, setDetail] = useState({}) // courseId → { sessions, rate }
  const [pendingReq, setPendingReq] = useState(0)
  const [title, setTitle] = useState('')
  const [room, setRoom] = useState('')
  const [period, setPeriod] = useState({ startMonth: '', endMonth: '' })
  const [plans, setPlans] = useState([])
  const [pending, setPending] = useState(false)
  const [semesterPick, setSemesterPick] = useState('') // '' = 시작 월 기준 자동
  const sem = useSemester()
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()
  const [error, setError] = useState(null)
  // 왼쪽 메뉴의 '교육 만들기'는 /admin?new=1 로 와서 입력창이 바로 열린다
  const [params, setParams] = useSearchParams()
  const showForm = params.get('new') === '1'
  const setShowForm = (open) => setParams(open ? { new: '1' } : {}, { replace: true })

  const load = () => get('/courses').then(setCourses).catch(setError)
  // 학기 기본값: 시작 월이 있으면 그 달, 없으면 오늘 기준
  const autoSemester = semesterOf(period.startMonth ? new Date(`${period.startMonth}-01T00:00:00`) : new Date())
  const thisYear = new Date().getFullYear()
  const semesterChoices = [thisYear - 1, thisYear, thisYear + 1].flatMap((y) => TERMS.map((t) => ({ year: y, term: t.term })))
  useEffect(() => { load() }, [])
  useEffect(() => { get('/requests/pending-count').then((r) => setPendingReq(r.pending)).catch(() => {}) }, [])
  // 지금 학기 교육마다 회차(오늘 수업)와 수강생 출석률(평균·결석 주의)을 받아 온다
  useEffect(() => {
    if (!courses || !sem.current) return
    courses.filter((c) => c.semesterKey === sem.current.key).forEach((c) => {
      Promise.all([get(`/courses/${c.id}/sessions`), get(`/courses/${c.id}/attendance/rate`).catch(() => null)])
        .then(([sessions, rate]) => setDetail((d) => ({ ...d, [c.id]: { sessions, rate } })))
        .catch(() => {})
    })
  }, [courses, sem.current]) // eslint-disable-line react-hooks/exhaustive-deps

  const now = new Date()
  const today = (courses ?? []).flatMap((c) => (detail[c.id]?.sessions ?? [])
    .filter((s) => sameDay(new Date(s.scheduledAt), now))
    .map((s) => {
      const start = new Date(s.scheduledAt)
      const slot = (c.schedules ?? []).find((x) => x.dayOfWeek === DAY_KEYS[start.getDay()] && x.startTime === hm(start))
      const end = slot ? new Date(start.getTime() + slot.durationMinutes * 60000) : null
      return { s, c, start, end }
    }))
    .sort((a, b) => a.start - b.start)
  const held = today.filter((t) => t.s.status !== 'CANCELED')
  const when = (t) => {
    if (t.s.status === 'OPEN') return '출석 진행 중'
    if (t.s.status !== 'SCHEDULED') return null
    const left = Math.round((t.start - now) / 60000)
    return left > 0 ? (left >= 60 ? `${Math.floor(left / 60)}시간 ${left % 60}분 뒤` : `${left}분 뒤`) : '시작 시간 지남'
  }
  /** 교육 카드: 평균 출석률(마감 회차가 있을 때)과 결석 주의 인원 */
  const stat = (c) => {
    const rate = detail[c.id]?.rate
    if (!rate || rate.students.length === 0) return null
    const avg = rate.totalSessions > 0 ? Math.round(rate.students.reduce((n, x) => n + x.attendanceRate, 0) / rate.students.length) : null
    const risky = rate.students.filter((x) => ['WARNING', 'DANGER', 'FAIL'].includes(x.warningLevel)).length
    return { avg, risky }
  }
  const dateText = `${now.getMonth() + 1}월 ${now.getDate()}일 ${DAY_LABEL[DAY_KEYS[now.getDay()]]}요일`

  async function create(e) {
    e.preventDefault()
    setError(null)
    const problem = pending ? '고른 요일·시간을 [추가하기]로 넣어 주세요'
      : plans.length && (!period.startMonth || !period.endMonth) ? '수업 기간(몇 월부터 몇 월까지)을 넣어 주세요'
      : period.startMonth && period.endMonth && period.endMonth < period.startMonth ? '끝 월이 시작 월보다 빨라요'
      : null
    if (problem) { setError({ message: problem }); return }
    const chosen = semesterPick ? { year: Number(semesterPick.split('-')[0]), term: semesterPick.split('-')[1] } : autoSemester
    setBusy(true)
    try {
      const c = await post('/courses', {
        title,
        room,
        startDate: monthStart(period.startMonth),
        endDate: monthEnd(period.endMonth),
        // 수업 시간을 넣었으면 시간표 저장 + 오늘부터 끝 월까지 회차 자동 생성
        schedules: plans.length ? plans : null,
        semesterYear: chosen.year,
        semesterTerm: chosen.term,
      })
      navigate(`/admin/courses/${c.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Layout
      overlap={!showForm}
      actions={<button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>{showForm ? '닫기' : '+ 교육 만들기'}</button>}
      hero={!showForm && (
        <div className="hero-greet">
          <p className="muted-n">{dateText}</p>
          <h2>{user?.name}님,<br />{held.length ? `오늘 수업은 ${held.length}개예요` : '오늘은 수업이 없어요'}</h2>
        </div>
      )}
    >
      {!showForm && (
        <>
          <section className="next-card today-card">
            {today.length === 0 ? (
              <div className="muted small">{courses ? '오늘 출석을 받을 수업이 없어요' : '불러오는 중…'}</div>
            ) : today.map((t) => (
              <div key={t.s.id} className="today-row">
                <div className="next-time"><b>{hm(t.start)}</b>{t.end && <span>{hm(t.end)}</span>}</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="next-title">{t.c.title}</div>
                  <div className="muted small">
                    {[t.c.room, t.s.status === 'CANCELED' ? (t.s.cancelReason || '휴강') : when(t)].filter(Boolean).join(' · ')}
                  </div>
                </div>
                <TodayAction s={t.s} />
              </div>
            ))}
          </section>
          <div className="quick-row">
            <Link to="/requests" className="quick"><Icon name="clipboard-check-active" size={22} />
              <span style={{ flex: 1 }}>출석 인정</span>
              {pendingReq > 0 && <span className="menu-count" aria-label={`처리할 신청 ${pendingReq}건`}>{pendingReq}</span>}
            </Link>
            <Link to="/timetable" className="quick"><Icon name="calendar-active" size={22} />시간표</Link>
          </div>
          <h2 className="section-title">내 교육</h2>
        </>
      )}
      <InstallPrompt />
      <PushBanner />
      <ErrorBox error={error} />
      {showForm && (
        <form className="card" onSubmit={create}>
          <label>교육 이름<input placeholder="예: 데이터베이스 실습" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus /></label>
          <label>강의실 <span className="muted small">(선택)</span><input placeholder="예: 공학관 302호" value={room} onChange={(e) => setRoom(e.target.value)} maxLength={50} /></label>
          <div className="grid2">
            <label>시작 월<input type="month" value={period.startMonth} onChange={(e) => setPeriod({ ...period, startMonth: e.target.value })} /></label>
            <label>끝 월<input type="month" value={period.endMonth} min={period.startMonth || undefined} onChange={(e) => setPeriod({ ...period, endMonth: e.target.value })} /></label>
          </div>
          <label>학기
            <select className="input" value={semesterPick} onChange={(e) => setSemesterPick(e.target.value)}>
              <option value="">{semesterLabel(autoSemester)} (시작 월 기준 자동)</option>
              {semesterChoices.map((s) => <option key={semesterKey(s)} value={semesterKey(s)}>{semesterLabel(s)}</option>)}
            </select>
          </label>
          <WeeklyPlanFields plans={plans} onChange={setPlans} onPendingChange={setPending} />
          <p className="muted small">수업 시간을 넣으면 오늘부터 끝 월까지 매주 회차가 자동으로 만들어져요. 비워 두면 회차는 직접 추가해요.</p>
          <div className="btn-row">
            <button className="btn btn-primary" disabled={busy}>{busy ? '만드는 중…' : '만들기'}</button>
            <button type="button" className="btn" onClick={() => setShowForm(false)}>취소</button>
          </div>
        </form>
      )}
      <SemesterPicker sem={sem} />
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 만든 교육이 없어요. <b>교육 만들기</b>로 시작해 보세요.</Empty>
      ) : sem.filter(courses).length === 0 ? (
        <Empty>이 학기에는 교육이 없어요. 위에서 다른 학기를 골라 보세요.</Empty>
      ) : (
        <div className="list">
          {sem.filter(courses).map((c) => (
            <Link key={c.id} to={`/admin/courses/${c.id}`} className="card course-card">
              <div className="grow">
                <div className="item-title">{c.title}{c.finalizedAt && <span className="badge badge-final">출결 확정</span>}</div>
                <div className="muted small">{sem.value === 'ALL' && `${c.semesterLabel} · `}{fmtSchedules(c.schedules) || '시간표 미정'}</div>
                <div className="muted small">
                  {c.room || '강의실 미정'} · 수강생 {c.studentCount}명
                  {stat(c)?.risky > 0 && <> · <span className="sub-warn">결석 주의 {stat(c).risky}명</span></>}
                </div>
              </div>
              {stat(c)?.avg != null && (
                <div className="right"><div className="course-rate">{stat(c).avg}%</div><div className="muted" style={{ fontSize: '0.7rem' }}>평균 출석률</div></div>
              )}
              <Icon name="chevron" size={20} className="chevron" />
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
