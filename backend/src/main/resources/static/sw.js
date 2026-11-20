// D-20 앱처럼 설치(PWA)용 서비스 워커.
// 출석은 서버 시각·위치로 판정해야 하므로 API(/api)는 절대 캐시하지 않는다.
// 화면 파일은 항상 네트워크에서 먼저 받고(파일 이름이 고정이라 오래된 화면이 남지 않도록),
// 인터넷이 끊겼을 때만 마지막으로 받은 화면을 보여 준다.
const CACHE = 'qr-checkin-v2'
const SHELL = ['/', '/index.html', '/manifest.json', '/favicon.svg', '/icons/icon-192.png']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/h2-console')) return

  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok && (req.mode === 'navigate' || url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/'))) {
          const copy = res.clone()
          caches.open(CACHE).then((c) => c.put(req.mode === 'navigate' ? '/index.html' : req, copy))
        }
        return res
      })
      .catch(async () => {
        const cached = await caches.match(req.mode === 'navigate' ? '/index.html' : req)
        return cached || new Response('인터넷에 연결되어 있지 않아요', {
          status: 503,
          headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        })
      }),
  )
})

// 수업 알림(웹 푸시): 서버가 보낸 { title, body, url, tag } 를 알림으로 띄운다
self.addEventListener('push', (event) => {
  let data = {}
  try { data = event.data ? event.data.json() : {} } catch { data = { body: event.data && event.data.text() } }
  event.waitUntil(self.registration.showNotification(data.title || 'QR 출석', {
    body: data.body || '',
    tag: data.tag,
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    data: { url: data.url || '/' },
  }))
})

// 알림을 누르면 열려 있는 앱 창으로 가서 해당 화면을 열고, 없으면 새로 연다
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const w of wins) {
      if (new URL(w.url).origin === self.location.origin) {
        await w.focus()
        return w.navigate(url)
      }
    }
    return self.clients.openWindow(url)
  })())
})
