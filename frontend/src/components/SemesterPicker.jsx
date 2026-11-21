import { useEffect, useState } from 'react'
import { get } from '../api'

const STORE = 'qr-semester'

/**
 * 학기 선택. 기본은 현재 학기, 고른 값은 이 탭에서 기억한다(다른 화면에 갔다 와도 유지).
 * value = 학기 키("2026-FALL") 또는 'ALL'
 */
export function useSemester() {
  const [semesters, setSemesters] = useState(null)
  const [current, setCurrent] = useState(null)
  const [value, setValue] = useState(() => {
    try { return sessionStorage.getItem(STORE) } catch { return null }
  })

  useEffect(() => {
    Promise.all([get('/semesters'), get('/semesters/current')]).then(([list, cur]) => {
      setSemesters(list)
      setCurrent(cur)
      setValue((v) => (v && (v === 'ALL' || list.some((s) => s.key === v)) ? v : cur.key))
    }).catch(() => { setSemesters([]); setValue('ALL') })
  }, [])

  function choose(v) {
    setValue(v)
    try { sessionStorage.setItem(STORE, v) } catch { /* 저장 못 해도 이번엔 적용 */ }
  }

  /** 고른 학기의 교육만(학기 정보가 없는 예전 교육은 '전체'에서만 보임) */
  const filter = (courses) => (!value || value === 'ALL' ? courses : courses.filter((c) => c.semesterKey === value))

  return { semesters, current, value, choose, filter }
}

export default function SemesterPicker({ sem }) {
  if (!sem.semesters || sem.semesters.length === 0) return null
  return (
    <div className="semester-picker">
      <select className="input" value={sem.value ?? ''} onChange={(e) => sem.choose(e.target.value)} aria-label="학기 선택">
        {sem.semesters.map((s) => (
          <option key={s.key} value={s.key}>{s.label}{sem.current?.key === s.key ? ' (현재)' : ''}</option>
        ))}
        <option value="ALL">전체 학기</option>
      </select>
    </div>
  )
}
