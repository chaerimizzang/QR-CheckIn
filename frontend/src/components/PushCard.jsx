import { useEffect, useState } from 'react'
import { currentSubscription, disablePush, enablePush, pushSupport, sendTestPush } from '../push'
import { useAuth } from '../auth'

const DISMISS_KEY = 'qr-push-banner-dismissed'

/** 알림 켜기·끄기 상태 */
function usePush() {
  const [on, setOn] = useState(null) // null = 확인 중
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState(null) // { ok, text }
  const support = pushSupport()

  useEffect(() => {
    if (support !== 'ok') { setOn(false); return }
    currentSubscription().then((s) => setOn(!!s)).catch(() => setOn(false))
  }, [support])

  async function run(fn, okText) {
    setBusy(true); setMsg(null)
    try { await fn(); if (okText) setMsg({ ok: true, text: okText }) } catch (e) { setMsg({ ok: false, text: e.message }) } finally { setBusy(false) }
  }

  return {
    support, on, busy, msg,
    enable: () => run(async () => { await enablePush(); setOn(true) }, '알림을 켰어요. 수업 10분 전에 알려 드릴게요'),
    disable: () => run(async () => { await disablePush(); setOn(false) }, '이 기기의 알림을 껐어요'),
    test: () => run(async () => {
      const r = await sendTestPush()
      if (!r.sent) throw new Error('보낼 기기가 없어요. 알림을 다시 켜 주세요')
    }, '테스트 알림을 보냈어요. 잠시 뒤에 도착해요'),
  }
}

const IOS_HINT = '아이폰은 Safari [공유] → [홈 화면에 추가]로 앱을 설치한 뒤, 그 앱에서 알림을 켤 수 있어요(iOS 16.4 이상).'

/** 프로필의 [수업 알림] 카드 */
export function PushCard() {
  const { user } = useAuth()
  const p = usePush()
  const who = user?.role === 'ADMIN' ? '맡은 수업이 시작하기 10분 전에' : '수업 시작 10분 전에'

  return (
    <div className="card account-card">
      <h2 className="account-title">수업 알림</h2>
      {p.msg && <div className={`alert ${p.msg.ok ? 'alert-ok' : 'alert-error'}`}>{p.msg.text}</div>}
      <div className="account-rows">
        <div className="account-row">
          <div className="account-value wrap">
            <div>{who} 휴대폰으로 알려 드려요</div>
            <div className="muted small">
              {p.support === 'ios-install' ? IOS_HINT
                : p.support === 'unsupported' ? '이 브라우저는 알림을 지원하지 않아요. 크롬으로 열어 주세요.'
                  : p.on === null ? '확인 중…' : p.on ? '이 기기에서 받는 중' : '이 기기에서 꺼짐'}
            </div>
          </div>
          {p.support === 'ok' && p.on !== null && (
            <button type="button" className={`btn btn-sm${p.on ? '' : ' btn-primary'}`} disabled={p.busy}
              onClick={p.on ? p.disable : p.enable}>{p.on ? '끄기' : '켜기'}</button>
          )}
        </div>
        {p.on && (
          <div className="account-row">
            <div className="account-value muted small">알림이 잘 오는지 확인해 보세요</div>
            <button type="button" className="link-btn small" disabled={p.busy} onClick={p.test}>테스트 알림</button>
          </div>
        )}
      </div>
    </div>
  )
}

/** 홈 화면 위 [수업 알림 켜기] 안내. 이미 켰거나, 닫았거나, 지원하지 않으면 보이지 않는다 */
export function PushBanner() {
  const p = usePush()
  const [hidden, setHidden] = useState(() => {
    try { return localStorage.getItem(DISMISS_KEY) === '1' } catch { return false }
  })
  const denied = typeof Notification !== 'undefined' && Notification.permission === 'denied'
  if (hidden || p.support !== 'ok' || p.on !== false || denied) {
    return p.msg && p.on ? <div className="alert alert-ok">{p.msg.text}</div> : null
  }

  function dismiss() {
    try { localStorage.setItem(DISMISS_KEY, '1') } catch { /* 이번만 숨김 */ }
    setHidden(true)
  }

  return (
    <div className="card install-card push-banner">
      <div>
        <div className="item-title">수업 10분 전에 알림 받기</div>
        <div className="muted small">{p.msg && !p.msg.ok ? p.msg.text : '알림을 켜 두면 수업을 놓치지 않아요'}</div>
      </div>
      <div className="install-actions">
        <button type="button" className="btn btn-primary btn-sm" disabled={p.busy} onClick={p.enable}>켜기</button>
        <button type="button" className="btn btn-sm" onClick={dismiss}>닫기</button>
      </div>
    </div>
  )
}
