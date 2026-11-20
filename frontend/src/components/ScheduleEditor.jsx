import { useEffect, useState } from 'react'
import { post, put } from '../api'
import { monthEnd, monthStart, planToSlots, slotsToPlan, toMonth } from '../util'
import WeeklyPlanFields, { planProblem } from './WeeklyPlanFields'

const todayStr = () => new Date().toLocaleDateString('sv-SE') // yyyy-MM-dd (로컬 날짜)
const laterDate = (a, b) => (!b || a > b ? a : b)

function planOf(course) {
  const p = slotsToPlan(course.schedules)
  return { startMonth: toMonth(course.startDate), endMonth: toMonth(course.endDate),
    days: p.days, startTime: p.startTime, endTime: p.endTime, sameTime: p.sameTime }
}

/** 교육 상세 '시간표' 탭: 수업 기간·요일·시간 수정 + 시간표로 회차 만들기 */
export default function ScheduleEditor({ course, onChanged, onError, onNotice }) {
  const [plan, setPlan] = useState(() => planOf(course))
  const [range, setRange] = useState({ from: '', to: '' })
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setPlan(planOf(course))
    setRange({ from: laterDate(todayStr(), course.startDate), to: course.endDate ?? '' })
  }, [course])

  const original = planOf(course)
  const dirty = JSON.stringify({ ...plan, sameTime: 0 }) !== JSON.stringify({ ...original, sameTime: 0 })

  async function run(fn) {
    setBusy(true); onError(null); onNotice(null)
    try { await fn() } catch (err) { onError(err) } finally { setBusy(false) }
  }

  const save = (e) => { e.preventDefault()
    const problem = planProblem(plan)
    if (problem) { onError({ message: problem }); return }
    run(async () => {
      await put(`/courses/${course.id}`, { title: course.title, startDate: monthStart(plan.startMonth), endDate: monthEnd(plan.endMonth) })
      await put(`/courses/${course.id}/schedules`, { slots: plan.days.length ? planToSlots(plan) : [] })
      onNotice('수업 기간과 시간표를 저장했어요'); await onChanged()
    })
  }

  const generate = (e) => { e.preventDefault(); run(async () => {
    const r = await post(`/courses/${course.id}/sessions/generate`, { from: range.from || null, to: range.to || null })
    onNotice(r.created
      ? `회차 ${r.created}개를 만들었어요${r.skipped ? ` (이미 있던 ${r.skipped}개는 건너뜀)` : ''}`
      : `새로 만들 회차가 없어요${r.skipped ? ` (이미 ${r.skipped}개 있음)` : ''}`)
    await onChanged()
  }) }

  return (
    <>
      <form className="card" onSubmit={save}>
        <h2 className="card-title">수업 기간 · 요일 · 시간</h2>
        <WeeklyPlanFields value={plan} onChange={setPlan} />
        {!original.sameTime && <p className="warn small">지금은 요일마다 시간이 달라요. 저장하면 위 시간으로 모두 맞춰져요.</p>}
        <button className="btn btn-primary btn-sm" disabled={busy || !dirty}>저장</button>
      </form>

      <form className="card" onSubmit={generate}>
        <h2 className="card-title">시간표로 회차 만들기</h2>
        <p className="muted small">기간 안의 매주 수업 시간마다 회차를 만들어요. 같은 시각 회차가 이미 있으면 건너뛰고, 장소·반경은 가장 최근에 장소를 정한 회차를 따라가요.</p>
        <div className="grid2">
          <label>부터<input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} required /></label>
          <label>까지<input type="date" value={range.to} min={range.from || undefined} onChange={(e) => setRange({ ...range, to: e.target.value })} required /></label>
        </div>
        {dirty && <p className="warn small">바꾼 내용이 있어요. 먼저 [저장]을 눌러 주세요.</p>}
        <button className="btn btn-primary btn-sm" disabled={busy || dirty || !(course.schedules ?? []).length}>회차 만들기</button>
      </form>
    </>
  )
}
