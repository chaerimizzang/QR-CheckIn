import { useEffect, useState } from 'react'

const DISMISS_KEY = 'qr-install-dismissed'

function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true
}

function isIos() {
  const ua = navigator.userAgent
  return /iphone|ipad|ipod/i.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

/**
 * D-20 앱처럼 설치 안내.
 * - 안드로이드·PC 크롬: 브라우저가 주는 설치 이벤트로 [앱으로 설치] 버튼
 * - 아이폰·아이패드: 설치 버튼이 없어서 '공유 → 홈 화면에 추가' 방법을 안내(iOS 16.4 이상이면 알림도 가능)
 * 이미 앱으로 열었거나 [닫기]를 누르면 보이지 않는다.
 */
export default function InstallPrompt() {
  const [deferred, setDeferred] = useState(() => window.__qrInstallEvent || null)
  const [hidden, setHidden] = useState(() => {
    try { return isStandalone() || localStorage.getItem(DISMISS_KEY) === '1' } catch { return isStandalone() }
  })

  useEffect(() => {
    const onReady = () => setDeferred(window.__qrInstallEvent)
    const onInstalled = () => setHidden(true)
    window.addEventListener('qr-install-ready', onReady)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('qr-install-ready', onReady)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  function dismiss() {
    try { localStorage.setItem(DISMISS_KEY, '1') } catch { /* 저장 못 해도 이번엔 숨김 */ }
    setHidden(true)
  }

  async function install() {
    deferred.prompt()
    const { outcome } = await deferred.userChoice
    window.__qrInstallEvent = null
    setDeferred(null)
    if (outcome === 'accepted') setHidden(true)
  }

  if (hidden) return null
  if (deferred) {
    return (
      <div className="card install-card">
        <div>
          <b>QR 출석을 앱처럼 설치해 보세요</b>
          <div className="muted small">홈 화면 아이콘으로 바로 열 수 있어요</div>
        </div>
        <div className="install-actions">
          <button className="btn btn-primary btn-sm" onClick={install}>설치</button>
          <button className="btn btn-sm" onClick={dismiss}>닫기</button>
        </div>
      </div>
    )
  }
  if (isIos()) {
    return (
      <div className="card install-card">
        <div>
          <b>홈 화면에 추가하면 앱처럼 쓸 수 있어요</b>
          <div className="muted small">Safari 아래쪽 공유 버튼(□↑) → ‘홈 화면에 추가’</div>
        </div>
        <div className="install-actions">
          <button className="btn btn-sm" onClick={dismiss}>닫기</button>
        </div>
      </div>
    )
  }
  return null
}
