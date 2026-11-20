import { DAY_LABEL, WEEKDAYS } from '../util'

/**
 * 수업 기간(몇 월 ~ 몇 월) · 요일(월~금) · 시간(시작~끝) 입력.
 * value = { startMonth, endMonth, days: ['MONDAY', ...], startTime: 'HH:mm', endTime: 'HH:mm' }
 */
export default function WeeklyPlanFields({ value, onChange, showPeriod = true }) {
  const set = (patch) => onChange({ ...value, ...patch })
  const toggleDay = (d) => set({ days: value.days.includes(d) ? value.days.filter((x) => x !== d) : [...value.days, d] })

  return (
    <div className="plan">
      {showPeriod && (
        <div className="grid2">
          <label>시작 월<input type="month" value={value.startMonth} onChange={(e) => set({ startMonth: e.target.value })} /></label>
          <label>끝 월<input type="month" value={value.endMonth} min={value.startMonth || undefined} onChange={(e) => set({ endMonth: e.target.value })} /></label>
        </div>
      )}
      <div className="field">
        <span>수업 요일</span>
        <div className="day-chips" role="group" aria-label="수업 요일">
          {WEEKDAYS.map((d) => (
            <button key={d} type="button" className={`day-chip${value.days.includes(d) ? ' on' : ''}`}
              aria-pressed={value.days.includes(d)} onClick={() => toggleDay(d)}>
              {DAY_LABEL[d]}
            </button>
          ))}
        </div>
      </div>
      <div className="grid2">
        <label>시작 시간<input type="time" step={600} value={value.startTime} onChange={(e) => set({ startTime: e.target.value })} /></label>
        <label>끝 시간<input type="time" step={600} value={value.endTime} onChange={(e) => set({ endTime: e.target.value })} /></label>
      </div>
    </div>
  )
}

/** 입력 검사 — 문제가 있으면 안내 문장, 없으면 null */
export function planProblem(v, { requirePeriod = true } = {}) {
  if (requirePeriod && v.days.length && (!v.startMonth || !v.endMonth)) return '수업 기간(몇 월부터 몇 월까지)을 넣어 주세요'
  if (v.startMonth && v.endMonth && v.endMonth < v.startMonth) return '끝 월이 시작 월보다 빨라요'
  if (v.days.length && (!v.startTime || !v.endTime)) return '수업 시간을 넣어 주세요'
  if (v.days.length && v.endTime <= v.startTime) return '끝나는 시간이 시작 시간보다 늦어야 해요'
  return null
}
