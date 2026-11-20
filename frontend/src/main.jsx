import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth'
import './styles.css'

// D-20 앱처럼 설치: 서비스 워커 등록(빌드된 화면에서만. 개발 서버에서는 캐시가 헷갈리게 하지 않도록 끔)
// 브라우저가 '설치 가능'을 알려 주는 이벤트는 화면보다 먼저 올 수 있어 여기서 잡아 둔다(InstallPrompt 가 사용)
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  window.__qrInstallEvent = e
  window.dispatchEvent(new Event('qr-install-ready'))
})

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('서비스 워커 등록 실패', e))
  })
}

createRoot(document.getElementById('root')).render(
  // StrictMode 는 개발 중 카메라(html5-qrcode)를 두 번 켜는 문제가 있어 쓰지 않는다
  <BrowserRouter>
    <AuthProvider>
      <App />
    </AuthProvider>
  </BrowserRouter>,
)
