import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { del, get, post, put } from '../../api'
import { ErrorBox, Layout, Loading, SessionBadge, StatusBadge } from '../../components/ui'
import { POOR_ACCURACY_M, STATUS_LABEL, fmtDateTime, fmtTime, getPosition } from '../../util'
import Icon from '../../components/Icon'

/** A-04 회차 설정 + A-06 회차 출결 현황·수정 */
export default function SessionDetail() {
  const { sessionId } = useParams()
  const navigate = useNavigate()
  const [s, setS] = useState(null)
  const [roster, setRoster] = useState(null)
  const [form, setForm] = useState(null)
  const [history, setHistory] = useState({})
  const [historyOpen, setHistoryOpen] = useState(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedIds, setSelectedIds] = useState([])
  const [bulkStatus, setBulkStatus] = useState('PRESENT')
  const [locating, setLocating] = useState(null) // null | 측정 중 최고 정확도(m) | true
  const [accuracy, setAccuracy] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const loadSession = () => get(`/sessions/${sessionId}`).then((data) => {
    setS(data)
    setForm({
      scheduledAt: data.scheduledAt.slice(0, 16),
      latitude: data.latitude,
      longitude: data.longitude,
      radiusM: data.radiusM,
      checkinMinutes: data.checkinMinutes,
    })
  })
  const loadRoster = () => get(`/sessions/${sessionId}/attendances`).then(setRoster)

  // 출결을 바꾸는 중에는 자동 갱신(5초)이 옛 데이터로 덮어쓰지 않게:
  // 변경할 때마다 version 을 올리고, 그 전에 시작한 갱신 응답은 버린다
  const pendingRef = useRef(0)
  const versionRef = useRef(0)

  /** 바뀐 행만 바꿔 끼우고 요약 숫자도 다시 센다(서버를 다시 부르지 않아 바로 반영) */
  function applyRows(rows) {
    setRoster((r) => {
      if (!r) return r
      const byId = new Map(rows.map((x) => [x.userId, x]))
      const students = r.students.map((st) => (byId.has(st.userId) ? { ...st, ...byId.get(st.userId) } : st))
      const count = (k) => students.filter((st) => st.status === k).length
      return { ...r, students, summary: { enrolled: students.length, present: count('PRESENT'), late: count('LATE'),
        absent: count('ABSENT'), pending: students.filter((st) => !st.status).length } }
    })
  }

  /** 수정 이력: 열려 있는 학생은 다시 불러오고, 나머지는 캐시를 지워 다음에 열 때 새로 받는다 */
  function refreshHistory(userIds) {
    setHistory((prev) => {
      const next = { ...prev }
      userIds.forEach((id) => { if (id !== historyOpen) delete next[id] }) // 열려 있는 건 새 이력이 올 때까지 그대로
      return next
    })
    if (historyOpen != null && userIds.includes(historyOpen)) fetchHistory(historyOpen)
  }
  useEffect(() => { Promise.all([loadSession(), loadRoster()]).catch(setError) }, [sessionId])

  //실시간 출결 현황 자동 갱신
  useEffect(() => {
    const timer = window.setInterval(() => {
      const v = versionRef.current
      get(`/sessions/${sessionId}/attendances`)
        .then((data) => { if (pendingRef.current === 0 && v === versionRef.current) setRoster(data) })
        .catch(() => {}) // 자동 갱신 실패는 조용히 넘기고 다음 갱신에서 다시 시도
    }, 5000)
    return () => window.clearInterval(timer)
  }, [sessionId])

  async function useMyLocation() {
    setLocating(true)
    setError(null)
    try {
      const p = await getPosition({ targetAccuracy: 20, maxWaitMs: 12000, onProgress: setLocating })
      setForm((f) => ({ ...f, latitude: Number(p.latitude.toFixed(7)), longitude: Number(p.longitude.toFixed(7)) }))
      setAccuracy(Math.round(p.accuracy))
    } catch (err) {
      setError(err)
    } finally {
      setLocating(null)
    }
  }

  async function save(e) {
    e?.preventDefault()
    if (accuracy != null && accuracy > POOR_ACCURACY_M &&
      !window.confirm(`지정한 위치의 오차가 약 ${accuracy}m예요. 학생들이 '장소에서 떨어져 있어요'로 실패할 수 있어요.\n그래도 저장할까요?`)) return
    setError(null)
    setNotice(null)
    try {
      await put(`/sessions/${sessionId}`, {
        ...form,
        radiusM: Number(form.radiusM),
        checkinMinutes: Number(form.checkinMinutes),
      })
      await loadSession()
      setNotice('출석 조건을 저장했어요')
    } catch (err) {
      const fields = err.detail?.fields
      if (fields) err.detail.hint = Object.entries(fields).map(([k, v]) => `${k}: ${v}`).join(' · ')
      setError(err)
    }
  }

  async function open() {
    setError(null)
    try {
      await post(`/sessions/${sessionId}/open`)
      navigate(`/admin/sessions/${sessionId}/qr`)
    } catch (err) { setError(err) }
  }

  async function close() {
    if (!window.confirm('출석을 지금 마감할까요? 아직 출석하지 않은 수강생은 결석 처리돼요.')) return
    try {
      await post(`/sessions/${sessionId}/close`)
      await Promise.all([loadSession(), loadRoster()])
    } catch (err) { setError(err) }
  }

  async function remove() {
    if (!window.confirm('이 회차를 삭제할까요?')) return
    try {
      await del(`/sessions/${sessionId}`)
      navigate(`/admin/courses/${s.courseId}`)
    } catch (err) { setError(err) }
  }

  /** 출석/지각/결석 버튼 — 화면은 바로 바꾸고, 서버 응답으로 한 번 더 맞춘다. 실패하면 되돌림 */
  async function change(userId, status) {
    setError(null)
    versionRef.current += 1
    pendingRef.current += 1
    applyRows([{ userId, status, source: 'MANUAL' }])
    try {
      const row = await put(`/sessions/${sessionId}/attendances/${userId}`, { status })
      applyRows([row])
      refreshHistory([userId])
    } catch (err) {
      setError(err)
      loadRoster().catch(() => {})
    } finally {
      pendingRef.current -= 1
    }
  }

  function toggleStudent(userId) {
    setSelectedIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]
    )
  }

  async function bulkChange() {
    if (selectedIds.length === 0) {
      setNotice('학생을 먼저 선택해 주세요.')
      return
    }

    setError(null)
    setNotice(null)
    const ids = selectedIds
    versionRef.current += 1
    pendingRef.current += 1
    applyRows(ids.map((userId) => ({ userId, status: bulkStatus, source: 'MANUAL' })))
    setSelectedIds([])
    try {
      const rows = await put(`/sessions/${sessionId}/attendances/bulk`, { userIds: ids, status: bulkStatus })
      applyRows(rows)
      refreshHistory(ids)
      setNotice(`${ids.length}명의 출결을 변경했어요`)
    } catch (err) {
      setError(err)
      loadRoster().catch(() => {})
    } finally {
      pendingRef.current -= 1
    }
  }
  async function fetchHistory(userId) {
    try {
      const data = await get(`/sessions/${sessionId}/attendances/${userId}/history`)
      setHistory((prev) => ({ ...prev, [userId]: data }))
    } catch (err) {
      setError(err)
    }
  }

  /** 수정 이력 열기 — 바로 펼치고(불러오는 중 표시), 받아 오면 채운다 */
  function loadHistory(userId) {
    setError(null)
    setHistoryOpen(userId)
    fetchHistory(userId)
  }

  if (!s || !form) return <Layout><ErrorBox error={error} /><Loading /></Layout>

  const editable = s.status === 'SCHEDULED'
  const changed = form.latitude !== s.latitude || form.longitude !== s.longitude ||
    Number(form.radiusM) !== s.radiusM || Number(form.checkinMinutes) !== s.checkinMinutes ||
    form.scheduledAt !== s.scheduledAt.slice(0, 16)
  const hasLocation = s.latitude != null

  const filteredStudents = roster?.students.filter((student) => {
  const keyword = searchTerm.trim().toLowerCase()
  return (student.name ?? '').toLowerCase().includes(keyword) || (student.studentNo ?? '').toLowerCase().includes(keyword)
  }) ?? []
 
  //calculation of the percentage
  const total = roster?.summary.enrolled ?? 0
  const presentRate = total === 0
    ? 0 : Math.round((roster.summary.present / total) * 100)

  const lateRate = total === 0
    ? 0 : Math.round((roster.summary.late / total) * 100)

  const absentRate = total === 0
    ? 0 : Math.round((roster.summary.absent / total) * 100)

  return (
    <Layout back={`/admin/courses/${s.courseId}`} title={<>{s.sessionNo}회차 <SessionBadge status={s.status} /></>}>
      <ErrorBox error={error} />
      {notice && <div className="alert alert-ok">{notice}</div>}

      {/* 출석 진행 버튼 */}
      <div className="card action-card">
        {s.status === 'SCHEDULED' && (
          <>
            <div>
              <div className="item-title">출석 시작</div>
              <div className="muted small">
                {hasLocation ? `누르면 QR이 표시되고 ${s.checkinMinutes}분 뒤 자동 마감돼요` : '먼저 아래에서 출석 장소를 지정해 주세요'}
              </div>
            </div>
            <button className="btn btn-primary btn-lg" onClick={open} disabled={!hasLocation || changed}>
              출석 시작
            </button>
          </>
        )}
        {s.status === 'OPEN' && (
          <>
            <div>
              <div className="item-title">출석 진행 중</div>
              <div className="muted small">{fmtTime(s.closedAt)}에 자동 마감</div>
            </div>
            <div className="btn-row">
              <Link className="btn btn-primary" to={`/admin/sessions/${sessionId}/qr`}>QR 화면</Link>
              <button className="btn" onClick={close}>지금 마감</button>
            </div>
          </>
        )}
        {s.status === 'CLOSED' && (
          <div>
            <div className="item-title">출석 마감</div>
            <div className="muted small">{fmtDateTime(s.openedAt)} 시작 · {fmtTime(s.closedAt)} 마감. 출결은 아래에서 언제든 고칠 수 있어요.</div>
          </div>
        )}
      </div>

      {/* A-04 출석 조건 */}
      <form className="card" onSubmit={save}>
        <h2>출석 조건</h2>
        <div className="grid2">
          <label>
            QR 출석 시간(분)
            <input type="number" min={1} max={60} value={form.checkinMinutes} disabled={!editable}
              onChange={(e) => setForm({ ...form, checkinMinutes: e.target.value })} required />
          </label>
          <label>
            허용 반경(m)
            <input type="number" min={10} max={1000} value={form.radiusM} disabled={!editable}
              onChange={(e) => setForm({ ...form, radiusM: e.target.value })} required />
          </label>
          <div className="field">
            <span>출석 장소</span>
            <div className="loc">
              {form.latitude != null
                ? <span className="small">{form.latitude}, {form.longitude}{accuracy != null && <span className="muted"> (정확도 약 {accuracy}m)</span>}</span>
                : <span className="warn small">아직 지정 안 됨</span>}
              {editable && (
                <button type="button" className="btn btn-sm" onClick={useMyLocation} disabled={locating != null}>
                  {locating == null ? <><Icon name="pin" size={16} /> 현재 위치로 지정</>
                    : typeof locating === 'number' ? `정확도 높이는 중… (약 ${locating}m)` : '위치 확인 중…'}
                </button>
              )}
            </div>
          </div>
        </div>
        {editable && accuracy != null && accuracy > POOR_ACCURACY_M && (
          <div className="alert alert-error">
            <strong>위치 오차가 약 {accuracy}m로 너무 커요</strong>
            <div className="small">
              PC는 GPS가 없어서 위치가 크게 틀릴 수 있어요. <b>휴대폰</b>으로 이 화면을 열어 강의실 안에서 다시 지정해 주세요.
              휴대폰인데도 이렇다면 위치 설정의 '정확한 위치'가 켜져 있는지 확인해 주세요.
            </div>
          </div>
        )}
        {editable && (
          <>
            <p className="muted small">강의실 안에서 [현재 위치로 지정]을 누르세요. 실내에서는 GPS 오차가 커서 반경 50~100m를 권장해요.</p>
            <div className="btn-row">
              <button className="btn btn-primary" disabled={!changed}>저장</button>
              <button type="button" className="link-btn danger" onClick={remove}>회차 삭제</button>
            </div>
          </>
        )}
      </form>

      {/* A-06 출결 현황·수정 */}
      <div className="card">
        <div className="card-head">
          <h2>출결 현황</h2>
          <button className="btn btn-sm" onClick={() => loadRoster().catch(setError)}>새로고침</button>
        </div>
        {!roster ? <Loading /> : (
          <>
            <div className="summary">
            <span> 출석 <b>{roster.summary.present}</b> ({presentRate}%)</span>
            <span> 지각 <b>{roster.summary.late}</b> ({lateRate}%) </span>
            <span> 결석 <b>{roster.summary.absent}</b> ({absentRate}%) </span>
            <span> 미정 <b>{roster.summary.pending}</b> </span>
            <span className="muted">/ 전체 {total}명 </span>
          </div>
            {/* 학생 검색 */}
            <div className="field" style={{ marginTop: 16 }}>
              <label htmlFor="studentSearch">학생 검색</label>
              <input
                id="studentSearch"
                type="text"
                placeholder="이름 또는 학번을 입력하세요"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>

            {/* 일괄 출결 처리 */}
            <div className="bulk-box">
              <div>
                <strong>일괄 출결 처리</strong>
                <div className="small muted">선택한 학생 {selectedIds.length}명</div>
              </div>
              <div className="bulk-actions">
                <select value={bulkStatus} onChange={(e) => setBulkStatus(e.target.value)}>
                  <option value="PRESENT">출석</option>
                  <option value="LATE">지각</option>
                  <option value="ABSENT">결석</option>
                </select>
                <button className="btn btn-primary btn-sm" onClick={bulkChange} disabled={selectedIds.length === 0}>
                  선택 학생 출결 변경
                </button>
              </div>
            </div>

            {roster.students.length === 0 ? (<div className="empty">수강생이 없어요. 교육 화면에서 추가해 주세요.</div>) : filteredStudents.length === 0 ? (
              <div className="empty">검색 결과가 없습니다</div>) :(
              <div className="roster">
                {filteredStudents.map((r) => (
                  <div key={r.userId} className="roster-item">
                    <div className="roster-row">
                      <input
                        type="checkbox"
                        aria-label={`${r.name} 선택`}
                        checked={selectedIds.includes(r.userId)}
                        onChange={() => toggleStudent(r.userId)}
                      />
                      <div className="roster-who">
                        <div>{r.name} <span className="muted small">{r.studentNo}</span></div>
                        <div className="small muted">
                          <StatusBadge status={r.status} />
                          {r.checkedAt && ` ${fmtTime(r.checkedAt)}`}
                          {r.source === 'MANUAL' && ' · 수정됨'}
                          {r.source === 'SYSTEM' && ' · 자동'}
                        </div>
                      </div>
                      <button className="link-btn small" onClick={() =>
                        historyOpen === r.userId ? setHistoryOpen(null) : loadHistory(r.userId)}>
                        {historyOpen === r.userId ? '이력 닫기' : '수정 이력'}
                      </button>
                      <div className="seg-sm">
                        {['PRESENT', 'LATE', 'ABSENT'].map((st) => (
                          <button key={st} className={r.status === st ? `on ${st.toLowerCase()}` : ''}
                            onClick={() => r.status !== st && change(r.userId, st)}>
                            {STATUS_LABEL[st]}
                          </button>
                        ))}
                      </div>
                    </div>

                    {historyOpen === r.userId && (
                      <div className="roster-history small">
                        {!history[r.userId] ? (
                          <span className="muted">불러오는 중…</span>
                        ) : !history[r.userId].length ? (
                          <span className="muted">수정 이력이 없어요</span>
                        ) : (
                          history[r.userId].map((item) => (
                            <div key={item.id}>
                              {STATUS_LABEL[item.oldStatus] || '미정'} → {STATUS_LABEL[item.newStatus] || item.newStatus}
                              <span className="muted"> · {item.modifiedByName} · {fmtTime(item.modifiedAt)}</span>
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </Layout>
  )
}
