import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { post } from '../api'
import { Layout } from '../components/ui'
import { STATUS_LABEL, fmtTime, getPosition } from '../util'

/**
 * QR 에 담긴 주소(/check-in?t=토큰). 열리자마자 위치를 받아 출석 인증을 보낸다(B-07).
 * 실패하면 서버가 보낸 사유와 재시도 방법(B-09)을 보여 준다.
 */
export default function CheckIn() {
  const [params] = useSearchParams()
  const token = params.get('t')
  const [step, setStep] = useState('locating') // locating → sending → done | failed
  const [acc, setAcc] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
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
      setStep('sending')
      const res = await post('/check-in', { token, ...pos })
      setResult(res)
      setStep('done')
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

  return (
    <Layout back="/student">
      <div className="checkin">
        {(step === 'locating' || step === 'sending') && (
          <div className="checkin-state">
            <div className="spinner" />
            <h2>{step === 'locating' ? '현재 위치를 확인하고 있어요' : '출석을 확인하고 있어요'}</h2>
            {step === 'locating' && (acc == null
              ? <p className="muted">위치 권한을 물어보면 <b>허용</b>을 눌러 주세요</p>
              : <p className="muted">정확도 약 {acc}m</p>)}
          </div>
        )}

        {step === 'done' && result && (
          <div className="checkin-state ok">
            <div className="big-icon">✓</div>
            <h2>{STATUS_LABEL[result.status]} 완료</h2>
            <p className="checkin-time">{fmtTime(result.checkedAt)}</p>
            <p className="muted">{result.courseTitle} · {result.sessionNo}회차</p>
            <Link className="btn btn-block" to={`/student/courses/${result.courseId}`}>내 출결 보기</Link>
          </div>
        )}

        {step === 'failed' && error && (
          <div className={`checkin-state ${error.code === 'ALREADY_CHECKED' ? 'ok' : 'fail'}`}>
            <div className="big-icon">{error.code === 'ALREADY_CHECKED' ? '✓' : '!'}</div>
            <h2>{error.message}</h2>
            {error.detail?.hint && <p className="hint">{error.detail.hint}</p>}
            <div className="btn-col">
              {canRetry && <button className="btn btn-primary btn-block" onClick={run}>다시 시도</button>}
              {(needRescan || canRetry) && <Link className="btn btn-block" to="/scan">QR 다시 스캔</Link>}
              <Link className="btn btn-block link-only" to="/student">홈으로</Link>
            </div>
            {error.code && <p className="muted small">오류 코드: {error.code}</p>}
          </div>
        )}
      </div>
    </Layout>
  )
}
