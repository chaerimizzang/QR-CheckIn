import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { get, post } from '../../api'
import InstallPrompt from '../../components/InstallPrompt'
import { PushBanner } from '../../components/PushCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'
import { TERMS, fmtSchedules, monthEnd, monthStart, semesterKey, semesterLabel, semesterOf } from '../../util'
import SemesterPicker, { useSemester } from '../../components/SemesterPicker'
import WeeklyPlanFields from '../../components/WeeklyPlanFields'

/** A-02 교육 목록 */
export default function Courses() {
  const [courses, setCourses] = useState(null)
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
      title="내 교육"
      actions={<button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>+ 교육 만들기</button>}
    >
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
            <Link key={c.id} to={`/admin/courses/${c.id}`} className="card list-item">
              <div>
                <div className="item-title">{c.title}{c.finalizedAt && <span className="badge badge-final">출결 확정</span>}</div>
                <div className="muted small">
                  {sem.value === 'ALL' && `${c.semesterLabel} · `}수강생 {c.studentCount}명{fmtSchedules(c.schedules) && ` · ${fmtSchedules(c.schedules)}`}{c.room && ` · ${c.room}`}
                </div>
              </div>
              <Icon name="chevron" size={20} className="chevron" />
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
