import { useRef, useState } from 'react'
import { download, upload } from '../api'

/** 엑셀에서 열어도 한글이 깨지지 않는 CSV 양식(UTF-8 BOM) */
function downloadTemplate() {
  const text = '﻿학번,이름,연락처\n20240001,홍길동,010-1234-5678\n'
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }))
  a.download = '수강생_등록_양식.csv'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/**
 * 교육 상세 '수강생' 탭 — D-02 CSV로 한 번에 등록 · D-10 출결부/출석률 내려받기
 * CSV: 첫 줄 '학번,이름,연락처'. 가입한 학생만, 학번·이름이 가입 정보와 같아야 등록된다.
 */
export default function StudentCsvTools({ course, onDone, onError }) {
  const fileRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)

  async function onFile(e) {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일을 다시 골라도 동작하게
    if (!file) return
    setBusy(true); setResult(null); onError(null)
    try {
      const r = await upload(`/courses/${course.id}/enrollments/csv`, file)
      setResult(r)
      if (r.successCount) await onDone()
    } catch (err) {
      onError(err)
    } finally {
      setBusy(false)
    }
  }

  async function exportCsv(path, name) {
    onError(null)
    try { await download(`/courses/${course.id}${path}`, `${course.title}_${name}.csv`) } catch (err) { onError(err) }
  }

  return (
    <>
      <div className="card">
        <div className="card-head">
          <h2 className="card-title">CSV로 한 번에 등록</h2>
          <button type="button" className="link-btn small" onClick={downloadTemplate}>양식 받기</button>
        </div>
        <p className="muted small">
          첫 줄은 <b>학번,이름,연락처</b>예요. 회원가입한 학생만 등록되고, 학번·이름이 가입 정보와 같아야 해요.
          엑셀에서 저장한 CSV도 그대로 올릴 수 있어요.
        </p>
        <input ref={fileRef} type="file" accept=".csv,text/csv" onChange={onFile} hidden />
        <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => fileRef.current?.click()}>
          {busy ? '올리는 중…' : 'CSV 파일 선택'}
        </button>

        {result && (
          <div className="csv-result">
            <div className={result.failureCount ? 'alert alert-error' : 'alert alert-ok'}>
              {result.successCount}명 등록{result.failureCount ? ` · ${result.failureCount}명 실패` : '했어요'}
            </div>
            {result.failures.length > 0 && (
              <div className="table-card csv-fail">
                <table>
                  <thead><tr><th>줄</th><th>학번</th><th>실패 이유</th></tr></thead>
                  <tbody>
                    {result.failures.map((f, i) => (
                      <tr key={i}><td>{f.row}</td><td>{f.studentNo || '—'}</td><td>{f.reason}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      <div className="card export-row">
        <div>
          <h2 className="card-title">내려받기</h2>
          <p className="muted small" style={{ margin: 0 }}>엑셀에서 바로 열 수 있는 CSV 파일이에요.</p>
        </div>
        <div className="btn-row">
          <button type="button" className="btn btn-sm" onClick={() => exportCsv('/attendance/export', '출결부')}>출결부</button>
          <button type="button" className="btn btn-sm" onClick={() => exportCsv('/attendance/rate/export', '출석률')}>출석률</button>
        </div>
      </div>
    </>
  )
}
