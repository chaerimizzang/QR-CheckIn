import { useAuth } from '../auth'
import AdminRequests from './admin/AdminRequests'
import StudentRequests from './student/StudentRequests'

/** 메뉴 [출석 인정]: 학생은 신청·내 신청 목록, 교수는 승인·반려 */
export default function Requests() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <AdminRequests /> : <StudentRequests />
}
