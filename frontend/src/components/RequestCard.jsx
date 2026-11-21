import { useState } from 'react'
import { session } from '../api'
import { fmtDateTime } from '../util'
import Icon from './Icon'
import { StatusBadge } from './ui'

export const TYPE_LABEL = { EXCUSE: '공결', CORRECTION: '출결 정정' }
export const REQUEST_STATUS = {
  PENDING: { label: '대기', cls: 'req-pending' },
  APPROVED: { label: '승인', cls: 'req-approved' },
  REJECTED: { label: '반려', cls: 'req-rejected' },
  CANCELED: { label: '취소', cls: 'req-canceled' },
}

/** 증빙 파일은 로그인 토큰이 필요해서 받아서 새 창으로 연다 */
async function openEvidence(id, setError) {
  const win = window.open('', '_blank')
  try {
    const res = await fetch(`/api/v1/requests/${id}/evidence`, { headers: { Authorization: `Bearer ${session.token()}` } })
    if (!res.ok) throw new Error('파일을 열지 못했어요')
    const url = URL.createObjectURL(await res.blob())
    if (win) win.location.href = url
    else window.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    win?.close()
    setError?.(e)
  }
}

/** 신청 한 건(학생·교수 공통). children 자리에 버튼(승인·반려·취소)을 넣는다 */
export default function RequestCard({ r, showStudent, children, onError }) {
  const [opening, setOpening] = useState(false)
  const st = REQUEST_STATUS[r.status]
  return (
    <div className="card req-card">
      <div className="req-head">
        <span className={`req-status ${st.cls}`}>{st.label}</span>
        <span className="req-type">{TYPE_LABEL[r.type]}</span>
        <span className="muted small req-when">{fmtDateTime(r.createdAt)} 신청</span>
      </div>
      {showStudent && <div className="item-title">{r.studentName} <span className="muted small">{r.studentNo}</span></div>}
      <div className={showStudent ? 'small' : 'item-title'}>
        {r.courseTitle} · {r.sessionNo}회차 <span className="muted small">{fmtDateTime(r.scheduledAt)}</span>
      </div>
      <div className="req-change small">
        <StatusBadge status={r.statusAtRequest} /> <span className="muted">→</span> <StatusBadge status={r.requestedStatus} />
        {r.status === 'APPROVED' && <span className="muted"> 로 바뀌었어요</span>}
      </div>
      <p className="req-reason">{r.reason}</p>
      {r.hasEvidence && (
        <button type="button" className="link-btn small" disabled={opening}
          onClick={async () => { setOpening(true); await openEvidence(r.id, onError); setOpening(false) }}>
          <Icon name="paperclip-primary" size={14} /> 증빙 파일 보기
        </button>
      )}
      {r.reviewedAt && r.status !== 'CANCELED' && (
        <div className="req-review small">
          <b>{r.reviewerName ?? '교수'}</b> · {fmtDateTime(r.reviewedAt)}
          {r.reviewComment && <div>{r.reviewComment}</div>}
        </div>
      )}
      {children}
    </div>
  )
}

