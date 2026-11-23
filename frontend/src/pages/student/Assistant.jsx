import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../../api'
import { Layout } from '../../components/ui'
import Icon from '../../components/Icon'

const MAX_LEN = 300
const HISTORY_TURNS = 6
const STEPS = ['출결 확인 중…', '계산 중…', '답변 준비 중…']

/**
 * AI 출결 도우미(학생). 대화는 이 화면에만 두고(새로고침·로그아웃 시 사라짐) 다음 질문 때 최근 6턴을 함께 보낸다.
 * 숫자는 서버가 계산한 근거 카드(facts)를 그대로 보여 주고, 버튼은 서버가 준 내부 경로만 쓴다.
 */
export default function Assistant() {
  const [status, setStatus] = useState(null)
  const [messages, setMessages] = useState([]) // {role:'user', text} | {role:'assistant', reply} | {role:'error', error, retry}
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [step, setStep] = useState(0)
  const bottomRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    get('/assistant/status').then(setStatus).catch(() => setStatus({ available: false, presets: [] }))
  }, [])

  // 진행 문구를 바꿔 가며 보여 준다
  useEffect(() => {
    if (!busy) return undefined
    setStep(0)
    const t = setInterval(() => setStep((s) => Math.min(s + 1, STEPS.length - 1)), 1500)
    return () => clearInterval(t)
  }, [busy])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, busy])

  async function ask(text, preset) {
    const message = text.trim()
    if (!message || busy) return
    const history = toHistory(messages)
    setMessages((m) => [...m.filter((x) => x.role !== 'error'), { role: 'user', text: message }])
    setInput('')
    setBusy(true)
    try {
      const reply = await post('/assistant/messages', { message, history, ...(preset ? { preset } : {}) })
      setMessages((m) => [...m, { role: 'assistant', reply }])
    } catch (error) {
      // 실패한 질문은 대화에서 빼고 다시 시도할 수 있게 둔다
      setMessages((m) => [...m.slice(0, -1), { role: 'error', error, retry: { text: message, preset } }])
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  function onSubmit(e) {
    e.preventDefault()
    ask(input)
  }

  function onKeyDown(e) {
    // Enter 로 보내고 Shift+Enter 는 줄바꿈(한글 조합 중에는 보내지 않음)
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      ask(input)
    }
  }

  const presets = status?.presets ?? []
  const empty = messages.length === 0

  return (
    <Layout back="/student" title="AI 출결 도우미"
      actions={!empty && (
        <button type="button" className="btn btn-sm" onClick={() => setMessages([])} disabled={busy}>새 대화</button>
      )}>
      {status && !status.available && (
        <div className="alert alert-info small">지금은 아래 추천 질문에만 답할 수 있어요.</div>
      )}

      <div className="chat" aria-live="polite">
        {empty && (
          <div className="chat-intro">
            <Icon name="sparkles-active" size={28} />
            <div className="muted small">내 출결 기록과 이 서비스의 출결 규칙으로 답해요.</div>
            <div className="preset-list">
              {presets.map((p) => (
                <button key={p.id} type="button" className="preset" onClick={() => ask(p.question, p.id)} disabled={busy}>
                  {p.question}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => {
          if (m.role === 'user') return <div key={i} className="bubble-user">{m.text}</div>
          if (m.role === 'assistant') return <Reply key={i} reply={m.reply} />
          return (
            <div key={i} className="alert alert-error chat-error">
              <strong>{m.error.message}</strong>
              <div className="btn-row">
                <button type="button" className="btn btn-sm" onClick={() => ask(m.retry.text, m.retry.preset)}>다시 시도</button>
                <Link to="/attendance" className="btn btn-sm">출결 현황 보기</Link>
              </div>
            </div>
          )
        })}

        {busy && <div className="reply reply-busy muted small">{STEPS[step]}</div>}
        <div ref={bottomRef} />
      </div>

      <form className="chat-input" onSubmit={onSubmit}>
        <textarea ref={inputRef} className="input" rows={1} value={input} maxLength={MAX_LEN}
          placeholder="예: 자료구조에서 한 번 더 지각하면?" aria-label="질문"
          onChange={(e) => setInput(e.target.value)} onKeyDown={onKeyDown} disabled={busy} />
        <button type="submit" className="btn btn-primary send-btn" disabled={busy || !input.trim()} aria-label="보내기">
          <Icon name="send-white" size={20} />
        </button>
      </form>
      {input.length > MAX_LEN - 50 && <div className="muted small right">{input.length}/{MAX_LEN}</div>}
    </Layout>
  )
}

/** 답변 하나: 문장 + 근거 카드 + 이동 버튼 + 기준 시각·규칙 */
function Reply({ reply }) {
  return (
    <div className="reply">
      <p className="reply-text">{reply.answer}</p>
      {reply.facts?.length > 0 && (
        <dl className="fact-list">
          {reply.facts.map((f) => (
            <div key={f.label} className="fact">
              <dt>{f.label}</dt>
              <dd>{f.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {reply.actions?.length > 0 && (
        <div className="btn-row reply-actions">
          {reply.actions.map((a) => (
            <Link key={a.path} to={safePath(a.path)} className="btn btn-sm">{a.label}</Link>
          ))}
        </div>
      )}
      <div className="reply-foot">
        {fmtTime(reply.asOf)} 기준{reply.policy && ` · ${reply.policy.description}`}
        {reply.source === 'TEMPLATE' && ' · AI 연결이 안 돼 기본 답변으로 보여 드려요'}
      </div>
    </div>
  )
}

/** 서버가 만든 내부 경로만 쓴다(혹시 다른 값이 오면 출결 현황으로) */
function safePath(path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : '/attendance'
}

function toHistory(messages) {
  return messages
    .filter((m) => m.role === 'user' || m.role === 'assistant')
    .map((m) => (m.role === 'user' ? { role: 'user', text: m.text } : { role: 'assistant', text: m.reply.answer.slice(0, 1500) }))
    .slice(-HISTORY_TURNS)
}

function fmtTime(s) {
  if (!s) return ''
  const d = new Date(s)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
