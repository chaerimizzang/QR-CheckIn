
import { useEffect, useState } from 'react'
import { get } from '../../api'

export default function AttendanceStatistics({ courseId }) {
  const [statistics, setStatistics] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setStatistics(null)
    setError(null)

    get(`/courses/${courseId}/attendance/statistics`)
      .then(setStatistics)
      .catch(setError)
  }, [courseId])

  if (error) {
    return <div className="alert">통계를 불러오지 못했어요: {error.message}</div>
  }

  if (!statistics) {
    return <div className="card">통계를 불러오는 중...</div>
  }

  return (
    <div>
      <h2>회차별 출결 통계</h2>

      {statistics.sessions.length === 0 ? (
        <div className="card">마감된 회차가 아직 없어요.</div>
      ) : (
        statistics.sessions.map((session) => {
          const total =
            session.presentCount +
            session.lateCount +
            session.absentCount

          const percent = (count) =>
            total > 0 ? (count / total) * 100 : 0

          return (
            <div className="card" key={session.sessionId} style={{ marginBottom: 16 }}>
              <h3>{session.sessionNo}회차</h3>

              <div
                style={{
                  display: 'flex',
                  height: 28,
                  borderRadius: 6,
                  overflow: 'hidden',
                  background: '#e5e7eb'
                }}
              >
                <div style={{ width: `${percent(session.presentCount)}%`, background: '#22c55e' }} />
                <div style={{ width: `${percent(session.lateCount)}%`, background: '#f59e0b' }} />
                <div style={{ width: `${percent(session.absentCount)}%`, background: '#ef4444' }} />
              </div>

              <p>
                출석 {session.presentCount}명 ·
                지각 {session.lateCount}명 ·
                결석 {session.absentCount}명
              </p>
            </div>
          )
        })
      )}

      <h2>결석이 많은 학생</h2>

      {statistics.topAbsentStudents.length === 0 ? (
        <div className="card">결석한 학생이 없어요.</div>
      ) : (
        <div className="card table-card">
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>학번</th>
                <th>결석 횟수</th>
              </tr>
            </thead>
            <tbody>
              {statistics.topAbsentStudents.map((student) => (
                <tr key={student.userId}>
                  <td>{student.name}</td>
                  <td>{student.studentNo || '-'}</td>
                  <td>{student.absentCount}회</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
