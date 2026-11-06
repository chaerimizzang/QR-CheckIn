import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth'
import './styles.css'

createRoot(document.getElementById('root')).render(
  // StrictMode 는 개발 중 카메라(html5-qrcode)를 두 번 켜는 문제가 있어 쓰지 않는다
  <BrowserRouter>
    <AuthProvider>
      <App />
    </AuthProvider>
  </BrowserRouter>,
)
