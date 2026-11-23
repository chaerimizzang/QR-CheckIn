import { useEffect, useState } from 'react'
import { get } from '../../api'
import { ErrorBox, Layout, Loading } from '../../components/ui'
import AccountCard from '../../components/AccountCard'
import { PushCard } from '../../components/PushCard'

/** 학생 프로필: 내 정보 · 계정 · 수업 알림 (수업별 출석은 메뉴 [출석 현황]) */
export default function StudentProfile() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/me/profile').then(setData).catch(setError) }, [])

  return (
    <Layout back="/student" title="프로필">
      <ErrorBox error={error} />
      {!data ? (!error && <Loading />) : (
        <>
          <div className="card profile-card">
            <div className="profile-avatar" aria-hidden="true">{data.name?.slice(0, 1)}</div>
            <div className="profile-info">
              <div className="profile-name">{data.name}</div>
              <div className="muted small">
                {data.department && `${data.department} · `}{data.studentNo ? `학번 ${data.studentNo}` : '학번 미입력'} · {data.email}
              </div>
            </div>
          </div>

          <AccountCard name={data.name} email={data.email} department={data.department}
            onNameChange={(name) => setData({ ...data, name })}
            onDepartmentChange={(department) => setData({ ...data, department })} />
          <PushCard />
        </>
      )}
    </Layout>
  )
}
