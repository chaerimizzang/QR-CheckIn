import { useState } from 'react'
import { session } from '../api'
import { STATUS_LABEL, fmtDateTime } from '../util'
import Icon from './Icon'

const RESULT_LABEL = { PRESENT: '출석으로', LATE: '지각으로', EXCUSED: '공결로', ABSENT: '결석으로' }

export const REQUEST_STATUS = {
  PENDING: { label: '대기', cls: 'req-pending' },
  APPROVED: { label: '승인', cls: 'req-approved' },
  REJECTED: { label: '반려', cls: 'req-rejected' },
  CANCELED: { label: '취소', cls: 'req-canceled' },
}

/** "11/20 (금)" — 회차 번호 대신 수업 날짜로 보여 준다 */
export function fmtDay(s) {
  if (!s) return '-'
  const d = new Date(s)
  return `${d.getMonth() + 1}/${d.getDate()} (${'일월화수목금토'[d.getDay()]})`
}

/** 증빙 파일은 로그인 토큰이 필요해서 받아서 새 창으로 연다 */
async function openEvidence(id, onError) {
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
    onError?.(e)
  }
}

/**
 * 신청 한 줄. 평소에는 날짜·수업·종류·상태만 보이고, 누르면 사유·증빙·처리 결과와 버튼(children)이 펼쳐진다.
 * showStudent: 교수 화면(누가 신청했는지)
 */
export default function RequestCard({ r, showStudent, open, onToggle, children, onError }) {
  const st = REQUEST_STATUS[r.status]
  const before = r.statusAtRequest ? STATUS_LABEL[r.statusAtRequest] : '미정'
  return (
    <div className={`card req-item${open ? ' open' : ''}`}>
      <button type="button" className="req-summary" onClick={onToggle} aria-expanded={open}>
        <span className={`req-status ${st.cls}`}>{st.label}</span>
        <span className="req-main">
          <span className="req-line1">
            {showStudent ? <b>{r.studentName}</b> : <b>{fmtDay(r.scheduledAt)}</b>}
            {r.status === 'APPROVED' && <span className="req-type">{RESULT_LABEL[r.requestedStatus]} 인정</span>}
          </span>
          <span className="req-line2 muted small">
            {showStudent ? `${fmtDay(r.scheduledAt)} · ${r.courseTitle}` : r.courseTitle}
          </span>
        </span>
        <Icon name="chevron" size={18} className={`chevron req-chevron${open ? ' up' : ''}`} />
      </button>

      {open && (
        <div className="req-detail">
          <dl className="req-dl">
            {showStudent && <><dt>학번</dt><dd>{r.studentNo || '-'}</dd></>}
            <dt>수업</dt><dd>{r.courseTitle} · {fmtDateTime(r.scheduledAt)}</dd>
            <dt>출결</dt><dd>{r.status === 'APPROVED' ? <>{before} → <b>{STATUS_LABEL[r.requestedStatus]}</b></> : before}</dd>
            <dt>사유</dt><dd className="req-reason">{r.reason}</dd>
            {r.hasEvidence && (
              <><dt>증빙</dt><dd>
                <button type="button" className="link-btn small req-file" onClick={() => openEvidence(r.id, onError)}>
                  <Icon name="paperclip-primary" size={14} /> 파일 보기
                </button>
              </dd></>
            )}
            <dt>신청</dt><dd>{fmtDateTime(r.createdAt)}</dd>
            {r.reviewedAt && r.status !== 'CANCELED' && (
              <><dt>{st.label}</dt><dd>{r.reviewerName ?? '교수'} · {fmtDateTime(r.reviewedAt)}{r.reviewComment && <div className="req-comment">{r.reviewComment}</div>}</dd></>
            )}
          </dl>
          {children}
        </div>
      )}
    </div>
  )
}
