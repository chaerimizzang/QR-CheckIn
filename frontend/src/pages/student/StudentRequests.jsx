import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get, post, upload } from '../../api'
import Icon from '../../components/Icon'
import RequestCard, { fmtDay } from '../../components/RequestCard'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import { STATUS_LABEL, fmtTime } from '../../util'

const MAX_MB = 5

/** 이 날짜에 할 수 있는 신청(서버 규칙과 같게). 정정은 항상 '출석으로' */
function allowed(o) {
  if (!o || o.pending) return { excuse: false, correction: false }
  const st = o.myStatus
  return {
    excuse: st !== 'PRESENT' && st !== 'EXCUSED',
    correction: o.sessionStatus !== 'SCHEDULED' && st !== 'PRESENT' && st !== 'EXCUSED',
  }
}

function dateLabel(o) {
  const st = o.myStatus ? STATUS_LABEL[o.myStatus] : o.sessionStatus === 'SCHEDULED' ? '예정' : '미정'
  return `${fmtDay(o.scheduledAt)} ${fmtTime(o.scheduledAt)} · ${st}`
}

/** 학생: 출석 인정 신청 + 내 신청 목록 */
export default function StudentRequests() {
  const [params, setParams] = useSearchParams()
  const [list, setList] = useState(null)
  const [courses, setCourses] = useState([])
  const [formOpen, setFormOpen] = useState(params.get('new') === '1')
  const [courseId, setCourseId] = useState(params.get('course') ?? '')
  const [options, setOptions] = useState(null)
  const [sessionId, setSessionId] = useState(params.get('session') ?? '')
  const [type, setType] = useState('')
  const [reason, setReason] = useState('')
  const [file, setFile] = useState(null)
  const [openId, setOpenId] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [done, setDone] = useState('')
  const fileRef = useRef(null)

  const load = () => get('/me/requests').then(setList).catch(setError)
  useEffect(() => {
    load()
    get('/courses').then((cs) => setCourses(cs.filter((c) => !c.finalizedAt))).catch(setError)
  }, [])

  useEffect(() => {
    setOptions(null)
    if (courseId) get(`/courses/${courseId}/request-options`).then(setOptions).catch(setError)
  }, [courseId])

  // 신청할 수 있는 날짜만, 가까운 날짜부터
  const dates = useMemo(() => (options ?? [])
    .filter((o) => { const a = allowed(o); return a.excuse || a.correction })
    .sort((a, b) => new Date(b.scheduledAt) - new Date(a.scheduledAt)), [options])
  const option = dates.find((o) => String(o.sessionId) === String(sessionId))
  const can = allowed(option)

  // 날짜를 고르면 할 수 있는 종류가 하나뿐일 때 자동으로 고른다
  useEffect(() => {
    if (!option) { setType(''); return }
    if ((type === 'EXCUSE' && !can.excuse) || (type === 'CORRECTION' && !can.correction) || !type) {
      setType(can.correction ? (option.myStatus === 'ABSENT' && can.excuse ? '' : 'CORRECTION') : 'EXCUSE')
    }
  }, [option]) // eslint-disable-line react-hooks/exhaustive-deps

  function reset() {
    setSessionId(''); setType(''); setReason(''); setFile(null)
    if (fileRef.current) fileRef.current.value = ''
  }

  function toggleForm() {
    setFormOpen(!formOpen); setError(null); setDone('')
    if (formOpen) { reset(); setParams({}, { replace: true }) }
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
        type, reason, requestedStatus: type === 'CORRECTION' ? 'PRESENT' : undefined,
      })
      setDone('신청했어요. 교수님이 확인하면 알림으로 알려 드려요')
      reset(); setFormOpen(false); setParams({}, { replace: true })
      load()
    } catch (err) { setError(err) } finally { setBusy(false) }
  }

  async function cancel(id) {
    if (!window.confirm('이 신청을 취소할까요?')) return
    try { await post(`/requests/${id}/cancel`); load() } catch (err) { setError(err) }
  }

  return (
    <Layout title="출석 인정" actions={<button className="btn btn-primary" onClick={toggleForm}>{formOpen ? '닫기' : '+ 신청'}</button>}>
      {done && <div className="alert alert-ok">{done}</div>}
      <ErrorBox error={error} />

      {formOpen && (
        <form className="card req-form" onSubmit={submit}>
          <div className="grid2">
            <label>수업
              <select className="input" value={courseId} required onChange={(e) => { setCourseId(e.target.value); setSessionId('') }}>
                <option value="">선택</option>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
              </select>
            </label>
            <label>날짜
              <select className="input" value={sessionId} required disabled={!courseId || !options}
                onChange={(e) => setSessionId(e.target.value)}>
                <option value="">{courseId && options && dates.length === 0 ? '신청할 날짜가 없어요' : '선택'}</option>
                {dates.map((o) => <option key={o.sessionId} value={o.sessionId}>{dateLabel(o)}</option>)}
              </select>
            </label>
          </div>

          {option && (
            <div className="segmented req-types">
              <button type="button" className={type === 'EXCUSE' ? 'on' : ''} disabled={!can.excuse} onClick={() => setType('EXCUSE')}>
                공결 <small>사유 있는 결석</small>
              </button>
              <button type="button" className={type === 'CORRECTION' ? 'on' : ''} disabled={!can.correction} onClick={() => setType('CORRECTION')}>
                출석 정정 <small>출석했는데 잘못 처리됨</small>
              </button>
            </div>
          )}

          {type && (
            <>
              <textarea className="input" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required
                aria-label="사유"
                placeholder={type === 'EXCUSE' ? '사유를 적어 주세요 (예: 독감으로 병원 진료)' : '어떻게 된 건지 적어 주세요 (예: 13:02에 QR을 찍었어요)'} />
              <div className="req-form-foot">
                <input ref={fileRef} type="file" accept="image/*,application/pdf" onChange={pickFile} hidden />
                <button type="button" className="link-btn small req-file" onClick={() => fileRef.current?.click()}>
                  {file ? <><Icon name="paperclip-primary" size={14} /> {file.name}</> : '+ 증빙 사진 첨부(선택)'}
                </button>
                <button className="btn btn-primary" disabled={busy || !reason.trim()}>{busy ? '보내는 중…' : '신청하기'}</button>
              </div>
            </>
          )}
        </form>
      )}

      {!list ? <Loading /> : list.length === 0 ? (
        <Empty>신청한 내역이 없어요</Empty>
      ) : (
        <div className="list">
          {list.map((r) => (
            <RequestCard key={r.id} r={r} open={openId === r.id} onToggle={() => setOpenId(openId === r.id ? null : r.id)} onError={setError}>
              {r.status === 'PENDING' && (
                <div className="req-actions">
                  <button type="button" className="btn btn-sm" onClick={() => cancel(r.id)}>신청 취소</button>
                </div>
              )}
            </RequestCard>
          ))}
        </div>
      )}
    </Layout>
  )
}
