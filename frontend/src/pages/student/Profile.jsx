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
    <Layout back="/student" title="프로필" hero={data && (
      <div className="profile-hero">
        <div className="avatar" aria-hidden="true">{data.name?.slice(0, 1)}</div>
        <div style={{ minWidth: 0 }}>
          <div className="name">{data.name}</div>
          <div className="meta">{data.department && `${data.department} · `}{data.studentNo ? `학번 ${data.studentNo}` : '학번 미입력'}</div>
        </div>
      </div>
    )}>
      <ErrorBox error={error} />
      {!data ? (!error && <Loading />) : (
        <>

          <AccountCard name={data.name} email={data.email} department={data.department}
            onNameChange={(name) => setData({ ...data, name })}
            onDepartmentChange={(department) => setData({ ...data, department })} />
          <PushCard />
        </>
      )}
    </Layout>
  )
}
