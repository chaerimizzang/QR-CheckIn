import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Html5Qrcode } from 'html5-qrcode'
import { Layout } from '../../components/ui'

/** QR 에서 출석 토큰 꺼내기: 출석 URL(…/check-in?t=…) 또는 토큰 문자열 그 자체 */
function extractToken(text) {
  try {
    const u = new URL(text)
    if (u.pathname.endsWith('/check-in') && u.searchParams.get('t')) return u.searchParams.get('t')
  } catch { /* URL 이 아님 */ }
  return /^\d+\.\d+\.[\w-]+$/.test(text) ? text : null
}

/** S-03 QR 스캔(앱 안 카메라) */
export default function Scan() {
  const navigate = useNavigate()
  const scannerRef = useRef(null)
  const doneRef = useRef(false)
  const [error, setError] = useState(null)
  const [wrong, setWrong] = useState(false)

  useEffect(() => {
    const scanner = new Html5Qrcode('qr-reader', { verbose: false })
    scannerRef.current = scanner
    let started = false

    scanner.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: (w, h) => { const s = Math.floor(Math.min(w, h) * 0.75); return { width: s, height: s } } },
      (text) => {
        if (doneRef.current) return
        const token = extractToken(text)
        if (!token) { setWrong(true); return }
        doneRef.current = true
        scanner.stop().catch(() => {}).finally(() => navigate(`/check-in?t=${encodeURIComponent(token)}`))
      },
      () => {}, // 프레임마다 "못 찾음"은 무시
    ).then(() => { started = true }).catch(() => {
      setError(window.isSecureContext
        ? '카메라를 켤 수 없어요. 브라우저에서 카메라 권한을 허용해 주세요.'
        : '보안 연결(https)이 아니라서 카메라를 쓸 수 없어요. https 주소로 접속해 주세요.')
    })

    return () => {
      if (started && !doneRef.current) scanner.stop().catch(() => {})
    }
  }, [navigate])

  return (
    <Layout back="/student" title="QR 스캔">
      <div className="scan-box">
        <div id="qr-reader" />
      </div>
      {error ? (
        <div className="alert alert-error">
          <strong>{error}</strong>
          <div className="small">휴대폰 기본 카메라 앱으로 QR을 찍어도 출석할 수 있어요.</div>
        </div>
      ) : (
        <p className="muted center">강의실 화면의 QR을 네모 안에 맞춰 주세요</p>
      )}
      {wrong && <div className="alert alert-error">출석용 QR이 아니에요. 강의실 화면의 QR을 찍어 주세요.</div>}
    </Layout>
  )
}
