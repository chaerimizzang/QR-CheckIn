import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get, post, upload } from '../../api'
import RequestCard from '../../components/RequestCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import { STATUS_LABEL, fmtDateTime } from '../../util'

const MAX_MB = 5

/** 회차마다 어떤 신청이 되는지(서버 규칙과 같게) */
function allowed(o) {
  if (!o || o.pending) return { excuse: false, correction: false }
  const st = o.myStatus
  const excuse = st !== 'PRESENT' && st !== 'EXCUSED'
  const correction = o.sessionStatus !== 'SCHEDULED' && (st === 'ABSENT' || st === 'LATE' || !st)
  return { excuse, correction, lateOk: st !== 'LATE' }
}

function optionLabel(o) {
  const st = o.myStatus ? STATUS_LABEL[o.myStatus] : o.sessionStatus === 'SCHEDULED' ? '예정' : '미정'
  return `${o.sessionNo}회차 · ${fmtDateTime(o.scheduledAt)} · ${st}${o.pending ? ' (신청 처리 중)' : ''}`
}

/** 학생: 출석 인정 신청 + 내 신청 목록 */
export default function StudentRequests() {
  const [params, setParams] = useSearchParams()
  const [list, setList] = useState(null)
  const [courses, setCourses] = useState([])
  const [open, setOpen] = useState(params.get('new') === '1')
  const [courseId, setCourseId] = useState(params.get('course') ?? '')
  const [options, setOptions] = useState(null)
  const [sessionId, setSessionId] = useState(params.get('session') ?? '')
  const [type, setType] = useState('')
  const [requested, setRequested] = useState('PRESENT')
  const [reason, setReason] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [done, setDone] = useState('')
  const fileRef = useRef(null)

  const load = () => get('/me/requests').then(setList).catch(setError)
  useEffect(() => {
    load()
    get('/courses').then(setCourses).catch(setError)
  }, [])

  useEffect(() => {
    setOptions(null)
    if (!courseId) return
    get(`/courses/${courseId}/request-options`).then(setOptions).catch(setError)
  }, [courseId])

  const option = useMemo(() => options?.find((o) => String(o.sessionId) === String(sessionId)), [options, sessionId])
  const can = allowed(option)

  // 회차를 바꾸면 고를 수 있는 종류로 맞춘다
  useEffect(() => {
    if (!option) { setType(''); return }
    if (type === 'EXCUSE' && !can.excuse) setType('')
    if (type === 'CORRECTION' && !can.correction) setType('')
    if (!type) setType(can.excuse && !can.correction ? 'EXCUSE' : can.correction && !can.excuse ? 'CORRECTION' : '')
    if (!can.lateOk && requested === 'LATE') setRequested('PRESENT')
  }, [option]) // eslint-disable-line react-hooks/exhaustive-deps

  function reset() {
    setSessionId(''); setType(''); setReason(''); setFile(null); setRequested('PRESENT')
    if (fileRef.current) fileRef.current.value = ''
  }

  function toggle() {
    setOpen(!open); setError(null); setDone('')
    if (open) { reset(); setParams({}, { replace: true }) }
  }

  function pickFile(e) {
    const f = e.target.files?.[0] ?? null
    if (f && f.size > MAX_MB * 1024 * 1024) {
      setError({ message: `파일은 ${MAX_MB}MB 이하만 올릴 수 있어요` })
      e.target.value = ''
      return
    }
    setError(null)
    setFile(f)
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true); setError(null); setDone('')
    try {
      await upload(`/sessions/${sessionId}/requests`, file, 'file', {
        type, reason, requestedStatus: type === 'CORRECTION' ? requested : undefined,
      })
      setDone('신청했어요. 교수님이 확인하면 알림으로 알려 드려요')
      reset(); setOpen(false); setParams({}, { replace: true })
      load()
      if (courseId) get(`/courses/${courseId}/request-options`).then(setOptions).catch(() => {})
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  async function cancel(id) {
    if (!window.confirm('이 신청을 취소할까요?')) return
    try { await post(`/requests/${id}/cancel`); load() } catch (err) { setError(err) }
  }

  const pending = list?.filter((r) => r.status === 'PENDING').length ?? 0

  return (
    <Layout title="출석 인정" actions={<button className="btn btn-primary" onClick={toggle}>{open ? '닫기' : '+ 신청하기'}</button>}>
      {done && <div className="alert alert-ok">{done}</div>}
      <ErrorBox error={error} />

      {open && (
        <form className="card" onSubmit={submit}>
          <label>수업
            <select className="input" value={courseId} required
              onChange={(e) => { setCourseId(e.target.value); setSessionId('') }}>
              <option value="">수업을 골라 주세요</option>
              {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
          </label>

          {courseId && (
            <label>회차
              {!options ? <Loading /> : (
                <select className="input" value={sessionId} required onChange={(e) => setSessionId(e.target.value)}>
                  <option value="">회차를 골라 주세요</option>
                  {options.map((o) => {
                    const a = allowed(o)
                    return <option key={o.sessionId} value={o.sessionId} disabled={!a.excuse && !a.correction}>{optionLabel(o)}</option>
                  })}
                </select>
              )}
            </label>
          )}

          {option && (
            <div className="field">
              신청 종류
              <div className="choice-row">
                <button type="button" className={`choice${type === 'EXCUSE' ? ' on' : ''}`} disabled={!can.excuse}
                  onClick={() => setType('EXCUSE')}>
                  <b>공결</b><span>병결·예비군·경조사 등 사유가 있는 결석</span>
                </button>
                <button type="button" className={`choice${type === 'CORRECTION' ? ' on' : ''}`} disabled={!can.correction}
                  onClick={() => setType('CORRECTION')}>
                  <b>출결 정정</b><span>출석했는데 결석·지각으로 처리됨</span>
                </button>
              </div>
              {option.sessionStatus === 'SCHEDULED' && <span className="muted small">아직 열리지 않은 회차는 공결만 미리 신청할 수 있어요</span>}
            </div>
          )}

          {type === 'CORRECTION' && (
            <div className="field">
              바꿔 달라는 상태
              <div className="choice-row">
                <button type="button" className={`choice${requested === 'PRESENT' ? ' on' : ''}`} onClick={() => setRequested('PRESENT')}><b>출석</b></button>
                <button type="button" className={`choice${requested === 'LATE' ? ' on' : ''}`} disabled={!can.lateOk} onClick={() => setRequested('LATE')}><b>지각</b></button>
              </div>
            </div>
          )}

          {type && (
            <>
              <label>사유
                <textarea className="input" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required
                  placeholder={type === 'EXCUSE' ? '예: 11/20 독감으로 병원 진료' : '예: 13:02에 QR을 찍었는데 결석으로 나왔어요'} />
                <span className="muted small">{reason.length}/500</span>
              </label>
              <div className="field">
                증빙 파일 <span className="muted small">선택 · 사진 또는 PDF, {MAX_MB}MB 이하</span>
                <div className="file-pick">
                  <input ref={fileRef} type="file" accept="image/*,application/pdf" onChange={pickFile} />
                </div>
              </div>
              <button className="btn btn-primary btn-block" disabled={busy || !reason.trim()}>
                {busy ? '보내는 중…' : `${type === 'EXCUSE' ? '공결' : '출결 정정'} 신청하기`}
              </button>
            </>
          )}
        </form>
      )}

      <div className="section-row">
        <h2 className="section-title">내 신청 {pending > 0 && <span className="muted small">처리 중 {pending}건</span>}</h2>
      </div>
      {!list ? <Loading /> : list.length === 0 ? (
        <Empty>아직 신청한 내역이 없어요. 결석·지각이 잘못됐거나 사유가 있으면 [+ 신청하기]를 눌러 주세요.</Empty>
      ) : (
        <div className="list">
          {list.map((r) => (
            <RequestCard key={r.id} r={r} onError={setError}>
              {r.status === 'PENDING' && (
                <div className="req-actions">
                  <button type="button" className="link-btn small danger" onClick={() => cancel(r.id)}>신청 취소</button>
                </div>
              )}
            </RequestCard>
          ))}
        </div>
      )}
    </Layout>
  )
}
