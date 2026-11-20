import { useEffect, useState } from 'react'
import { post, put } from '../api'
import { DAYS, DAY_LABEL, fmtDuration } from '../util'
import Icon from './Icon'

const DURATIONS = [30, 50, 60, 75, 90, 100, 120, 150, 180, 240]
const todayStr = () => new Date().toLocaleDateString('sv-SE') // yyyy-MM-dd (로컬 날짜)
const laterDate = (a, b) => (!b || a > b ? a : b)

/** 교육 상세 '시간표' 탭: 교육 기간 · 주간 시간표 편집 · 시간표로 회차 만들기 */
export default function ScheduleEditor({ course, onChanged, onError, onNotice }) {
  const [period, setPeriod] = useState({ startDate: course.startDate ?? '', endDate: course.endDate ?? '' })
  const [slots, setSlots] = useState(course.schedules ?? [])
  const [range, setRange] = useState({ from: '', to: '' })
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setPeriod({ startDate: course.startDate ?? '', endDate: course.endDate ?? '' })
    setSlots(course.schedules ?? [])
    setRange({ from: laterDate(todayStr(), course.startDate), to: course.endDate ?? '' })
  }, [course])

  const dirty = JSON.stringify(slots) !== JSON.stringify(course.schedules ?? [])
  const periodDirty = (period.startDate || null) !== (course.startDate ?? null) || (period.endDate || null) !== (course.endDate ?? null)

  const update = (i, patch) => setSlots(slots.map((s, k) => (k === i ? { ...s, ...patch } : s)))
  const addSlot = () => {
    const last = slots[slots.length - 1]
    setSlots([...slots, { dayOfWeek: last ? DAYS[(DAYS.indexOf(last.dayOfWeek) + 2) % 5] : 'MONDAY', startTime: last?.startTime ?? '09:00', durationMinutes: last?.durationMinutes ?? 90 }])
  }

  async function run(fn) {
    setBusy(true); onError(null); onNotice(null)
    try { await fn() } catch (err) { onError(err) } finally { setBusy(false) }
  }

  const savePeriod = (e) => { e.preventDefault(); run(async () => {
    await put(`/courses/${course.id}`, { title: course.title, startDate: period.startDate || null, endDate: period.endDate || null })
    onNotice('교육 기간을 저장했어요'); await onChanged()
  }) }

  const saveSlots = () => run(async () => {
    await put(`/courses/${course.id}/schedules`, { slots })
    onNotice(slots.length ? '시간표를 저장했어요' : '시간표를 비웠어요'); await onChanged()
  })

  const generate = (e) => { e.preventDefault(); run(async () => {
    const r = await post(`/courses/${course.id}/sessions/generate`, { from: range.from || null, to: range.to || null })
    onNotice(r.created
      ? `회차 ${r.created}개를 만들었어요${r.skipped ? ` (이미 있던 ${r.skipped}개는 건너뜀)` : ''}`
      : `새로 만들 회차가 없어요${r.skipped ? ` (이미 ${r.skipped}개 있음)` : ''}`)
    await onChanged()
  }) }

  return (
    <>
      <form className="card" onSubmit={savePeriod}>
        <h2 className="card-title">교육 기간</h2>
        <div className="grid2">
          <label>시작일<input type="date" value={period.startDate} onChange={(e) => setPeriod({ ...period, startDate: e.target.value })} /></label>
          <label>종료일<input type="date" value={period.endDate} min={period.startDate || undefined} onChange={(e) => setPeriod({ ...period, endDate: e.target.value })} /></label>
        </div>
        <button className="btn btn-sm" disabled={busy || !periodDirty} style={{ marginTop: 10 }}>기간 저장</button>
      </form>

      <div className="card">
        <h2 className="card-title">주간 시간표</h2>
        {slots.length === 0 && <p className="muted small">아직 시간표가 없어요. 수업 시간을 추가해 주세요.</p>}
        <div className="slot-list">
          {slots.map((s, i) => (
            <div key={i} className="slot-row">
              <select value={s.dayOfWeek} onChange={(e) => update(i, { dayOfWeek: e.target.value })} aria-label="요일">
                {DAYS.map((d) => <option key={d} value={d}>{DAY_LABEL[d]}</option>)}
              </select>
              <input type="time" step={600} value={s.startTime} onChange={(e) => update(i, { startTime: e.target.value })} aria-label="시작 시간" required />
              <select value={s.durationMinutes} onChange={(e) => update(i, { durationMinutes: Number(e.target.value) })} aria-label="수업 길이">
                {[...new Set([...DURATIONS, s.durationMinutes])].sort((a, b) => a - b).map((m) => <option key={m} value={m}>{fmtDuration(m)}</option>)}
              </select>
              <button type="button" className="icon-btn" onClick={() => setSlots(slots.filter((_, k) => k !== i))} aria-label="이 수업 시간 삭제">
                <Icon name="remove" size={18} />
              </button>
            </div>
          ))}
        </div>
        <div className="btn-row" style={{ marginTop: 10 }}>
          <button type="button" className="btn btn-sm link-ico" onClick={addSlot} disabled={slots.length >= 14}>
            <Icon name="plus-primary" size={16} /> 수업 시간 추가
          </button>
          <button type="button" className="btn btn-primary btn-sm" onClick={saveSlots} disabled={busy || !dirty}>시간표 저장</button>
        </div>
      </div>

      <form className="card" onSubmit={generate}>
        <h2 className="card-title">시간표로 회차 만들기</h2>
        <p className="muted small">기간 안의 매주 수업 시간마다 회차를 만들어요. 같은 시각 회차가 이미 있으면 건너뛰고, 장소·반경은 가장 최근에 장소를 정한 회차를 따라가요.</p>
        <div className="grid2">
          <label>부터<input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} required /></label>
          <label>까지<input type="date" value={range.to} min={range.from || undefined} onChange={(e) => setRange({ ...range, to: e.target.value })} required /></label>
        </div>
        {dirty && <p className="warn small">시간표를 바꿨어요. 먼저 [시간표 저장]을 눌러 주세요.</p>}
        <button className="btn btn-primary btn-sm" disabled={busy || dirty || !(course.schedules ?? []).length} style={{ marginTop: 10 }}>
          회차 만들기
        </button>
      </form>
    </>
  )
}
