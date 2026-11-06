/**
 * 브라우저 위치를 받는다. 첫 값은 Wi-Fi·기지국 기반이라 부정확한 경우가 많아서,
 * maxWaitMs 동안 계속 받아 보며 가장 정확한 값을 고른다(targetAccuracy 이하가 나오면 바로 끝).
 * onProgress(현재 최고 정확도) 로 진행 상황을 알려 준다.
 */
export function getPosition({ targetAccuracy = 30, maxWaitMs = 8000, onProgress } = {}) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(locationError('UNSUPPORTED'))
      return
    }
    if (!window.isSecureContext) {
      reject(locationError('INSECURE'))
      return
    }
    let best = null
    let watchId = null
    let timer = null
    const finish = () => {
      navigator.geolocation.clearWatch(watchId)
      clearTimeout(timer)
      resolve(best)
    }
    watchId = navigator.geolocation.watchPosition(
      (p) => {
        const cur = { latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy }
        if (!best || cur.accuracy < best.accuracy) {
          best = cur
          onProgress?.(Math.round(best.accuracy))
        }
        if (best.accuracy <= targetAccuracy) finish()
      },
      (e) => {
        if (best) { finish(); return } // 이미 받은 값이 있으면 그걸 쓴다
        navigator.geolocation.clearWatch(watchId)
        clearTimeout(timer)
        reject(locationError(e.code === 1 ? 'LOCATION_DENIED' : e.code === 3 ? 'TIMEOUT' : 'UNAVAILABLE'))
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    )
    // 시간이 다 되면 그때까지의 최고값으로 끝낸다(아직 하나도 없으면 계속 기다리다 timeout 오류)
    timer = setTimeout(() => { if (best) finish() }, maxWaitMs)
  })
}

/** 장소 지정에 쓰기엔 너무 부정확한 기준(m) */
export const POOR_ACCURACY_M = 100

const LOCATION_MESSAGES = {
  LOCATION_DENIED: ['위치 권한이 꺼져 있어요', '브라우저 설정에서 이 사이트의 위치 권한을 허용한 뒤 새로고침해 주세요'],
  TIMEOUT: ['위치를 가져오는 데 시간이 너무 오래 걸렸어요', '창가로 이동하거나 Wi-Fi를 켠 뒤 다시 시도해 주세요'],
  UNAVAILABLE: ['현재 위치를 알 수 없어요', '휴대폰의 위치 서비스가 켜져 있는지 확인해 주세요'],
  UNSUPPORTED: ['이 브라우저는 위치 기능을 지원하지 않아요', 'Chrome 또는 Safari로 열어 주세요'],
  INSECURE: ['보안 연결(https)이 아니라서 위치를 쓸 수 없어요', 'https 주소로 접속해 주세요'],
}

function locationError(code) {
  const [message, hint] = LOCATION_MESSAGES[code]
  const err = new Error(message)
  err.code = code
  err.detail = { hint }
  return err
}

export const STATUS_LABEL = { PRESENT: '출석', LATE: '지각', ABSENT: '결석' }
export const SESSION_LABEL = { SCHEDULED: '예정', OPEN: '출석 중', CLOSED: '마감' }

export function fmtDateTime(s) {
  if (!s) return '-'
  const d = new Date(s)
  return `${d.getMonth() + 1}/${d.getDate()} (${'일월화수목금토'[d.getDay()]}) ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtTime(s) {
  if (!s) return '-'
  const d = new Date(s)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fmtSeconds(total) {
  const t = Math.max(0, Math.floor(total))
  return `${Math.floor(t / 60)}:${pad(t % 60)}`
}

/** <input type="datetime-local"> 기본값: 다음 정각 */
export function nextHourLocal() {
  const d = new Date()
  d.setHours(d.getHours() + 1, 0, 0, 0)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:00`
}

const pad = (n) => String(n).padStart(2, '0')
