import { get, post, session } from './api'

/**
 * 수업 알림(웹 푸시). 서비스 워커(sw.js)가 알림을 받아 띄운다.
 * - 안드로이드·PC 크롬/엣지: 브라우저와 설치한 앱 모두 가능
 * - 아이폰·아이패드: 홈 화면에 추가한 앱(iOS 16.4+)에서만 가능
 */

function isStandalone() {
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true
}

function isIos() {
  const ua = navigator.userAgent
  return /iphone|ipad|ipod/i.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

/** 'ok' | 'ios-install'(아이폰은 홈 화면 앱에서만) | 'unsupported' */
export function pushSupport() {
  if (isIos() && !isStandalone()) return 'ios-install'
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return 'unsupported'
  return 'ok'
}

async function registration() {
  // 서비스 워커는 배포 빌드에서만 등록된다(main.jsx). 개발 서버(npm run dev)에서는 알림을 시험할 수 없다
  const reg = await Promise.race([
    navigator.serviceWorker.ready,
    new Promise((resolve) => setTimeout(() => resolve(null), 4000)),
  ])
  if (!reg) throw new Error('알림 준비가 안 됐어요. 화면을 새로고침한 뒤 다시 시도해 주세요')
  return reg
}

/** 이 기기에서 알림을 받고 있는지 */
export async function currentSubscription() {
  if (pushSupport() !== 'ok') return null
  const reg = await navigator.serviceWorker.getRegistration()
  return reg ? reg.pushManager.getSubscription() : null
}

function toUint8(base64url) {
  const pad = '='.repeat((4 - (base64url.length % 4)) % 4)
  const raw = atob((base64url + pad).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(raw, (c) => c.charCodeAt(0))
}

/** 알림 켜기: 권한 요청 → 브라우저 구독 → 서버에 저장 */
export async function enablePush() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error(permission === 'denied'
      ? '알림이 차단돼 있어요. 브라우저(또는 휴대폰) 설정에서 이 사이트의 알림을 허용해 주세요'
      : '알림 허용을 눌러야 받을 수 있어요')
  }
  const reg = await registration()
  const { publicKey } = await get('/push/public-key')
  let sub = await reg.pushManager.getSubscription()
  // 서버 키가 바뀌었으면 예전 구독은 버리고 새로 구독
  if (sub && sub.options?.applicationServerKey) {
    const old = btoa(String.fromCharCode(...new Uint8Array(sub.options.applicationServerKey)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    if (old !== publicKey) { await sub.unsubscribe(); sub = null }
  }
  if (!sub) {
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toUint8(publicKey) })
  }
  await post('/push/subscriptions', sub.toJSON())
  return sub
}

/** 알림 끄기(이 기기) */
export async function disablePush() {
  const sub = await currentSubscription()
  if (!sub) return
  await post('/push/subscriptions/delete', { endpoint: sub.endpoint }).catch(() => {})
  await sub.unsubscribe()
}

/** 로그아웃할 때: 다음에 이 기기로 로그인하는 사람이 내 수업 알림을 받지 않게 끊는다 */
export function disablePushOnLogout() {
  const token = session.token()
  currentSubscription().then((sub) => {
    if (!sub) return
    if (token) {
      fetch('/api/v1/push/subscriptions/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ endpoint: sub.endpoint }),
      }).catch(() => {})
    }
    sub.unsubscribe().catch(() => {})
  }).catch(() => {})
}

export const sendTestPush = () => post('/push/test')
