import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { homePath, useAuth } from './auth'
import Login from './pages/Login'
import Signup from './pages/Signup'
import CheckIn from './pages/CheckIn'
import Courses from './pages/admin/Courses'
import CourseDetail from './pages/admin/CourseDetail'
import SessionDetail from './pages/admin/SessionDetail'
import StudentHome from './pages/student/Home'
import MyAttendance from './pages/student/MyAttendance'

// 카메라·QR 라이브러리는 그 화면을 열 때만 내려받는다
const QrScreen = lazy(() => import('./pages/admin/QrScreen'))
const Scan = lazy(() => import('./pages/student/Scan'))

/** 로그인이 필요한 화면. 로그인 후 원래 주소로 돌아오도록 next 를 붙인다 */
function RequireRole({ role, children }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) {
    const next = location.pathname + location.search
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />
  }
  if (role && user.role !== role) return <Navigate to={homePath(user)} replace />
  return children
}

export default function App() {
  const { user } = useAuth()
  return (
    <Suspense fallback={<div className="empty">불러오는 중…</div>}>
    <Routes>
      <Route path="/" element={<Navigate to={user ? homePath(user) : '/login'} replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/signup" element={<Signup />} />

      {/* 관리자 A-02 ~ A-06 */}
      <Route path="/admin" element={<RequireRole role="ADMIN"><Courses /></RequireRole>} />
      <Route path="/admin/courses/:courseId" element={<RequireRole role="ADMIN"><CourseDetail /></RequireRole>} />
      <Route path="/admin/sessions/:sessionId" element={<RequireRole role="ADMIN"><SessionDetail /></RequireRole>} />
      <Route path="/admin/sessions/:sessionId/qr" element={<RequireRole role="ADMIN"><QrScreen /></RequireRole>} />

      {/* 수강생 S-02 ~ S-04 */}
      <Route path="/student" element={<RequireRole role="STUDENT"><StudentHome /></RequireRole>} />
      <Route path="/student/courses/:courseId" element={<RequireRole role="STUDENT"><MyAttendance /></RequireRole>} />
      <Route path="/scan" element={<RequireRole role="STUDENT"><Scan /></RequireRole>} />
      {/* QR 에 담긴 주소. 기본 카메라 앱으로 찍어도 여기로 열린다 */}
      <Route path="/check-in" element={<RequireRole role="STUDENT"><CheckIn /></RequireRole>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </Suspense>
  )
}
