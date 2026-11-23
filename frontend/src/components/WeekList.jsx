import { Link } from 'react-router-dom'

const WD = '일월화수목금토'
const pad = (n) => String(n).padStart(2, '0')
/** 10.27 (화) */
export const fmtMdDay = (d) => `${pad(d.getMonth() + 1)}.${pad(d.getDate())} (${WD[d.getDay()]})`
const mondayOf = (d) => { const m = new Date(d); m.setHours(0, 0, 0, 0); m.setDate(m.getDate() - ((m.getDay() + 6) % 7)); return m }

/**
 * 수업 날짜를 주차로 묶는다. 1주차 = 시작일(없으면 첫 수업)이 있는 주(월~일).
 * 차시 = 그 주 안에서 몇 번째 수업인지(날짜·시간 순)
 */
export function groupByWeek(items, getDate, startDate) {
  const sorted = [...items].sort((a, b) => getDate(a) - getDate(b))
  if (sorted.length === 0) return []
  const base = mondayOf(startDate ? new Date(`${startDate}T00:00:00`) : getDate(sorted[0]))
  const today = new Date()
  const weeks = new Map()
  for (const it of sorted) {
    const d = getDate(it)
    const week = Math.floor((mondayOf(d) - base) / (7 * 86400000)) + 1
    if (!weeks.has(week)) weeks.set(week, { week, rows: [] })
    const w = weeks.get(week)
    w.rows.push({ item: it, date: d, period: w.rows.length + 1 })
  }
  return [...weeks.values()].map((w) => {
    const from = mondayOf(w.rows[0].date)
    const to = new Date(from); to.setDate(to.getDate() + 7)
    return { ...w, current: today >= from && today < to }
  })
}

/** 주차 카드 목록. row(r) → { key, to?, sub?, right } */
export default function WeekList({ weeks, row }) {
  return (
    <div className="week-list">
      {weeks.map((w) => {
        const first = w.rows[0].date
        const last = w.rows[w.rows.length - 1].date
        return (
          <section key={w.week} className="week">
            <div className="week-head">
              <h3>{w.week > 0 ? `${w.week}주차` : '개강 전'}{w.current && <span className="week-now">이번 주</span>}</h3>
              <span className="muted small">{fmtMdDay(first).slice(0, 5)}{last.toDateString() !== first.toDateString() && ` – ${fmtMdDay(last).slice(0, 5)}`}</span>
            </div>
            <div className={`card week-card${w.current ? ' week-card-now' : ''}`}>
              {w.rows.map((r) => {
                const v = row(r)
                const inner = (
                  <>
                    <span className="week-date">{fmtMdDay(r.date)}</span>
                    <span className="week-period">{r.period}차시{v.sub && <span className="muted small">{v.sub}</span>}</span>
                    {v.right}
                  </>
                )
                return v.to
                  ? <Link key={v.key} to={v.to} className="week-row">{inner}</Link>
                  : <div key={v.key} className="week-row">{inner}</div>
              })}
            </div>
          </section>
        )
      })}
    </div>
  )
}
