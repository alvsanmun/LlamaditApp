// LlamaditApp · web del cuidador
// Copyright (C) 2026 alvsanmun — GNU Affero General Public License v3 o posterior.
//
// Gestiona a distancia el móvil de la persona: lee la copia del estado que publica el
// móvil (devices/{id}.state) y le deja órdenes (devices/{id}/commands), que el móvil
// ejecuta. Ver app/src/main/java/com/launcherfacil/app/remote/ y firebase/firestore.rules.
//
// Cada familia usa su propio proyecto de Firebase: la web no lleva ninguno dentro. El
// cuidador pega los datos de su proyecto (se guardan solo en este navegador) y la web le
// enseña un QR con esos datos y una invitación, que el móvil de la persona fotografía.

import { initializeApp } from './vendor/firebase-app.js';
import { getAuth, signInAnonymously, onAuthStateChanged } from './vendor/firebase-auth.js';
import {
  getFirestore, doc, getDoc, setDoc, addDoc, updateDoc, collection, query, where, orderBy, limit,
  onSnapshot, serverTimestamp, arrayRemove, deleteField, Timestamp
} from './vendor/firebase-firestore.js';

const GUIDE_URL = 'https://github.com/alvsanmun/LlamaditApp/blob/main/docs/GESTION-A-DISTANCIA.md';
const INVITE_MINUTES = 15;

// --- Textos (de momento solo en castellano) ---------------------------------------------
const HOME_ITEMS = {
  CLOCK: 'Reloj', BATTERY: 'Batería', CALL: 'Llamar', WHATSAPP: 'Videollamada', DIALPAD: 'Teclado',
  SOS: 'SOS', MESSAGES: 'Mensajes', MISSED_CALLS: 'Llamadas perdidas', GO_HOME: 'Volver a casa',
  WHATSAPP_APP: 'WhatsApp completo', EXERCISES: 'Ejercicios', RADIO: 'Radio',
  CAMERA: 'Cámara', PHOTOS: 'Fotos', NEWS: 'Periódicos'
};
const SIZES = { SMALL: 'Pequeño', MEDIUM: 'Mediano', LARGE: 'Grande' };
const SOS_ICONS = { TEXT: 'Letras SOS', AMBULANCE: 'Ambulancia', CROSS: 'Cruz', SIREN: 'Sirena', HEART: 'Corazón', WARNING: 'Aviso' };
const EXERCISES = {
  MATH: 'Cálculo', PAIRS: 'Parejas', PAINT: 'Pintar por números', WORDS: 'Sopa de letras',
  FACES: '¿Quién es?', SEQUENCE: 'Repetir la secuencia', MONEY: 'Contar dinero', PUZZLE: 'Puzle'
};
const SLEEP = { 30: '30 minutos', 60: '1 hora', 120: '2 horas', 180: '3 horas', 0: 'Nunca' };
const EVENTS = {
  sos: () => '¡Ha pulsado el SOS!',
  goHome: () => 'Ha pulsado Volver a casa',
  arrived: () => 'Ha llegado a casa',
  lowBattery: e => `Batería baja (${e.percent} %)`,
  pillsTaken: e => `Se ha tomado las pastillas de las ${e.time}`,
  pillsMissed: e => `No ha confirmado las pastillas de las ${e.time}`,
  pillsSkipped: e => e.total > 1 && e.skipped < e.total
    ? `No se ha tomado ${e.skipped} de ${e.total} pastillas de las ${e.time}`
    : `Ha dicho que no se toma las pastillas de las ${e.time}`,
  linked: e => `Se ha vinculado ${e.name || 'un móvil'}`
};

// Periódicos que puede ver (los mismos que app/.../news/News.kt).
const PAPER_GROUPS = [
  ['📰 Actualidad', { 'elpais': 'El País', 'elmundo': 'El Mundo', 'abc': 'ABC', 'lavanguardia': 'La Vanguardia', '20minutos': '20minutos', 'eldiario': 'elDiario.es', 'elconfidencial': 'El Confidencial', 'larazon': 'La Razón', 'europapress': 'Europa Press', 'elespanol': 'El Español', 'huffpost': 'El HuffPost', 'okdiario': 'OKDIARIO', 'libertaddigital': 'Libertad Digital', 'elindependiente': 'El Independiente', 'infolibre': 'infoLibre', 'lasexta': 'laSexta', 'antena3': 'Antena 3 Noticias', 'newtral': 'Newtral', 'eldebate': 'El Debate', 'cope': 'COPE' }],
  ['⚽ Deportes', { 'marca': 'Marca', 'as': 'AS', 'mundodeportivo': 'Mundo Deportivo', 'sport': 'Sport', 'relevo': 'Relevo' }],
  ['💶 Economía', { 'cincodias': 'Cinco Días', 'elblogsalmon': 'El Blog Salmón' }],
  ['💻 Tecnología', { 'xataka': 'Xataka', 'genbeta': 'Genbeta', 'xatakamovil': 'Xataka Móvil', 'applesfera': 'Applesfera', 'hipertextual': 'Hipertextual', 'computerhoy': 'Computer Hoy', 'andro4all': 'Andro4all' }],
  ['🔬 Ciencia', { 'muyinteresante': 'Muy Interesante', 'xatakaciencia': 'Xataka Ciencia' }],
  ['❤️ Salud', { 'vitonica': 'Vitónica', 'saludelmundo': 'Salud (El Mundo)' }],
  ['🚗 Motor', { 'motorpasion': 'Motorpasión' }],
  ['🍳 Cocina', { 'directoalpaladar': 'Directo al Paladar' }],
  ['🎬 Cine y televisión', { 'espinof': 'Espinof', 'fotogramas': 'Fotogramas' }],
];
const PAPERS = Object.assign({}, ...PAPER_GROUPS.map(g => g[1]));
const LOW_BATTERY_LEVELS = [10, 15, 20, 25, 30, 40, 50];
const PILL_COUNTS = [0.5, 1, 1.5, 2, 2.5, 3, 4, 5];
const pillCount = c => (Math.floor(c) || '') + (c % 1 ? '½' : '');
const pillTime = m => `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`;

// --- Iconos (trazo, como los del panel) -------------------------------------------------
const ICON = {
  back: '<path d="M15 5l-7 7 7 7"/>',
  chev: '<path d="M9 5l7 7-7 7"/>',
  up: '<path d="M6 15l6-6 6 6"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  people: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7"/><path d="M18 14a6.5 6.5 0 0 1 3.5 6"/>',
  sos: '<path d="M12 3l9 16H3z"/><path d="M12 10v4"/><path d="M12 17h.01"/>',
  grid: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  pin: '<path d="M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  house: '<path d="M4 11l8-7 8 7"/><path d="M6 10v10h12V10"/><path d="M10 20v-5h4v5"/>',
  puzzle: '<path d="M10 4a2 2 0 0 1 4 0v2h4v4h-2a2 2 0 0 0 0 4h2v4h-4v-2a2 2 0 0 0-4 0v2H6v-4h2a2 2 0 0 0 0-4H6V6h4z"/>',
  radio: '<rect x="3" y="8" width="18" height="12" rx="2"/><path d="M7 8l10-4"/><circle cx="15" cy="14" r="2.5"/><path d="M6 12h4M6 16h4"/>',
  wifi: '<path d="M2 9a15 15 0 0 1 20 0"/><path d="M5.5 12.5a10 10 0 0 1 13 0"/><path d="M9 16a5 5 0 0 1 6 0"/><path d="M12 19.5h.01"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 21h4"/>',
  bellOff: '<path d="M8.5 6.1A6 6 0 0 1 18 11v4"/><path d="M6 11v5l-2 2h12"/><path d="M10 21h4"/><path d="M3 3l18 18"/>',
  person: '<circle cx="12" cy="9" r="4" fill="currentColor" stroke="none"/><path d="M4 21a8 8 0 0 1 16 0z" fill="currentColor" stroke="none"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  battery: '<rect x="7" y="4" width="10" height="17" rx="2"/><path d="M10 2h4"/><path d="M12 9v4"/><path d="M12 16.5h.01"/>',
  pill: '<rect x="3" y="8.5" width="18" height="7" rx="3.5" transform="rotate(-35 12 12)"/><path d="M9.5 8.5l5 7"/>',
  news: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 8h10M7 12h10M7 16h6"/>'
};
const svg = (name, extra = '') =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" ${extra}>${ICON[name]}</svg>`;

// --- Utilidades -------------------------------------------------------------------------
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const $ = sel => document.querySelector(sel);
const app = $('#app');

let toastTimer;
function toast(text, ms = 3500) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toastTimer);
  if (ms) toastTimer = setTimeout(() => t.classList.remove('show'), ms);
}

function ago(date) {
  if (!date) return 'nunca';
  const s = Math.round((Date.now() - date.getTime()) / 1000);
  if (s < 90) return 'ahora mismo';
  const m = Math.round(s / 60);
  if (m < 60) return `hace ${m} min`;
  const h = Math.round(m / 60);
  if (h < 24) return `hace ${h} h`;
  return date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' }) + ' ' +
    date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
}

function when(date) {
  if (!date) return '';
  const today = new Date().toDateString() === date.toDateString();
  const time = date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
  return today ? `Hoy ${time}` : date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' }) + ' ' + time;
}

/** Foto elegida → JPEG (base64) de [side] px de lado mayor, sin recortar (cajas de pastillas). */
function resizeImage(file, side) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, side / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(img.src);
      resolve(canvas.toDataURL('image/jpeg', 0.85));
    };
    img.onerror = reject;
    img.src = URL.createObjectURL(file);
  });
}

/** Foto elegida → cuadrado de 720 px en JPEG (base64), como guarda el panel. */
function resizePhoto(file, side = 720) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const s = Math.min(img.width, img.height);
      const out = Math.min(side, s);
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = out;
      canvas.getContext('2d').drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, out, out);
      URL.revokeObjectURL(img.src);
      resolve(canvas.toDataURL('image/jpeg', 0.85));
    };
    img.onerror = reject;
    img.src = URL.createObjectURL(file);
  });
}

// --- Proyecto de Firebase de la familia ---------------------------------------------------

/**
 * Lee los datos del proyecto del texto que da la consola de Firebase (el bloque
 * `const firebaseConfig = { apiKey: "…", … }`) o de un JSON. Null si falta algo.
 */
function parseConfig(text) {
  // También vale pegar el enlace de "Invitar a otro cuidador" (app del cuidador, web local).
  const link = String(text).match(/proyecto=([A-Za-z0-9_-]+)/);
  if (link) {
    try { text = unb64(link[1]); } catch (e) { return null; }
  }
  const c = {};
  for (const m of String(text).matchAll(/["']?(\w+)["']?\s*:\s*["']([^"']+)["']/g)) c[m[1]] = m[2].trim();
  if (!c.apiKey || !c.projectId || !c.appId) return null;
  return {
    apiKey: c.apiKey, projectId: c.projectId, appId: c.appId,
    authDomain: c.authDomain || `${c.projectId}.firebaseapp.com`,
    messagingSenderId: c.messagingSenderId || '', storageBucket: c.storageBucket || ''
  };
}

const b64 = s => btoa(unescape(encodeURIComponent(s))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64 = s => decodeURIComponent(escape(atob(s.replace(/-/g, '+').replace(/_/g, '/'))));

/** Enlace para otro cuidador: lleva los datos del proyecto después de #, que no llega a ningún servidor. */
const inviteLink = config => `${location.origin}${location.pathname}#proyecto=${b64(JSON.stringify(config))}`;

function loadConfig() {
  const hash = new URLSearchParams(location.hash.slice(1)).get('proyecto');
  if (hash) {
    history.replaceState(null, '', location.pathname);
    try {
      const fromLink = parseConfig(unb64(hash));
      if (fromLink) {
        const old = parseConfig(localStorage.getItem('project') || '');
        if (!old || old.projectId === fromLink.projectId ||
            confirm(`Ya usas el proyecto «${old.projectId}». ¿Cambiar al proyecto «${fromLink.projectId}»? Dejarás de ver los móviles del otro.`)) {
          if (old && old.projectId !== fromLink.projectId) localStorage.removeItem('device');
          localStorage.setItem('project', JSON.stringify(fromLink));
        }
      }
    } catch (e) { /* Enlace roto: se ignora. */ }
  }
  return parseConfig(localStorage.getItem('project') || '');
}

const config = loadConfig();
const fb = config && initializeApp(config);
const auth = fb && getAuth(fb);
const db = fb && getFirestore(fb);

const S = {
  uid: null,
  devices: [],          // [{id, data}]
  deviceId: localStorage.getItem('device') || null,
  device: null,         // datos del móvil abierto
  photos: {},           // contactId → dataURL
  events: [],
  view: { name: 'home' },
  lastLocate: null,
  ringWanted: null,     // true/false: sonar o parar pedido y aún sin confirmar
  unsub: []
};

if (auth) {
  onAuthStateChanged(auth, user => {
    if (!user) {
      signInAnonymously(auth).catch(e => {
        app.innerHTML = `${top('LlamaditApp · Cuidador')}<div class="card">
          <p class="warn">No se puede conectar con el proyecto «${esc(config.projectId)}»: ${esc(e.code || e.message)}</p>
          <p class="hint">Comprueba que en Firebase está activado el acceso anónimo y que los datos son los de la app web del proyecto (mira la <a href="${GUIDE_URL}" target="_blank" rel="noopener">guía</a>).</p>
          <button class="btn" id="change">Cambiar los datos del proyecto</button></div>`;
        $('#change').onclick = forgetProject;
      });
      return;
    }
    S.uid = user.uid;
    listenDevices();
    render();
  });
} else {
  viewSetup();
}

/** Olvida el proyecto en este navegador (los móviles no cambian). */
function forgetProject() {
  if (S.uid && !confirm('¿Cambiar los datos del proyecto? Dejarás de ver los móviles hasta volver a ponerlos.')) return;
  localStorage.removeItem('project');
  localStorage.removeItem('device');
  location.reload();
}

/** Primera vez: pegar los datos del proyecto de Firebase de la familia. */
function viewSetup() {
  app.innerHTML = `${top('LlamaditApp · Cuidador')}
    <div class="card">
      <p class="hint">Para gestionar a distancia el móvil de la persona, tu familia necesita su propio
      <b>proyecto de Firebase</b> (de Google, gratuito). Los datos se guardan ahí, en vuestra cuenta: ni
      LlamaditApp ni nadie más puede verlos.</p>
      <p class="hint">Si no lo tenéis, créalo siguiendo la <a href="${GUIDE_URL}" target="_blank" rel="noopener">guía paso a paso</a> (unos 15 minutos).
      Si otro cuidador ya lo tiene, pídele su enlace de invitación y pégalo aquí.</p>
    </div>
    <div class="card">
      <label class="field">Datos del proyecto (la configuración de la app web que da Firebase) o enlace de invitación
        <textarea id="cfg" rows="8" spellcheck="false" placeholder="const firebaseConfig = {&#10;  apiKey: &quot;…&quot;,&#10;  projectId: &quot;…&quot;,&#10;  appId: &quot;…&quot;&#10;};"></textarea></label>
      <button class="btn primary" id="save">Guardar</button>
      <p class="warn" id="err" hidden></p>
      <p class="hint">Se guardan solo en este navegador.</p>
    </div>`;
  $('#save').onclick = () => {
    const parsed = parseConfig($('#cfg').value);
    if (!parsed) {
      $('#err').textContent = 'No se encuentran apiKey, projectId y appId. Copia el bloque entero de la consola de Firebase, o el enlace de invitación completo.';
      $('#err').hidden = false;
      return;
    }
    localStorage.setItem('project', JSON.stringify(parsed));
    location.reload();
  };
}

function listenDevices() {
  S.devicesUnsub?.();
  S.devicesUnsub = onSnapshot(query(collection(db, 'devices'), where('caregivers', 'array-contains', S.uid)), snap => {
    const before = new Set(S.devices.map(d => d.id));
    S.devices = snap.docs.map(d => ({ id: d.id, data: d.data() }));
    // Un móvil nuevo mientras se enseña el QR: acaba de aceptar la invitación.
    const added = S.view.name === 'link' && S.loaded && S.devices.find(d => !before.has(d.id));
    S.loaded = true;
    if (added) {
      toast('Móvil vinculado ✓');
      openDevice(added.id);
      render(true);
      return;
    }
    if (S.deviceId && !S.devices.some(d => d.id === S.deviceId)) closeDevice();
    // Al volver a abrir la web: el último móvil que se miró (o el único que hay).
    if (S.deviceId && !S.unsub.length && S.view.name !== 'link') openDevice(S.deviceId);
    if (!S.deviceId && S.devices.length === 1 && S.view.name === 'home') openDevice(S.devices[0].id);
    render();
  }, e => console.warn('devices', e));
}

function openDevice(id) {
  closeDevice();
  S.deviceId = id;
  localStorage.setItem('device', id);
  S.view = { name: 'device' };
  const ref = doc(db, 'devices', id);
  S.unsub.push(onSnapshot(ref, snap => {
    S.device = snap.data() || null;
    if (S.ringWanted !== null && S.device?.state?.ringing === S.ringWanted) S.ringWanted = null;
    render();
  },
    () => { closeDevice(); render(); }));
  S.unsub.push(onSnapshot(collection(ref, 'photos'), snap => {
    snap.docChanges().forEach(c => {
      if (c.type === 'removed') delete S.photos[c.doc.id];
      else S.photos[c.doc.id] = 'data:image/jpeg;base64,' + c.doc.data().jpeg;
    });
    render();
  }));
  S.unsub.push(onSnapshot(query(collection(ref, 'events'), orderBy('at', 'desc'), limit(15)), snap => {
    S.events = snap.docs.map(d => d.data());
    render();
  }));
}

function closeDevice() {
  S.unsub.forEach(u => u());
  S.unsub = [];
  S.deviceId = null;
  S.device = null;
  S.photos = {};
  S.events = [];
  localStorage.removeItem('device');
}

/**
 * Deja una orden al móvil y espera a que la haga. Devuelve el resultado, o lanza un
 * error si el móvil dice que ha fallado. Si tarda, avisa de que se hará en cuanto tenga
 * conexión (la orden se queda guardada).
 */
async function send(type, args = {}, { quiet = false, timeout = 25000 } = {}) {
  const ref = await addDoc(collection(db, 'devices', S.deviceId, 'commands'), {
    type, args, by: S.uid, status: 'pending', at: serverTimestamp()
  });
  if (!quiet) toast('Enviado al móvil…', 0);
  return new Promise((resolve, reject) => {
    const slow = setTimeout(() => {
      toast('El móvil no responde ahora. Se hará en cuanto tenga conexión.', 6000);
    }, timeout);
    const stop = onSnapshot(ref, snap => {
      const c = snap.data();
      if (!c || c.status === 'pending') return;
      clearTimeout(slow);
      stop();
      if (c.status === 'done') {
        if (!quiet) toast('Hecho ✓');
        resolve(c.result || {});
      } else {
        toast('No se ha podido: ' + (c.error || 'error'), 6000);
        reject(new Error(c.error));
      }
    });
  });
}

// --- Navegación ---------------------------------------------------------------------------
function go(view) {
  S.view = view;
  render(true);
  scrollTo(0, 0);
}

window.addEventListener('popstate', () => {});

/**
 * Dibuja la pantalla. Las de formulario solo se dibujan al entrar ([force]): si se
 * redibujaran con cada cambio del móvil, se perdería lo que se está escribiendo.
 */
function render(force = false) {
  if (!S.uid) return;
  const v = S.view;
  if (!force && v.form) return;
  const views = {
    home: viewHome, link: viewLink, share: viewShare, device: viewDevice, people: viewPeople, person: viewPerson,
    personForm: viewPersonForm, sos: viewSos, screen: viewScreen, locate: viewLocate, house: viewHouse,
    exercises: viewExercises, radio: viewRadio, connection: viewConnection, settings: viewSettings, where: viewWhere,
    battery: viewBattery, pills: viewPills, pillForm: viewPillForm, pillHistory: viewPillHistory, news: viewNews
  };
  if (S.deviceId && !S.device && v.name !== 'link') {
    app.innerHTML = '<p class="loading">Conectando con el móvil…</p>';
    return;
  }
  (views[v.name] || viewHome)();
}

function top(title, back) {
  return `<div class="top">${back ? `<button class="nav" data-go="${back}" aria-label="Atrás">${svg('back')}</button>` : ''}<h1>${esc(title)}</h1></div>`;
}

function bind() {
  app.querySelectorAll('[data-go]').forEach(b => b.onclick = () => go({ name: b.dataset.go }));
}

function switchRow(id, label, checked) {
  return `<label class="switch-row"><span>${esc(label)}</span><span class="switch"><input type="checkbox" id="${id}" ${checked ? 'checked' : ''}><i></i></span></label>`;
}

const st = () => S.device?.state || {};

// --- Pantallas ----------------------------------------------------------------------------

function viewHome() {
  const list = S.devices.map(d => `
    <button class="section-row" data-dev="${esc(d.id)}">
      <span class="icon" style="background:var(--navy)">${svg('people')}</span>
      <span class="text"><span class="title">${esc(d.data.state?.name || 'Sin nombre')}</span>
      <span class="sub">Visto ${ago(d.data.state?.lastSeen?.toDate())}</span></span>${svg('chev', 'class="chev"')}
    </button>`).join('');
  app.innerHTML = `${top('LlamaditApp · Cuidador')}
    ${S.devices.length ? `<div class="label">Móviles que cuidas</div>${list}` : `<div class="card"><p class="hint">Todavía no has vinculado ningún móvil.</p></div>`}
    <button class="btn primary" id="link">Vincular un móvil</button>
    <div class="label">Proyecto de la familia</div>
    <div class="card">
      <p class="hint">Proyecto de Firebase: <b>${esc(config.projectId)}</b></p>
      <button class="btn" id="share">Invitar a otro cuidador</button>
      <button class="btn" id="change">Cambiar los datos del proyecto</button>
    </div>`;
  app.querySelectorAll('[data-dev]').forEach(b => b.onclick = () => openDevice(b.dataset.dev));
  $('#link').onclick = () => go({ name: 'link', form: true });
  $('#share').onclick = () => go({ name: 'share', form: true });
  $('#change').onclick = forgetProject;
}

/** Código QR (SVG) de un texto, con la librería qrcode-generator. */
function qrSvg(text) {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  return qr.createSvgTag({ cellSize: 6, margin: 4, scalable: true });
}

/** Token aleatorio para la invitación (24 caracteres). */
function randomToken() {
  const abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  return Array.from(crypto.getRandomValues(new Uint8Array(24)), b => abc[b % abc.length]).join('');
}

/**
 * Vincular un móvil: la web crea una invitación (invites/{token}) y enseña un QR con los
 * datos del proyecto y la invitación. El móvil de la persona le hace una foto, se conecta
 * al proyecto y acepta la invitación; entonces aparece en la lista (listenDevices).
 */
function viewLink() {
  const v = S.view;
  v.form = true;
  clearInterval(S.linkTimer);
  const backTo = S.deviceId ? 'device' : 'home';
  app.innerHTML = `${top('Vincular un móvil', backTo)}
    <div class="card">
      <label class="field">Tu nombre (lo verá en el móvil de la persona)<input id="me" type="text" maxlength="40" value="${esc(localStorage.getItem('me') || '')}"></label>
      <button class="btn primary" id="go">Crear el código QR</button>
      <p class="warn" id="err" hidden></p>
    </div>
    <div id="qr"></div>`;
  app.querySelector('[data-go]').onclick = () => { clearInterval(S.linkTimer); go({ name: backTo }); };
  $('#go').onclick = async () => {
    const name = $('#me').value.trim();
    localStorage.setItem('me', name);
    $('#go').disabled = true;
    $('#err').hidden = true;
    const token = randomToken();
    const expires = Date.now() + INVITE_MINUTES * 60000;
    try {
      await setDoc(doc(db, 'invites', token), { caregiver: S.uid, name, expires: Timestamp.fromMillis(expires) });
    } catch (e) {
      $('#err').textContent = 'No se ha podido crear el código: ' + (e.code || e.message) + '. Comprueba la conexión y las reglas del proyecto (guía).';
      $('#err').hidden = false;
      $('#go').disabled = false;
      return;
    }
    const payload = 'LLAMADITAPP:' + JSON.stringify({ p: config.projectId, k: config.apiKey, a: config.appId, i: token });
    $('#qr').innerHTML = `<div class="card qr-card">
        <div class="qr">${qrSvg(payload)}</div>
        <p class="hint" id="left"></p>
        <p class="hint">En el móvil de la persona: mantén pulsado el reloj 5 segundos, escribe el PIN, entra en
        <b>Gestión a distancia</b> y pulsa <b>Hacer una foto al código QR</b>. Enfoca este código.</p>
        <p class="hint">Si no estás junto a ella, haz una captura de esta pantalla, mándala al móvil de la persona y allí usa <b>Elegir una foto del código QR</b>.</p>
      </div>`;
    $('#go').textContent = 'Crear otro código';
    $('#go').disabled = false;
    let polls = 0;
    const tick = () => {
      // Cada 3 s: si el móvil ya ha usado la invitación, se vuelve a pedir la lista de móviles
      // (la escucha puede haberse dormido, por ejemplo con la app en segundo plano).
      if (++polls % 3 === 0) {
        getDoc(doc(db, 'invites', token)).then(d => { if (!d.exists() && S.view.name === 'link') listenDevices(); }).catch(() => {});
      }
      const s = Math.max(0, Math.round((expires - Date.now()) / 1000));
      const left = $('#left');
      if (!left) return clearInterval(S.linkTimer);
      left.textContent = s ? `Vale ${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} más.` : 'Este código ha caducado. Crea otro.';
      if (!s) {
        clearInterval(S.linkTimer);
        $('#qr .qr')?.classList.add('expired');
      }
    };
    clearInterval(S.linkTimer);
    S.linkTimer = setInterval(tick, 1000);
    tick();
  };
}

/** Invitar a otro cuidador: enlace (y su QR) con los datos del proyecto de la familia. */
function viewShare() {
  S.view.form = true;
  // Con la web publicada, un enlace que basta con abrir (y su QR). En la app del cuidador y en
  // la web local la dirección no le sirve a nadie más: se comparte un texto para pegar.
  const published = !window.LlamaditApp && !/^(localhost|127\.|\[::1\])/.test(location.hostname);
  const link = inviteLink(config);
  const code = link.slice(link.indexOf('proyecto='));
  const message = published ? link
    : 'Invitación a la gestión a distancia de LlamaditApp. Pega este texto en «Datos del proyecto» de la app o la web del cuidador:\n' + code;
  app.innerHTML = `${top('Invitar a otro cuidador', S.view.back || 'home')}
    <div class="card">
      <p class="hint">${published
        ? 'Manda este enlace a la otra persona que cuida (por WhatsApp, por correo…) o que escanee el código con la cámara de su móvil. Si usa la app del cuidador o la web en su ordenador, lo pega en <b>Datos del proyecto</b>.'
        : 'Manda la invitación a la otra persona que cuida (por WhatsApp, por correo…). La primera vez que abra la app o la web del cuidador, la pega en <b>Datos del proyecto</b>.'}
      Después tiene que vincular el móvil de la persona con su propio código QR.</p>
      ${published ? `<div class="qr">${qrSvg(link)}</div>` : ''}
      <button class="btn primary" id="sharebtn">Compartir la invitación</button>
      <button class="btn" id="copy">Copiar la invitación</button>
      <p class="warn">Quien tenga la invitación puede conectarse al proyecto, pero no ve ningún móvil hasta que lo vincule con el móvil de la persona delante (o con la foto de su código).</p>
    </div>`;
  bind();
  $('#sharebtn').hidden = !navigator.share && !window.LlamaditApp;
  $('#sharebtn').onclick = () => window.LlamaditApp
    ? window.LlamaditApp.share(message)
    : navigator.share(published ? { title: 'LlamaditApp · Cuidador', url: link } : { text: message }).catch(() => {});
  $('#copy').onclick = () => navigator.clipboard.writeText(message).then(() => toast('Invitación copiada ✓'), () => prompt('Copia la invitación:', message));
}

function viewDevice() {
  const s = st();
  const b = s.battery;
  const seen = s.lastSeen?.toDate?.();
  const online = seen && Date.now() - seen.getTime() < 45 * 60000;
  // Lo que se acaba de pedir manda hasta que el móvil lo confirma.
  const ringing = S.ringWanted ?? !!s.ringing;
  const paused = s.kiosk === 'paused' || s.kiosk === 'session';
  const kiosk = { locked: 'Modo quiosco activo', paused: 'Modo quiosco en pausa', session: 'El cuidador está configurando', off: 'Sin modo quiosco' }[s.kiosk] || '';
  const people = s.people || [];
  const sosPerson = people.find(p => p.sos);
  const nophoto = people.filter(p => !p.photo).length;
  const visible = item => (s.homeLayout || '').split(',').some(e => e.startsWith(item + ':') && e.endsWith(':1'));
  const rows = [
    ['people', 'people', '#2E7D32', 'Personas', people.length ? `${people.length} personas` + (nophoto ? ` · ${nophoto} sin foto` : '') : 'Ninguna', !people.length || nophoto],
    ['sos', 'sos', '#C62828', 'Emergencia (SOS)', sosPerson ? `Llama a ${sosPerson.name}` : 'Sin contacto', !sosPerson],
    ['screen', 'grid', '#1565C0', 'Pantalla principal', `${(s.homeLayout || '').split(',').filter(e => e.endsWith(':1') && !/^(CLOCK|BATTERY)/.test(e)).length} botones`, false],
    ['locate', 'pin', '#00796B', 'Ubicación por SMS', `${people.filter(p => p.locate).length} personas pueden pedirla`, false],
    ['house', 'house', '#795548', 'Volver a casa', !visible('GO_HOME') ? 'Desactivado' : s.goHome?.hasHouse ? (s.goHome.address || 'Casa marcada') : 'Falta marcar la casa', visible('GO_HOME') && !s.goHome?.hasHouse],
    ['exercises', 'puzzle', '#AD1457', 'Ejercicios', !visible('EXERCISES') ? 'Desactivados' : `${(s.exercises || []).filter(e => e.enabled).length} de 8 activos`, false],
    ['radio', 'radio', '#3949AB', 'Radio', !visible('RADIO') ? 'Desactivada' : `${s.radio?.stations?.length || 0} emisoras`, false],
    ['battery', 'battery', '#EF6C00', 'Batería baja', s.lowBattery?.on ? `Aviso al ${s.lowBattery.percent} %` : 'Desactivado', false],
    (() => {
      const w = pillWeek(s.pills);
      const missed = s.pills?.enabled ? w.total - w.taken : 0;
      return ['pills', 'pill', '#00897B', 'Pastillas', missed > 0 ? `${missed} sin tomar en 7 días`
        : s.pills?.enabled && s.pills.doses?.length ? (n => n === 1 ? '1 toma al día' : `${n} tomas al día`)(new Set(s.pills.doses.map(d => d.minutes)).size) : 'Desactivadas', missed > 0];
    })(),
    ['news', 'news', '#4E5B65', 'Periódicos', visible('NEWS') ? `${(s.news || []).length} periódicos` : 'Desactivados', false],
    ['connection', 'wifi', '#455A64', 'Conexión', s.wifi ? 'Wi-Fi y datos móviles' : 'Solo datos móviles', false],
    ['settings', 'link', '#6A1B9A', 'Vinculación', `${(S.device.caregivers || []).length} cuidadores`, false]
  ];
  const events = S.events.map(e => {
    const f = EVENTS[e.type];
    return f ? `<div class="event ${e.type === 'sos' ? 'sos' : ''}"><span class="when">${esc(when(e.at?.toDate?.()))}</span><span class="what">${esc(f(e))}</span></div>` : '';
  }).join('');
  app.innerHTML = `${top(s.name || 'Sin nombre', S.devices.length > 1 ? 'home' : '')}
    <div class="status">
      <div class="row">
        <span><span class="dot" style="background:${online ? '#69F0AE' : '#FFD54F'}"></span>${online ? 'Conectado' : 'Sin conexión'} · ${ago(seen)}</span>
        ${b ? `<span>🔋 ${b.percent} %${b.charging ? ' (cargando)' : ''}</span>` : ''}
      </div>
      <div class="sub">${esc(kiosk)}${s.appVersion ? ` · LlamaditApp ${esc(s.appVersion)}` : ''}</div>
    </div>
    ${paused ? `<div class="card">
      <p class="warn">Con el modo quiosco en pausa, el móvil no tiene el botón SOS a la vista.</p>
      <button class="btn green" id="kioskon">Activar el modo quiosco</button>
    </div>` : ''}
    <div class="big-actions">
      <button class="big locate" id="where">${svg('pin')}¿Dónde está?</button>
      ${ringing
        ? `<button class="big ring stop" id="ring">${svg('bellOff')}Dejar de sonar</button>`
        : `<button class="big ring" id="ring">${svg('bell')}Hacer sonar</button>`}
    </div>
    ${events ? `<div class="label">Actividad</div><div class="card">${events}</div>` : ''}
    <div class="label">Apartados</div>
    ${rows.map(([view, icon, color, title, sub, warn]) => `
      <button class="section-row" data-go="${view}">
        <span class="icon" style="background:${color}">${svg(icon)}</span>
        <span class="text"><span class="title">${esc(title)}</span><span class="sub ${warn ? 'warn' : ''}">${esc(sub)}</span></span>${svg('chev', 'class="chev"')}
      </button>`).join('')}`;
  bind();
  if (S.devices.length > 1) app.querySelector('.top [data-go]').onclick = () => { closeDevice(); go({ name: 'home' }); };
  $('#where').onclick = () => go({ name: 'where' });
  if (paused) $('#kioskon').onclick = () => {
    if (!confirm('¿Activar el modo quiosco ahora? Si alguien está usando el móvil, se le cerrará lo que tenga abierto.')) return;
    send('resumeKiosk').then(() => toast('Modo quiosco activado ✓')).catch(() => {});
  };
  $('#ring').onclick = () => {
    // Se cambia el botón al momento; el móvil confirma con su estado (ringing).
    const want = !ringing;
    S.ringWanted = want;
    clearTimeout(S.ringTimer);
    S.ringTimer = setTimeout(() => { S.ringWanted = null; render(); }, 15000);
    render();
    send(want ? 'ring' : 'stopRing').then(() => toast(want ? 'Sonando 30 segundos' : 'Ya no suena'))
      .catch(() => { S.ringWanted = null; render(); });
  };
}

function viewWhere() {
  const r = S.lastLocate;
  const map = r && r.found
    ? (() => {
      const d = 0.004;
      const bbox = [r.lng - d, r.lat - d * 0.7, r.lng + d, r.lat + d * 0.7].join(',');
      return `<iframe class="map" src="https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${r.lat},${r.lng}" title="Mapa"></iframe>
        <p class="hint">Precisión: unos ${Math.round(r.accuracy || 0)} m · ${esc(when(new Date(r.time)))}${r.battery != null ? ` · Batería ${r.battery} %${r.charging ? ' (cargando)' : ''}` : ''}</p>
        <a class="btn primary" style="display:grid;place-items:center;text-decoration:none" href="https://www.google.com/maps/search/?api=1&query=${r.lat},${r.lng}" target="_blank" rel="noopener">Abrir en Google Maps</a>`;
    })()
    : r ? '<p class="warn">El móvil no ha podido saber dónde está ahora mismo (sin GPS ni red). Prueba otra vez en un rato.</p>' : '';
  app.innerHTML = `${top('¿Dónde está?', 'device')}
    <div class="card">
      ${map || '<p class="hint">Se pide la ubicación al móvil en este momento (tarda unos segundos). Nada de seguimiento: solo cuando tú la pides.</p>'}
      <button class="btn green" id="ask">${r ? 'Volver a pedirla' : 'Pedir la ubicación'}</button>
    </div>`;
  bind();
  $('#ask').onclick = async () => {
    $('#ask').disabled = true;
    $('#ask').textContent = 'Buscando… (hasta 30 s)';
    try {
      S.lastLocate = await send('locate', {}, { quiet: true, timeout: 40000 });
      $('#toast').classList.remove('show');
    } catch (e) { /* ya avisado */ }
    if (S.view.name === 'where') render(true);
  };
  if (!r) $('#ask').click();
}

function personPhoto(p, cls = 'ph') {
  const src = S.photos[String(p.id)];
  return src ? `<img class="${cls}" src="${src}" alt="">` : `<span class="${cls}">${svg('person')}</span>`;
}

function tags(p) {
  return `<div class="tags">${p.sos ? '<span class="tag sos">SOS</span>' : ''}${p.whatsapp ? '<span class="tag video">Vídeo</span>' : ''}${p.locate ? '<span class="tag loc">Ubicación</span>' : ''}${!p.photo ? '<span class="tag nophoto">Sin foto</span>' : ''}</div>`;
}

function viewPeople() {
  const people = st().people || [];
  app.innerHTML = `${top('Personas', 'device')}
    <p class="hint" style="margin:0 4px 10px">Las personas que salen en Llamar y en Videollamada, en este orden. Toca una para cambiarla.</p>
    ${people.map((p, i) => `
      <div class="card"><div class="person">
        <div class="move">
          <button class="mini" data-move="${i}" data-dir="-1" ${i === 0 ? 'disabled' : ''} aria-label="Subir">${svg('up')}</button>
          <button class="mini" data-move="${i}" data-dir="1" ${i === people.length - 1 ? 'disabled' : ''} aria-label="Bajar">${svg('down')}</button>
        </div>
        ${personPhoto(p)}
        <button class="info" data-person="${p.id}" style="border:0;background:none;text-align:left;cursor:pointer;color:inherit;padding:0">
          <div class="nm">${esc(p.name)}</div><div class="tel">${esc(p.phone)}</div>${tags(p)}
        </button>${svg('chev', 'class="chev"')}
      </div></div>`).join('') || '<div class="card"><p class="hint">Todavía no hay nadie.</p></div>'}
    <button class="btn primary" id="add">Añadir persona</button>`;
  bind();
  app.querySelectorAll('[data-person]').forEach(b => b.onclick = () => go({ name: 'person', id: Number(b.dataset.person) }));
  app.querySelectorAll('[data-move]').forEach(b => b.onclick = () => {
    const i = Number(b.dataset.move), j = i + Number(b.dataset.dir);
    const ids = people.map(p => p.id);
    [ids[i], ids[j]] = [ids[j], ids[i]];
    send('reorder', { ids }).catch(() => {});
  });
  $('#add').onclick = () => go({ name: 'personForm', form: true });
}

function viewPerson() {
  const p = (st().people || []).find(x => x.id === S.view.id);
  // Recién creada, el móvil aún no ha publicado la lista nueva: se dibuja al llegar.
  if (!p) {
    app.innerHTML = `${top('', 'people')}<p class="loading">Cargando…</p>`;
    bind();
    return;
  }
  app.innerHTML = `${top(p.name, 'people')}
    <div class="card">
      <div class="person-big">${personPhoto(p)}<div><div style="font-size:22px;font-weight:900">${esc(p.name)}</div><div class="tel">${esc(p.phone)}</div>${tags(p)}</div></div>
      <button class="btn" id="edit">Cambiar foto, nombre o teléfono</button>
    </div>
    <div class="label">Qué puede hacer</div>
    <div class="card">
      ${switchRow('sos', 'Contacto de emergencia (SOS)', p.sos)}
      <p class="hint">El botón SOS le llama y le envía avisos por SMS. Solo puede haber uno.</p>
      ${switchRow('wa', 'Tiene WhatsApp (videollamada)', p.whatsapp)}
      ${switchRow('loc', 'Puede pedir la ubicación por SMS', p.locate)}
    </div>
    <button class="btn danger" id="remove">Quitar de la lista</button>`;
  bind();
  $('#edit').onclick = () => go({ name: 'personForm', id: p.id, form: true });
  $('#sos').onchange = e => { if (e.target.checked) send('setSos', { id: p.id }).catch(() => {}); else e.target.checked = true; };
  $('#wa').onchange = e => send('setWhatsApp', { id: p.id, on: e.target.checked }).catch(() => {});
  $('#loc').onchange = e => send('setLocate', { id: p.id, on: e.target.checked }).catch(() => {});
  $('#remove').onclick = () => {
    if (confirm(`¿Quitar a ${p.name} de la lista? Seguirá en la agenda del móvil.`)) {
      send('removePerson', { id: p.id }).then(() => go({ name: 'people' })).catch(() => {});
    }
  };
}

function viewPersonForm() {
  const p = S.view.id != null ? (st().people || []).find(x => x.id === S.view.id) : null;
  const accounts = st().googleAccounts || [];
  let photo = null;
  let removePhoto = false;
  app.innerHTML = `${top(p ? 'Cambiar datos' : 'Nueva persona', p ? '' : 'people')}
    <div class="card">
      <div class="photo-pick">
        <span id="ph">${p ? personPhoto(p) : `<span class="ph">${svg('person')}</span>`}</span>
        <div class="btns">
          <span class="btn file-btn" style="display:grid;place-items:center">Elegir una foto<input type="file" accept="image/*" id="file"></span>
          ${p && p.photo ? '<button class="btn" id="nophoto">Quitar la foto</button>' : ''}
        </div>
      </div>
      <p class="hint">Una foto de la cara, de cerca y con luz: es lo que verá para reconocer a la persona.</p>
    </div>
    <div class="card">
      <label class="field">Nombre<input id="name" type="text" maxlength="60" value="${esc(p?.name || '')}"></label>
      <label class="field">Teléfono<input id="phone" type="tel" value="${esc(p?.phone || '')}"></label>
      <p class="hint">Con el prefijo si es de otro país (+351…).</p>
    </div>
    ${p ? '' : `<div class="card">${switchRow('wa', 'Tiene WhatsApp (videollamada)', false)}</div>
      ${accounts.length ? `<div class="label">Dónde se guarda</div><div class="card">
        ${[...accounts, ''].map((a, i) => `<label class="radio-row"><input type="radio" name="acc" value="${esc(a)}" ${i === 0 ? 'checked' : ''}><span>${esc(a || 'Solo en ese móvil')}</span></label>`).join('')}
      </div>` : ''}`}
    <p class="warn" id="err" hidden></p>
    <button class="btn green" id="save">Guardar</button>`;
  bind();
  if (p) app.querySelector('.top .nav')?.remove();
  const back = () => go(p ? { name: 'person', id: p.id } : { name: 'people' });
  if (p) $('.top').insertAdjacentHTML('afterbegin', `<button class="nav" id="bk" aria-label="Atrás">${svg('back')}</button>`), $('#bk').onclick = back;
  $('#file').onchange = async e => {
    const f = e.target.files[0];
    if (!f) return;
    photo = await resizePhoto(f);
    removePhoto = false;
    $('#ph').innerHTML = `<img class="ph" src="${photo}" alt="">`;
  };
  $('#nophoto') && ($('#nophoto').onclick = () => {
    removePhoto = true;
    photo = null;
    $('#ph').innerHTML = `<span class="ph">${svg('person')}</span>`;
  });
  $('#save').onclick = async () => {
    const name = $('#name').value.trim();
    const phone = $('#phone').value.trim();
    const err = $('#err');
    err.hidden = true;
    if (!name) { err.textContent = 'Escribe el nombre.'; err.hidden = false; return; }
    if ((phone.match(/\d/g) || []).length < 3) { err.textContent = 'Escribe un teléfono válido.'; err.hidden = false; return; }
    $('#save').disabled = true;
    try {
      if (p) {
        const args = { id: p.id, name, phone };
        if (photo) args.photo = photo;
        if (removePhoto) args.removePhoto = true;
        await send('updatePerson', args, { timeout: 30000 });
        back();
      } else {
        const args = { name, phone, whatsapp: $('#wa').checked };
        if (photo) args.photo = photo;
        const acc = app.querySelector('input[name=acc]:checked');
        if (acc) args.account = acc.value || null;
        const r = await send('addPerson', args, { timeout: 30000 });
        go({ name: 'person', id: r.id });
      }
    } catch (e) {
      $('#save').disabled = false;
    }
  };
}

function viewSos() {
  const s = st();
  const people = s.people || [];
  app.innerHTML = `${top('Emergencia (SOS)', 'device')}
    <div class="label">A quién llama</div>
    <div class="card">
      ${people.map(p => `<label class="radio-row"><input type="radio" name="sos" value="${p.id}" ${p.sos ? 'checked' : ''}><span>${esc(p.name)} · <span class="tel">${esc(p.phone)}</span></span></label>`).join('') || '<p class="hint">Primero añade personas.</p>'}
      <p class="hint">Mantener pulsado el SOS 2 segundos le llama y le envía la ubicación por SMS (y 3 avisos más, cada 10 minutos).</p>
    </div>
    <div class="label">Dibujo del botón</div>
    <div class="card"><div class="choice-grid">
      ${Object.entries(SOS_ICONS).map(([k, t]) => `<button class="choice ${s.sosIcon === k ? 'on' : ''}" data-icon="${k}">${esc(t)}</button>`).join('')}
    </div></div>`;
  bind();
  app.querySelectorAll('input[name=sos]').forEach(r => r.onchange = () => send('setSos', { id: Number(r.value) }).catch(() => {}));
  app.querySelectorAll('[data-icon]').forEach(b => b.onclick = () => send('setSosIcon', { value: b.dataset.icon }).catch(() => {}));
}

function parseLayout(text) {
  return (text || '').split(',').filter(Boolean).map(e => {
    const [item, size, vis] = e.split(':');
    return { item, size: size || 'MEDIUM', visible: vis !== '0' };
  });
}
const encodeLayout = l => l.map(e => `${e.item}:${e.size}:${e.visible ? 1 : 0}`).join(',');

function viewScreen() {
  const s = st();
  const layout = parseLayout(s.homeLayout);
  app.innerHTML = `${top('Pantalla principal', 'device')}
    <p class="hint" style="margin:0 4px 10px">Qué botones se ven, su tamaño y su orden (de arriba abajo). El reloj siempre se ve: es la entrada al panel.</p>
    <div class="card">
      ${layout.map((e, i) => `<div class="home-item">
        <label class="switch"><input type="checkbox" data-vis="${i}" ${e.visible ? 'checked' : ''} ${e.item === 'CLOCK' ? 'disabled' : ''} aria-label="${esc(HOME_ITEMS[e.item] || e.item)}"><i></i></label>
        <span class="nm">${esc(HOME_ITEMS[e.item] || e.item)}</span>
        ${/^(CLOCK|BATTERY)$/.test(e.item) ? '' : `<select data-size="${i}">${Object.entries(SIZES).map(([k, t]) => `<option value="${k}" ${e.size === k ? 'selected' : ''}>${t}</option>`).join('')}</select>`}
        <button class="mini" data-mv="${i}" data-dir="-1" ${i === 0 ? 'disabled' : ''} aria-label="Subir">${svg('up')}</button>
        <button class="mini" data-mv="${i}" data-dir="1" ${i === layout.length - 1 ? 'disabled' : ''} aria-label="Bajar">${svg('down')}</button>
      </div>`).join('')}
    </div>
    <div class="label">Botón para volver</div>
    <div class="card"><div class="choice-grid" style="grid-template-columns:1fr 1fr">
      <button class="choice ${s.backButton === 'HOUSE_TOP' ? 'on' : ''}" data-back="HOUSE_TOP">Casa arriba</button>
      <button class="choice ${s.backButton === 'ARROW_BOTTOM' ? 'on' : ''}" data-back="ARROW_BOTTOM">Flecha abajo</button>
    </div></div>`;
  bind();
  const save = () => send('setHomeLayout', { layout: encodeLayout(layout) }).catch(() => {});
  app.querySelectorAll('[data-vis]').forEach(c => c.onchange = () => { layout[c.dataset.vis].visible = c.checked; save(); });
  app.querySelectorAll('[data-size]').forEach(c => c.onchange = () => { layout[c.dataset.size].size = c.value; save(); });
  app.querySelectorAll('[data-mv]').forEach(b => b.onclick = () => {
    const i = Number(b.dataset.mv), j = i + Number(b.dataset.dir);
    [layout[i], layout[j]] = [layout[j], layout[i]];
    save();
  });
  app.querySelectorAll('[data-back]').forEach(b => b.onclick = () => send('setBackButton', { value: b.dataset.back }).catch(() => {}));
}

function viewLocate() {
  const s = st();
  S.view.form = true;
  app.innerHTML = `${top('Ubicación por SMS', 'device')}
    <div class="card">
      <p class="hint">Estas personas pueden escribir un SMS con una palabra clave al móvil, que responde con un enlace a su posición. Tú no lo necesitas: usa «¿Dónde está?».</p>
      ${(s.people || []).map(p => switchRow('l' + p.id, p.name, p.locate)).join('')}
    </div>
    <div class="label">Palabras clave</div>
    <div class="card">
      <label class="field">Separadas por comas<input id="kw" type="text" value="${esc((s.keywords || []).join(', '))}"></label>
      <button class="btn" id="savekw">Guardar las palabras</button>
      <p class="warn">Cada respuesta es un SMS: según la tarifa, podrían cobrarse.</p>
    </div>`;
  bind();
  (s.people || []).forEach(p => $('#l' + p.id).onchange = e => send('setLocate', { id: p.id, on: e.target.checked }).catch(() => {}));
  $('#savekw').onclick = () => send('setKeywords', { keywords: $('#kw').value.split(',') }).catch(() => {});
}

function viewHouse() {
  const g = st().goHome || {};
  S.view.form = true;
  app.innerHTML = `${top('Volver a casa', 'device')}
    <div class="card">
      <label class="field">Dirección de casa<input id="addr" type="text" value="${esc(g.address || '')}"></label>
      <button class="btn" id="saveaddr">Guardar la dirección</button>
      ${g.hasHouse ? '' : '<p class="warn">Todavía no está marcada la casa.</p>'}
      ${g.portalPhoto ? '' : '<p class="warn">Falta la foto del portal: se pone en el propio móvil (Panel → Volver a casa).</p>'}
    </div>
    <div class="card">
      ${switchRow('alert', 'Avisarte por SMS cuando pulse el botón', g.alert)}
      ${switchRow('upd', 'Ubicación por SMS cada 10 min durante la ruta', g.updates)}
      <p class="warn">Envía SMS al contacto del SOS: según la tarifa, podrían cobrarse.</p>
    </div>
    <p class="hint" style="margin:0 4px">El botón marrón se muestra u oculta en «Pantalla principal».</p>`;
  bind();
  $('#saveaddr').onclick = () => send('setHouse', { address: $('#addr').value }).catch(() => {});
  $('#alert').onchange = e => send('setGoHome', { alert: e.target.checked }).catch(() => {});
  $('#upd').onchange = e => send('setGoHome', { updates: e.target.checked }).catch(() => {});
}

function viewExercises() {
  const list = st().exercises || [];
  app.innerHTML = `${top('Ejercicios', 'device')}
    <p class="hint" style="margin:0 4px 10px">El nivel sube solo tras 5 partidas seguidas bien hechas y baja tras 3 con fallos. Aquí puedes elegirlo.</p>
    ${list.map(e => `<div class="card">
      ${switchRow('e' + e.id, EXERCISES[e.id] || e.id, e.enabled)}
      <div class="levels">${[1, 2, 3, 4, 5].map(n => `<button class="${e.level === n ? 'on' : ''}" data-ex="${e.id}" data-lv="${n}">${n}</button>`).join('')}</div>
      <p class="hint">${e.played} partidas · ${e.wellDone} bien hechas</p>
    </div>`).join('')}`;
  bind();
  list.forEach(e => $('#e' + e.id).onchange = ev => send('setExercise', { id: e.id, enabled: ev.target.checked }).catch(() => {}));
  app.querySelectorAll('[data-lv]').forEach(b => b.onclick = () => send('setExercise', { id: b.dataset.ex, level: Number(b.dataset.lv) }).catch(() => {}));
}

function viewRadio() {
  const r = st().radio || {};
  app.innerHTML = `${top('Radio', 'device')}
    <div class="card">
      ${(r.stations || []).map(x => `<div class="home-item"><span class="nm">${esc(x.name)}</span><button class="mini" data-del="${esc(x.uuid)}" aria-label="Quitar" style="color:var(--red)">✕</button></div>`).join('') || '<p class="hint">No hay emisoras.</p>'}
      <p class="hint">Las emisoras nuevas se añaden en el propio móvil (Panel → Radio), porque hay que buscarlas y escucharlas.</p>
    </div>
    <div class="card">
      <label class="field">Se apaga sola tras<select id="sleep">${Object.entries(SLEEP).map(([k, t]) => `<option value="${k}" ${Number(k) === r.sleepMinutes ? 'selected' : ''}>${t}</option>`).join('')}</select></label>
    </div>`;
  bind();
  app.querySelectorAll('[data-del]').forEach(b => b.onclick = () => send('removeStation', { uuid: b.dataset.del }).catch(() => {}));
  $('#sleep').onchange = e => send('setRadioSleep', { minutes: Number(e.target.value) }).catch(() => {});
}

function viewBattery() {
  const b = st().lowBattery || { on: false, percent: 20 };
  app.innerHTML = `${top('Batería baja', 'device')}
    <div class="card">
      ${switchRow('lbon', 'Recordarle que cargue el móvil', b.on)}
      <p class="hint">Cuando la batería baja a este nivel y el móvil no está cargando, sale en grande el dibujo del móvil y el cargador. Se quita con el botón verde o al enchufar el cargador.</p>
      ${b.on ? `<label class="field">Avisar al<select id="lbpc">${LOW_BATTERY_LEVELS.map(p => `<option value="${p}" ${p === b.percent ? 'selected' : ''}>${p} %</option>`).join('')}</select></label>` : ''}
    </div>`;
  bind();
  $('#lbon').onchange = e => send('setLowBattery', { on: e.target.checked }).catch(() => {});
  $('#lbpc') && ($('#lbpc').onchange = e => send('setLowBattery', { percent: Number(e.target.value) }).catch(() => {}));
}

/** Foto de la caja de una pastilla (o el dibujo de una pastilla si no tiene). */
function pillPhoto(d) {
  const src = S.photos['pill_' + d.id];
  return src ? `<img class="ph" src="${src}" alt="">` : `<span class="ph">${svg('pill')}</span>`;
}

function viewPills() {
  const p = st().pills || { enabled: false, doses: [] };
  const doses = p.doses || [];
  const times = [...new Set(doses.map(d => d.minutes))].sort((a, b) => a - b);
  app.innerHTML = `${top('Pastillas', 'device')}
    <div class="card">
      ${switchRow('pon', 'Avisos de las pastillas', p.enabled)}
      <p class="hint">A cada hora el móvil suena y enseña la foto de cada caja con las pastillas que hay que tomar. La persona confirma cada una con el botón verde. Si no, se lo vuelve a recordar cada 15 minutos y a la hora lo verás en la actividad.</p>
    </div>
    ${pillHistoryCard(p)}
    ${times.map(t => `<div class="label">${pillTime(t)}</div><div class="card">
      ${doses.filter(d => d.minutes === t).map(d => `<button class="pill-row" data-pill="${esc(d.id)}">${pillPhoto(d)}<span class="nm">× ${pillCount(d.count)}</span>${svg('chev', 'class="chev"')}</button>`).join('')}
    </div>`).join('')}
    <button class="btn primary" id="addpill">Añadir una pastilla</button>`;
  bind();
  $('#pon').onchange = e => send('setPills', { on: e.target.checked }).catch(() => {});
  $('#addpill').onclick = () => go({ name: 'pillForm' });
  app.querySelectorAll('[data-pill]').forEach(b => b.onclick = () => go({ name: 'pillForm', id: b.dataset.pill }));
}

/** Historial de tomas: { date, minutes, status: taken|missed|pending, at }. */
function pillHistory(p) {
  const today = localDate(new Date());
  return (p?.history || []).map(r => ({ ...r, status: r.status === 'pending' && r.date < today ? 'missed' : r.status }));
}

function localDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Tomas confirmadas y en total de los últimos 7 días (sin las que están sonando). */
function pillWeek(p) {
  const from = localDate(new Date(Date.now() - 7 * 86400000));
  const done = pillHistory(p).filter(r => r.date > from && r.status !== 'pending');
  return { taken: done.filter(r => r.status === 'taken').length, total: done.length };
}

function pillDay(date) {
  const today = localDate(new Date());
  const yesterday = localDate(new Date(Date.now() - 86400000));
  if (date === today) return 'Hoy';
  if (date === yesterday) return 'Ayer';
  const [y, m, d] = date.split('-').map(Number);
  const s = new Date(y, m - 1, d).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function pillRecord(r) {
  const [cls, mark, text] = r.status === 'taken' ? ['taken', '✓', `Tomada a las ${pillTime(r.at ?? r.minutes)}`]
    : r.status === 'skipped' ? ['skipped', '✗', r.total > 1 && r.skipped < r.total ? `No se ha tomado ${r.skipped} de ${r.total}` : 'Ha dicho que no se la toma']
    : r.status === 'missed' ? ['missed', '✗', 'No la ha confirmado'] : ['wait', '…', 'Avisando ahora'];
  // Qué pastillas eran: la foto de cada caja, cuántas y si la tomó (✓) o no (✗).
  const boxes = (r.boxes || []).map(b => `<span class="pill-box">${pillPhoto(b)}<b class="n">× ${pillCount(b.count)}</b>${
    b.taken === true ? '<b class="ans y">✓</b>' : b.taken === false ? '<b class="ans n2">✗</b>' : ''}</span>`).join('');
  return `<div class="pill-rec ${cls}"><span class="t">${pillTime(r.minutes)}</span><span class="dot">${mark}</span><span class="tx">${text}</span></div>${
    boxes ? `<div class="pill-boxes">${boxes}</div>` : ''}`;
}

function pillHistoryCard(p) {
  const h = pillHistory(p);
  if (!h.length) return '';
  const w = pillWeek(p);
  const today = h.filter(r => r.date === localDate(new Date()));
  return `<div class="label">Historial</div><div class="card">
    <p class="pill-sum ${w.taken < w.total ? 'bad' : ''}">Últimos 7 días: se ha tomado ${w.taken} de ${w.total} tomas</p>
    ${today.map(pillRecord).join('')}
    <button class="btn" data-go="pillHistory">Ver todo el historial</button>
  </div>`;
}

function viewPillHistory() {
  const h = pillHistory(st().pills);
  const days = [...new Set(h.map(r => r.date))].sort().reverse();
  app.innerHTML = `${top('Historial de pastillas', 'pills')}
    ${days.length ? '' : '<p class="hint">Todavía no hay ninguna toma apuntada.</p>'}
    ${days.map(d => `<div class="label">${esc(pillDay(d))}</div><div class="card">
      ${h.filter(r => r.date === d).sort((a, b) => a.minutes - b.minutes).map(pillRecord).join('')}
    </div>`).join('')}`;
  bind();
}

function viewPillForm() {
  const d = (st().pills?.doses || []).find(x => x.id === S.view.id);
  const minutes = d ? d.minutes : 9 * 60;
  const time = `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  let photo = null;
  S.view.form = true;
  app.innerHTML = `${top(d ? 'Pastilla' : 'Nueva pastilla', 'pills')}
    <div class="card">
      <label class="field">Hora<input id="tm" type="time" value="${time}"></label>
      <label class="field">Cuántas<select id="cnt">${PILL_COUNTS.map(c => `<option value="${c}" ${(d ? d.count : 1) === c ? 'selected' : ''}>${pillCount(c)}</option>`).join('')}</select></label>
    </div>
    <div class="card">
      <div class="photo-pick">
        <span id="ph">${d ? pillPhoto(d) : `<span class="ph">${svg('pill')}</span>`}</span>
        <div class="btns"><span class="btn file-btn" style="display:grid;place-items:center">Elegir una foto<input type="file" accept="image/*" id="file"></span></div>
      </div>
      <p class="hint">Una foto de la caja, de frente y con luz: es lo que verá.</p>
    </div>
    <p class="warn" id="err" hidden></p>
    <button class="btn green" id="save">Guardar</button>
    ${d ? '<button class="btn danger" id="del">Quitar esta pastilla</button>' : ''}`;
  bind();
  $('#file').onchange = async e => {
    const f = e.target.files[0];
    if (!f) return;
    photo = await resizeImage(f, 1080);
    $('#ph').innerHTML = `<img class="ph" src="${photo}" alt="">`;
  };
  $('#save').onclick = async () => {
    const [h, m] = ($('#tm').value || '').split(':').map(Number);
    if (Number.isNaN(h) || Number.isNaN(m)) { $('#err').textContent = 'Elige la hora.'; $('#err').hidden = false; return; }
    const args = { minutes: h * 60 + m, count: Number($('#cnt').value) };
    if (photo) args.photo = photo;
    if (d) args.id = d.id;
    $('#save').disabled = true;
    try {
      await send(d ? 'updatePill' : 'addPill', args, { timeout: 30000 });
      go({ name: 'pills' });
    } catch (e) {
      $('#save').disabled = false;
    }
  };
  $('#del') && ($('#del').onclick = () => {
    if (!confirm('¿Quitar esta pastilla de los avisos?')) return;
    send('removePill', { id: d.id }).then(() => go({ name: 'pills' })).catch(() => {});
  });
}

function viewNews() {
  const s = st();
  const layout = parseLayout(s.homeLayout);
  const shown = layout.some(e => e.item === 'NEWS' && e.visible);
  const chosen = new Set(s.news || []);
  app.innerHTML = `${top('Periódicos', 'device')}
    <div class="card">
      ${switchRow('nwon', 'Mostrar el botón Periódicos', shown)}
      <p class="hint">La persona pasa de un periódico a otro con las flechas, baja por las noticias y toca una para verla en grande, escucharla o abrirla entera.</p>
      <p class="warn">Gasta datos móviles: unos pocos MB cada vez que se abre un periódico.</p>
    </div>
    ${PAPER_GROUPS.map(([group, list]) => `<div class="label">${esc(group)}</div>
      <div class="card">${Object.entries(list).map(([id, name]) => switchRow('np' + id, name, chosen.has(id))).join('')}</div>`).join('')}`;
  bind();
  $('#nwon').onchange = e => {
    layout.forEach(x => { if (x.item === 'NEWS') x.visible = e.target.checked; });
    send('setHomeLayout', { layout: encodeLayout(layout) }).catch(() => {});
  };
  Object.keys(PAPERS).forEach(id => $('#np' + id).onchange = e => {
    if (e.target.checked) chosen.add(id); else chosen.delete(id);
    send('setNewspapers', { ids: Object.keys(PAPERS).filter(x => chosen.has(x)) }).catch(() => {});
  });
}

function viewConnection() {
  const s = st();
  app.innerHTML = `${top('Conexión', 'device')}
    <div class="card">
      ${switchRow('wifi', 'Wi-Fi', s.wifi)}
      <p class="hint">Apagado, el móvil solo usa los datos móviles (y la gestión a distancia sigue funcionando por ellos). La red Wi-Fi se elige en el propio móvil.</p>
    </div>`;
  bind();
  $('#wifi').onchange = e => send('setWifi', { on: e.target.checked }).catch(() => {});
}

function viewSettings() {
  const s = st();
  const names = S.device.caregiverNames || {};
  S.view.form = true;
  app.innerHTML = `${top('Vinculación', 'device')}
    <div class="card">
      <label class="field">Nombre de la persona<input id="nm" type="text" maxlength="40" value="${esc(s.name || '')}"></label>
      <button class="btn" id="savenm">Guardar el nombre</button>
    </div>
    <div class="label">Cuidadores vinculados</div>
    <div class="card">${(S.device.caregivers || []).map(u => `<div>${esc(names[u] || 'Sin nombre')}${u === S.uid ? ' <span class="ok">(tú)</span>' : ''}</div>`).join('')}
    </div>
    <div class="label">Proyecto de la familia</div>
    <div class="card">
      <p class="hint">Proyecto de Firebase: <b>${esc(config.projectId)}</b></p>
      <button class="btn" id="share">Invitar a otro cuidador</button>
      <button class="btn" id="link">Vincular otro móvil</button>
    </div>
    <button class="btn danger" id="unlink">Desvincular este móvil</button>`;
  bind();
  $('#share').onclick = () => go({ name: 'share', form: true, back: 'settings' });
  $('#link').onclick = () => go({ name: 'link', form: true });
  $('#savenm').onclick = () => send('setName', { name: $('#nm').value }).catch(() => {});
  $('#unlink').onclick = async () => {
    if (!confirm('¿Dejar de gestionar este móvil? Para volver habrá que vincularlo otra vez con un código QR.')) return;
    await updateDoc(doc(db, 'devices', S.deviceId), { caregivers: arrayRemove(S.uid), ['caregiverNames.' + S.uid]: deleteField() });
    closeDevice();
    go({ name: 'home' });
  };
}

// Instalar como app (pantalla de inicio).
// (No en la app del cuidador, que ya lleva la web dentro: window.LlamaditApp.)
if ('serviceWorker' in navigator && !window.LlamaditApp) navigator.serviceWorker.register('sw.js').catch(() => {});

// Botón Atrás de Android en la app del cuidador: pulsa la flecha de la pantalla, si la hay.
// Devuelve false en la primera pantalla (entonces la app se cierra).
window.appBack = () => {
  const back = document.querySelector('.top .nav');
  if (!back) return false;
  back.click();
  return true;
};
