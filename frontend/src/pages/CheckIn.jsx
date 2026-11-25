import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get, post } from '../api'
import { fmtTime, getDeviceId, getPosition } from '../util'

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const dayLabel = (iso) => {
  const d = new Date(iso)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEK[d.getDay()]})`
}

const CheckIcon = () => (
  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#15203a" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
)
const BangIcon = () => (
  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#15203a" strokeWidth="2.6" strokeLinecap="round" aria-hidden="true"><path d="M12 6.5v7" /><path d="M12 17.5h.01" /></svg>
)
const CloseIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffffff" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
)

/** 긴 서버 문구 대신 화면 제목은 짧게(거리 같은 숫자는 아래 카드에) */
const TITLE = { OUT_OF_RANGE: '강의실에서 떨어져 있어요' }

/** 실패 사유별로 한 줄 더 설명 — 대리 출석을 왜 막는지 학생이 알 수 있게 */
const EXTRA = {
  QR_EXPIRED: 'QR은 15초마다 바뀌어요. 사진이나 캡처로 받은 QR로는 출석할 수 없어요.',
  QR_INVALID: '강의실 화면에 띄운 QR만 쓸 수 있어요.',
  DEVICE_ALREADY_USED: '같은 휴대폰으로 두 사람이 출석할 수 없어요.',
}

/**
 * QR 에 담긴 주소(/check-in?t=토큰). 열리자마자 위치를 받아 출석 인증을 보낸다(B-07).
 * 실패하면 서버가 보낸 사유와 재시도 방법(B-09)을 보여 준다. 화면은 네이비 전체 화면(디자인 B·출석 완료).
 */
export default function CheckIn() {
  const [params] = useSearchParams()
  const token = params.get('t')
  const [step, setStep] = useState('locating') // locating → sending → done | failed
  const [acc, setAcc] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [rate, setRate] = useState(null)
  const ran = useRef(false)

  async function run() {
    setError(null)
    setResult(null)
    if (!token) {
      setError({ message: '출석 QR 정보가 없어요', detail: { hint: '강의실 화면의 QR을 다시 스캔해 주세요' } })
      setStep('failed')
      return
    }
    try {
      setStep('locating')
      setAcc(null)
      // 스캔부터 결과까지 10초 이내(N-01)를 위해 최대 6초, 50m 이내가 나오면 바로 보낸다
      const pos = await getPosition({ targetAccuracy: 50, maxWaitMs: 6000, onProgress: setAcc })
      setAcc(Math.round(pos.accuracy))
      setStep('sending')
      const res = await post('/check-in', { token, ...pos, deviceId: getDeviceId() })
      setResult(res)
      setStep('done')
      // 이 수업 출석률(방금 출석 반영) — 실패해도 화면은 그대로
      get('/me/profile').then((p) => setRate(p.courses.find((c) => c.courseId === res.courseId)?.attendanceRate ?? null))
        .catch(() => {})
    } catch (err) {
      setError(err)
      setStep('failed')
    }
  }

  useEffect(() => {
    if (ran.current) return
    ran.current = true
    run()
  }, [])

  // 토큰이 만료되는 오류는 같은 QR 로 재시도해도 소용없으니 '다시 스캔'만 보여 준다
  const needRescan = ['QR_EXPIRED', 'QR_INVALID'].includes(error?.code)
  const canRetry = ['LOW_ACCURACY', 'OUT_OF_RANGE', 'LOCATION_DENIED', 'TIMEOUT', 'UNAVAILABLE', 'NETWORK'].includes(error?.code)
  const already = error?.code === 'ALREADY_CHECKED'
  const d = error?.detail ?? {}

  return (
    <div className="checkin-navy">
      <div className="cn-top">
        <Link to="/student" aria-label="닫기" className="cn-close"><CloseIcon /></Link>
      </div>

      {(step === 'locating' || step === 'sending') && (
        <div className="cn-body">
          <div className="cn-spinner" />
          <h1>{step === 'locating' ? '위치를 확인하고 있어요' : '출석을 확인하고 있어요'}</h1>
          <p className="cn-sub">
            {step === 'locating' && acc == null ? <>위치 권한을 물어보면 <b>허용</b>을 눌러 주세요</>
              : acc != null ? `정확도 약 ${acc}m` : ''}
          </p>
        </div>
      )}

      {step === 'done' && result && (
        <>
          <div className="cn-body">
            <div className={`cn-icon ${result.status === 'LATE' ? 'late' : 'ok'}`}><div><CheckIcon /></div></div>
            <div>
              <h1>{result.status === 'LATE' ? '지각으로 출석했어요' : '출석했어요'}</h1>
              <p className="cn-sub">{result.courseTitle} · {dayLabel(result.checkedAt)}</p>
            </div>
            <div className="cn-card">
              <div><span>체크인 시각</span><b>{fmtTime(result.checkedAt)}</b></div>
              <div><span>위치 확인</span><b>강의실 안{acc != null ? ` · 오차 ${acc}m` : ''}</b></div>
              <div><span>이 수업 출석률</span><b>{rate == null ? '—' : `${Math.round(rate)}%`}</b></div>
            </div>
          </div>
          <div className="cn-actions">
            <Link className="cn-btn" to="/student">확인</Link>
            <Link className="cn-link" to={`/student/courses/${result.courseId}`}>이 수업 출결 보기</Link>
          </div>
        </>
      )}

      {step === 'failed' && error && (
        <>
          <div className="cn-body">
            <div className={`cn-icon ${already ? 'ok' : 'bad'}`}><div>{already ? <CheckIcon /> : <BangIcon />}</div></div>
            <div>
              <h1>{TITLE[error.code] ?? error.message}</h1>
              {d.hint && <p className="cn-sub">{d.hint}</p>}
            </div>
            {(d.distanceM != null || EXTRA[error.code]) && (
              <div className="cn-card">
                {d.distanceM != null && <div><span>강의실까지 거리</span><b className="bad">약 {d.distanceM}m</b></div>}
                {d.radiusM != null && <div><span>출석 인정 범위</span><b>{d.radiusM}m 이내</b></div>}
                {EXTRA[error.code] && <p className="cn-note">{EXTRA[error.code]}</p>}
              </div>
            )}
          </div>
          <div className="cn-actions">
            {(needRescan || canRetry) && <Link className="cn-btn" to="/scan">QR 다시 스캔</Link>}
            {canRetry && <button type="button" className="cn-btn ghost" onClick={run}>이 자리에서 다시 시도</button>}
            {already && <Link className="cn-btn" to="/student">확인</Link>}
            <Link className="cn-link" to="/student">홈으로</Link>
            {error.code && <p className="cn-code">오류 코드: {error.code}</p>}
          </div>
        </>
      )}
    </div>
  )
}

