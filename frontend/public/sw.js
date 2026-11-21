// D-20 앱처럼 설치(PWA)용 서비스 워커.
// 출석은 서버 시각·위치로 판정해야 하므로 API(/api)는 절대 캐시하지 않는다.
// 앱을 빨리 열기 위해:
//  - /assets(파일 이름에 해시가 붙어 내용이 바뀌지 않음)·/icons 는 한 번 받으면 캐시에서 바로 꺼낸다
//  - 화면(index.html)은 캐시에 있으면 바로 보여 주고, 뒤에서 새 버전을 받아 둔다(다음에 열 때 반영)
//    캐시에 없을 때만 네트워크를 기다린다
const CACHE = 'qr-checkin-v3'
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

const offline = () => new Response('인터넷에 연결되어 있지 않아요', {
  status: 503,
  headers: { 'Content-Type': 'text/plain; charset=utf-8' },
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/h2-console')) return

  // 해시가 붙은 화면 파일·아이콘: 캐시 우선
  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(caches.open(CACHE).then(async (cache) => {
      const hit = await cache.match(req)
      if (hit) return hit
      try {
        const res = await fetch(req)
        if (res.ok) cache.put(req, res.clone())
        return res
      } catch {
        return offline()
      }
    }))
    return
  }

  // 화면(페이지 이동): 캐시된 화면을 바로 보여 주고 뒤에서 새로 받아 둔다
  if (req.mode === 'navigate') {
    event.respondWith(caches.open(CACHE).then(async (cache) => {
      const cached = await cache.match('/index.html')
      const network = fetch('/index.html', { cache: 'no-cache' })
        .then((res) => { if (res.ok) cache.put('/index.html', res.clone()); return res })
        .catch(() => null)
      if (cached) {
        event.waitUntil(network)
        return cached
      }
      return (await network) || offline()
    }))
    return
  }

  // 그 밖(manifest 등): 네트워크 우선, 안 되면 캐시
  event.respondWith(fetch(req).catch(async () => (await caches.match(req)) || offline()))
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
