import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../../api'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'

/** A-02 교육 목록 */
export default function Courses() {
  const [courses, setCourses] = useState(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState(null)
  const [showForm, setShowForm] = useState(false)

  const load = () => get('/courses').then(setCourses).catch(setError)
  useEffect(() => { load() }, [])

  async function create(e) {
    e.preventDefault()
    setError(null)
    try {
      await post('/courses', { title })
      setTitle('')
      setShowForm(false)
      load()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Layout
      title="내 교육"
      actions={<button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>+ 교육 만들기</button>}
    >
      <ErrorBox error={error} />
      {showForm && (
        <form className="card row-form" onSubmit={create}>
          <input placeholder="교육 이름 (예: 데이터베이스 실습)" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
          <button className="btn btn-primary">만들기</button>
        </form>
      )}
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 만든 교육이 없어요. 오른쪽 위 <b>교육 만들기</b>로 시작해 보세요.</Empty>
      ) : (
        <div className="list">
          {courses.map((c) => (
            <Link key={c.id} to={`/admin/courses/${c.id}`} className="card list-item">
              <div>
                <div className="item-title">{c.title}</div>
                <div className="muted small">
                  수강생 {c.studentCount}명{c.startDate && ` · ${c.startDate} ~ ${c.endDate || ''}`}
                </div>
              </div>
              <span className="chevron">›</span>
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
