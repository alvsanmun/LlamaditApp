// Service worker mínimo: permite instalar la web como app. No guarda nada en caché
// (los datos siempre vienen de Firebase al momento).
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});
