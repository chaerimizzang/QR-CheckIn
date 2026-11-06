import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { get } from '../../api'
import { fmtSeconds } from '../../util'

/**
 * A-05 QR 출석 진행(프로젝터 화면).
 * 서버가 알려 준 nextRefreshMs 뒤에 새 토큰을 받아 QR 을 다시 그린다(기본 15초).
 * QR 주소는 지금 열려 있는 주소(localhost 또는 https 터널)를 기준으로 만든다.
 */
export default function QrScreen() {
  const { sessionId } = useParams()
  const [qr, setQr] = useState(null)
  const [closed, setClosed] = useState(false)
  const [error, setError] = useState(null)
  const [now, setNow] = useState(Date.now())
  const timer = useRef(null)

  const fetchQr = useCallback(async () => {
    try {
      const data = await get(`/sessions/${sessionId}/qr`)
      setQr({ ...data, receivedAt: Date.now() })
      setError(null)
      timer.current = setTimeout(fetchQr, data.nextRefreshMs + 300)
    } catch (err) {
      if (err.code === 'SESSION_CLOSED') {
        setClosed(true)
      } else {
        setError(err)
        timer.current = setTimeout(fetchQr, 3000) // 일시적 오류면 3초 뒤 재시도
      }
    }
  }, [sessionId])

  useEffect(() => {
    fetchQr()
    const tick = setInterval(() => setNow(Date.now()), 250)
    return () => { clearTimeout(timer.current); clearInterval(tick) }
  }, [fetchQr])

  const elapsed = qr ? (now - qr.receivedAt) / 1000 : 0
  const remaining = qr ? qr.remainingSeconds - elapsed : 0
  const nextIn = qr ? Math.max(0, qr.nextRefreshMs / 1000 - elapsed) : 0
  const url = qr ? `${window.location.origin}/check-in?t=${encodeURIComponent(qr.token)}` : ''

  function fullscreen() {
    document.documentElement.requestFullscreen?.()
  }

  return (
    <div className="qr-page">
      <div className="qr-top">
        <Link to={`/admin/sessions/${sessionId}`} className="link-btn">← 회차 화면</Link>
        <button className="link-btn" onClick={fullscreen}>전체 화면</button>
      </div>

      {closed ? (
        <div className="qr-closed">
          <h1>출석이 마감되었어요</h1>
          <p className="muted">출석하지 못한 수강생은 결석으로 처리됐어요. 늦게 온 학생은 회차 화면에서 지각으로 바꿀 수 있어요.</p>
          <Link className="btn btn-primary btn-lg" to={`/admin/sessions/${sessionId}`}>출결 현황 보기</Link>
        </div>
      ) : !qr ? (
        <div className="qr-closed">{error ? <><h2>{error.message}</h2><p className="muted">{error.detail?.hint}</p></> : '불러오는 중…'}</div>
      ) : (
        <div className="qr-main">
          <div className="qr-box">
            <QRCodeSVG value={url} size={1024} level="M" marginSize={2} style={{ width: '100%', height: 'auto' }} />
            <div className="qr-progress"><div style={{ width: `${(nextIn / qr.refreshSeconds) * 100}%` }} /></div>
          </div>
          <div className="qr-side">
            <div className="qr-stat">
              <div className="muted">남은 시간</div>
              <div className={`qr-big ${remaining < 60 ? 'warn' : ''}`}>{fmtSeconds(remaining)}</div>
            </div>
            <div className="qr-stat">
              <div className="muted">출석</div>
              <div className="qr-big">{qr.presentCount}<span className="qr-of"> / {qr.enrolledCount}</span></div>
            </div>
            <p className="muted small">휴대폰 카메라나 앱의 [QR 스캔]으로 찍으세요.<br />QR은 {qr.refreshSeconds}초마다 바뀌어요.</p>
            {error && <p className="warn small">연결이 불안정해요. 다시 시도하는 중…</p>}
          </div>
        </div>
      )}
    </div>
  )
}
