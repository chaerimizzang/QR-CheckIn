import { useState } from 'react'
import { DAY_LABEL, WEEKDAYS, toMinutes } from '../util'
import Icon from './Icon'

/** "월·수 09:00–10:30" */
export const fmtPlan = (p) => `${WEEKDAYS.filter((d) => p.days.includes(d)).map((d) => DAY_LABEL[d]).join('·')} ${p.startTime}–${p.endTime}`

/** 이미 넣은 묶음과 같은 요일에 시간이 겹치는지 */
function overlaps(a, b) {
  return a.days.some((d) => b.days.includes(d))
    && toMinutes(a.startTime) < toMinutes(b.endTime) && toMinutes(b.startTime) < toMinutes(a.endTime)
}

/**
 * 수업 시간 묶음 만들기: 요일(월~금) + 시작~끝 시간을 고르고 [추가하기] → 아래 목록에 쌓인다.
 * 다른 요일에 다른 시간도 가능(예: 월·수 09:00–10:30 + 금 13:00–15:00).
 * plans = [{ days: ['MONDAY', ...], startTime: 'HH:mm', endTime: 'HH:mm' }, ...]
 */
export default function WeeklyPlanFields({ plans, onChange, onPendingChange }) {
  const [draft, setDraft] = useState({ days: [], startTime: '09:00', endTime: '10:30' })
  const [problem, setProblem] = useState(null)

  const setDraftAndNotify = (next) => {
    setDraft(next)
    setProblem(null)
    onPendingChange?.(next.days.length > 0)
  }
  const toggleDay = (d) => setDraftAndNotify({ ...draft, days: draft.days.includes(d) ? draft.days.filter((x) => x !== d) : [...draft.days, d] })

  function add() {
    if (!draft.days.length) return setProblem('수업 요일을 골라 주세요')
    if (!draft.startTime || !draft.endTime) return setProblem('수업 시간을 넣어 주세요')
    if (draft.endTime <= draft.startTime) return setProblem('끝나는 시간이 시작 시간보다 늦어야 해요')
    const clash = plans.find((p) => overlaps(p, draft))
    if (clash) return setProblem(`이미 넣은 ${fmtPlan(clash)} 와 시간이 겹쳐요`)
    onChange([...plans, draft])
    setDraftAndNotify({ days: [], startTime: draft.startTime, endTime: draft.endTime })
  }

  return (
    <div className="plan">
      <div className="plan-box">
        <div className="field">
          <span>수업 요일</span>
          <div className="day-chips" role="group" aria-label="수업 요일">
            {WEEKDAYS.map((d) => (
              <button key={d} type="button" className={`day-chip${draft.days.includes(d) ? ' on' : ''}`}
                aria-pressed={draft.days.includes(d)} onClick={() => toggleDay(d)}>
                {DAY_LABEL[d]}
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <span>수업 시간</span>
          <div className="time-range">
            <input type="time" step={600} value={draft.startTime} aria-label="시작 시간"
              onChange={(e) => setDraftAndNotify({ ...draft, startTime: e.target.value })} />
            <span className="muted">~</span>
            <input type="time" step={600} value={draft.endTime} aria-label="끝 시간"
              onChange={(e) => setDraftAndNotify({ ...draft, endTime: e.target.value })} />
          </div>
        </div>
        {problem && <p className="warn small">{problem}</p>}
        <button type="button" className="btn btn-sm btn-block" onClick={add}>추가하기</button>
      </div>

      {plans.length > 0 && (
        <ul className="plan-list">
          {plans.map((p, i) => (
            <li key={i}>
              <span>{fmtPlan(p)}</span>
              <button type="button" className="link-btn small" onClick={() => onChange(plans.filter((_, k) => k !== i))}>삭제</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
