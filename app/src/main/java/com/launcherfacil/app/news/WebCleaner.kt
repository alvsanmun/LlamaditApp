/*
 * LlamaditApp
 * Copyright (C) 2026 alvsanmun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.launcherfacil.app.news

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Quita lo que tapa la noticia en la web del periódico: el aviso de las cookies, los
 * anuncios que ocupan la pantalla y las ventanas de "regístrate". La persona no sabe leer
 * esos avisos ni cerrarlos.
 *
 * 1. No se descargan los gestores de cookies (Didomi, OneTrust, Sourcepoint, Quantcast…)
 *    ni los anuncios: sin ellos no sale el aviso. No se acepta nada en nombre de nadie.
 * 2. Por si el periódico lleva el aviso dentro de su propia web, un guion quita las capas
 *    fijas que tapan buena parte de la pantalla y vuelve a dejar desplazar la página.
 */
internal object WebCleaner {
    /** Dominios bloqueados (y sus subdominios). */
    private val BLOCKED = listOf(
        // Gestores de consentimiento de cookies.
        "privacy-mgmt.com", "privacy-center.org", "didomi.io", "cookielaw.org", "onetrust.com",
        "consensu.org", "quantcast.com", "cookiebot.com", "usercentrics.eu", "trustarc.com",
        "consentmanager.net", "cookiehub.net", "iubenda.com",
        // Anuncios (vídeos que se ponen solos, ventanas a pantalla completa).
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "adservice.google.es", "googletagservices.com", "amazon-adsystem.com", "taboola.com",
        "outbrain.com", "smartadserver.com", "teads.tv", "criteo.com", "criteo.net", "adnxs.com",
        "rubiconproject.com", "pubmatic.com", "seedtag.com", "richaudience.com", "sunmedia.tv",
        // Muros de suscripción que se cargan aparte.
        "tinypass.com", "piano.io", "poool.fr",
    )

    /** Subdominios que suelen ser de anuncios o de cookies en la propia web del periódico. */
    private val PREFIXES = listOf("ads.", "ad.", "adserver.", "cmp.", "consent.", "cookies.")

    private fun blocked(host: String): Boolean =
        BLOCKED.any { host == it || host.endsWith(".$it") } || PREFIXES.any { host.startsWith(it) }

    /** Para [android.webkit.WebViewClient.shouldInterceptRequest]: lo bloqueado vuelve vacío. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host?.lowercase() ?: return null
        if (!blocked(host)) return null
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /**
     * Guion que se ejecuta al empezar a pintar y al terminar de cargar. Vigila la página
     * unos segundos (los avisos suelen llegar tarde) y quita lo que tape la noticia.
     */
    const val SCRIPT = """
(function () {
  if (window.__llamaditCleaner) { window.__llamaditCleaner(); return; }
  var KNOWN = [
    '#didomi-host', '#didomi-notice', '.didomi-popup-container', '#onetrust-consent-sdk',
    '#onetrust-banner-sdk', '.qc-cmp2-container', '#qc-cmp2-container', '[id^="sp_message_container"]',
    '.sp_veil', '#CybotCookiebotDialog', '#usercentrics-root', '#truste-consent-track',
    '.fc-consent-root', '#cmpbox', '#cmpbox2', '.cmp-container', '#cookie-law-info-bar',
    '.cookie-banner', '.cookies-banner', '#cookie-banner', '.tp-modal', '.tp-backdrop',
    '[class*="paywall-modal"]', '[class*="cookie-consent"]', '[id*="cookie-consent"]'
  ];
  var LOCKS = ['didomi-popup-open', 'sp-message-open', 'qc-cmp-ui-showing', 'tp-modal-open',
    'modal-open', 'no-scroll', 'noscroll', 'overflow-hidden', 'is-locked'];
  function unlock() {
    [document.documentElement, document.body].forEach(function (el) {
      if (!el) return;
      LOCKS.forEach(function (c) { el.classList.remove(c); });
      var s = getComputedStyle(el);
      if (s.overflow === 'hidden' || s.overflowY === 'hidden') {
        el.style.setProperty('overflow', 'auto', 'important');
        el.style.setProperty('overflow-y', 'auto', 'important');
      }
      if (s.position === 'fixed') el.style.setProperty('position', 'static', 'important');
      el.style.removeProperty('top');
    });
  }
  function clean() {
    KNOWN.forEach(function (q) {
      document.querySelectorAll(q).forEach(function (el) { el.remove(); });
    });
    // Capas fijas: las que tapan más de un tercio de la pantalla (avisos, "suscríbete") y las
    // que flotan por debajo de la cabecera (ofertas, anuncios, vídeos que siguen al bajar).
    // La cabecera del periódico (pegada arriba) se queda.
    var area = window.innerWidth * window.innerHeight;
    var all = document.body ? document.body.querySelectorAll('div, section, aside, iframe, dialog, ins, footer') : [];
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var s = getComputedStyle(el);
      if (s.position !== 'fixed') continue;
      if (s.display === 'none' || s.visibility === 'hidden') continue;
      var r = el.getBoundingClientRect();
      var w = Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      var h = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));
      if (w * h === 0) continue;
      if (w * h > area / 3 || r.top > 70) el.remove();
    }
    // Ofertas que cuelgan de la cabecera (dentro de ella, pero por debajo).
    document.querySelectorAll('header [class*="offer"], header [class*="promo"], header [class*="banner"], [class*="subscribe-bar"], [class*="sticky-ad"], [class*="adhesion"]')
      .forEach(function (el) { el.remove(); });
    // Huecos de anuncios (aunque no se descarguen, dejan un recuadro vacío de "Publicidad").
    if (document.body) document.body.querySelectorAll('.ad, .ads, .adv, .advertising, [class*="advertising"], [class*="v-adv"], [class*="header-ad"], [class*="ad-slot"], [class*="ads-wall"], [id^="div-gpt-ad"], ins.adsbygoogle, [class*="publicidad"], [class*="revUnit"], [class*="skinRevenue"]')
      .forEach(function (el) { el.remove(); });
    // Recuadros con el rótulo "Publicidad" puesto por estilo (::before), sin anuncio dentro.
    if (document.body) document.body.querySelectorAll('div, aside, section').forEach(function (el) {
      if (el.children.length > 2) return;
      var label = getComputedStyle(el, '::before').content + getComputedStyle(el, '::after').content;
      if (/publicidad|advertisement|anuncio/i.test(label)) el.remove();
    });
    unlock();
  }
  window.__llamaditCleaner = clean;
  clean();
  var runs = 0;
  var timer = setInterval(function () { clean(); if (++runs > 20) clearInterval(timer); }, 500);
  var queued = false;
  try {
    new MutationObserver(function () {
      if (runs > 20 || queued) return;
      queued = true;
      setTimeout(function () { queued = false; clean(); }, 150);
    })
      .observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
})();
"""
}
