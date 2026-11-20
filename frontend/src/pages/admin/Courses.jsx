import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get, post } from '../../api'
import InstallPrompt from '../../components/InstallPrompt'
import { Empty, ErrorBox, Layout, Loading } from '../../components/ui'
import Icon from '../../components/Icon'
import { fmtSchedules } from '../../util'

/** A-02 교육 목록 */
export default function Courses() {
  const [courses, setCourses] = useState(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState(null)
  // 왼쪽 메뉴의 '교육 만들기'는 /admin?new=1 로 와서 입력창이 바로 열린다
  const [params, setParams] = useSearchParams()
  const showForm = params.get('new') === '1'
  const setShowForm = (open) => setParams(open ? { new: '1' } : {}, { replace: true })

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
      <InstallPrompt />
      <ErrorBox error={error} />
      {showForm && (
        <form className="card row-form" onSubmit={create}>
          <input placeholder="교육 이름 (예: 데이터베이스 실습)" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
          <button className="btn btn-primary">만들기</button>
        </form>
      )}
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>아직 만든 교육이 없어요. <b>교육 만들기</b>로 시작해 보세요.</Empty>
      ) : (
        <div className="list">
          {courses.map((c) => (
            <Link key={c.id} to={`/admin/courses/${c.id}`} className="card list-item">
              <div>
                <div className="item-title">{c.title}</div>
                <div className="muted small">
                  수강생 {c.studentCount}명{fmtSchedules(c.schedules) && ` · ${fmtSchedules(c.schedules)}`}
                </div>
              </div>
              <Icon name="chevron" size={20} className="chevron" />
            </Link>
          ))}
        </div>
      )}
    </Layout>
  )
}
