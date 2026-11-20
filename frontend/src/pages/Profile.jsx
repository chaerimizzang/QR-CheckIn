import { useAuth } from '../auth'
import AdminProfile from './admin/AdminProfile'
import StudentProfile from './student/Profile'

/** /profile — 역할에 따라 관리자·학생 프로필 */
export default function Profile() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <AdminProfile /> : <StudentProfile />
}
