import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api'
import { useAuth } from '../auth'
import { Empty, ErrorBox, Layout, Loading } from '../components/ui'
import { DAYS, DAY_LABEL, fmtSlot, fromMinutes, toMinutes } from '../util'

const HOUR_PX = 52

/** 주간 시간표(월~일) — 내 교육(관리자: 개설한 교육, 수강생: 수강 중인 교육)의 시간표를 한 화면에 */
export default function Timetable() {
  const { user } = useAuth()
  const [courses, setCourses] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/courses').then(setCourses).catch(setError) }, [])

  const isAdmin = user?.role === 'ADMIN'
  const linkOf = (c) => (isAdmin ? `/admin/courses/${c.id}` : `/student/courses/${c.id}`)

  const { blocks, startHour, endHour, unscheduled } = useMemo(() => {
    const list = (courses ?? []).flatMap((c) => (c.schedules ?? []).map((s) => {
      const start = toMinutes(s.startTime)
      return { course: c, slot: s, start, end: start + s.durationMinutes }
    }))
    const min = list.length ? Math.min(...list.map((b) => b.start)) : 9 * 60
    const max = list.length ? Math.max(...list.map((b) => b.end)) : 18 * 60
    return {
      blocks: list,
      startHour: Math.min(9, Math.floor(min / 60)),
      endHour: Math.max(18, Math.ceil(max / 60)),
      unscheduled: (courses ?? []).filter((c) => !(c.schedules ?? []).length),
    }
  }, [courses])

  const hours = Array.from({ length: endHour - startHour }, (_, i) => startHour + i)
  const top = (min) => ((min - startHour * 60) / 60) * HOUR_PX
  const today = DAYS[(new Date().getDay() + 6) % 7]

  return (
    <Layout title="시간표">
      <ErrorBox error={error} />
      {!courses ? (!error && <Loading />) : courses.length === 0 ? (
        <Empty>{isAdmin ? '아직 만든 교육이 없어요.' : '아직 수강 중인 교육이 없어요.'}</Empty>
      ) : (
        <>
          <div className="tt card">
            <div className="tt-head">
              <div />
              {DAYS.map((d) => <div key={d} className={`tt-day${d === today ? ' today' : ''}`}>{DAY_LABEL[d]}</div>)}
            </div>
            <div className="tt-body" style={{ height: hours.length * HOUR_PX }}>
              <div className="tt-hours">
                {hours.map((h) => <div key={h} style={{ height: HOUR_PX }}>{h}</div>)}
              </div>
              {DAYS.map((d) => (
                <div key={d} className={`tt-col${d === today ? ' today' : ''}`}>
                  {hours.map((h) => <div key={h} className="tt-line" style={{ top: (h - startHour) * HOUR_PX }} />)}
                  {blocks.filter((b) => b.slot.dayOfWeek === d).map((b, i) => (
                    <Link key={i} to={linkOf(b.course)} className="tt-block"
                      style={{ top: top(b.start), height: Math.max(top(b.end) - top(b.start) - 2, 18) }}
                      title={`${b.course.title} · ${fmtSlot(b.slot)}`}>
                      <span className="tt-title">{b.course.title}</span>
                      <span className="tt-time">{b.slot.startTime}–{fromMinutes(b.end)}</span>
                    </Link>
                  ))}
                </div>
              ))}
            </div>
          </div>

          {blocks.length === 0 && (
            <Empty>{isAdmin ? '교육 화면의 [시간표] 탭에서 수업 시간을 넣으면 여기에 보여요.' : '아직 시간표가 정해진 교육이 없어요.'}</Empty>
          )}
          {isAdmin && unscheduled.length > 0 && blocks.length > 0 && (
            <p className="muted small">시간표가 없는 교육: {unscheduled.map((c, i) => (
              <span key={c.id}>{i > 0 && ', '}<Link to={`/admin/courses/${c.id}?tab=schedule`}>{c.title}</Link></span>
            ))}</p>
          )}
        </>
      )}
    </Layout>
  )
}
