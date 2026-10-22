package com.halo.expressassistant.hub

import android.webkit.WebView
import android.view.Window
import android.view.WindowManager

/** Fits the official JD SVG; it never reads, generates or replaces the code's contents. */
internal object IdentityViewport {
    /** Both dedicated identity activities permit user screenshots, including release builds. */
    fun prepareWindow(window: Window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun fitJd(page: WebView) {
        page.evaluateJavascript(FIT_SCRIPT, null)
    }

    // Public JD identityCode currently uses .qr-box.plus { transform:rotate(90deg) }
    // and a JsBarcode SVG with an intrinsic width. Overview mode cannot scale that SVG
    // inside its own .qr region. Preserve its coordinates with viewBox, then contain it.
    val FIT_SCRIPT = """
        (function () {
          if (window.__parcelIdentityFit) { window.__parcelIdentityFit(); return true; }
          var queued = false;
          function style(node, key, value) {
            if (node && node.style[key] !== value) node.style[key] = value;
          }
          function number(value) { return parseFloat(value) || 0; }
          function margins(node) {
            var s = getComputedStyle(node); return number(s.marginTop) + number(s.marginBottom);
          }
          function fit() {
            queued = false;
            // This adapter intentionally does nothing on authorization/login pages.
            if (/login|passport|plogin/i.test(location.pathname + location.hostname)) return;
            var box = document.querySelector('.qr-box');
            var wrapper = box && box.closest('.wrapper');
            if (!box || !wrapper) return;
            var vw = document.documentElement.clientWidth || innerWidth;
            var vh = innerHeight;
            if (vw < 48 || vh < 48) return;
            var pad = Math.min(12, vw * 0.0266667);
            var width = vw - pad * 2, height = vh - pad * 2;
            var plus = box.classList.contains('plus');
            style(wrapper, 'boxSizing', 'border-box');
            style(wrapper, 'width', vw + 'px');
            style(wrapper, 'minHeight', vh + 'px');
            style(wrapper, 'padding', pad + 'px');
            style(wrapper, 'overflowX', 'hidden');
            style(box, 'boxSizing', 'border-box');
            style(box, 'width', (plus ? height : width) + 'px');
            style(box, 'height', (plus ? width : Math.min(height, vw * 0.946667)) + 'px');
            style(box, 'left', plus ? '100%' : '0px');
            style(box, 'right', 'auto');
            // Keep official orientation and change-size handler. Only constrain layout.
            style(box, 'fontSize', Math.max(12, Math.min(14, vw * 0.0373333)) + 'px');
            style(box, 'lineHeight', '1.35');
            var phone = box.querySelector('.font-18');
            style(phone, 'fontSize', Math.max(14, Math.min(18, vw * 0.048)) + 'px');
            style(phone, 'height', 'auto');
            style(phone, 'paddingBottom', '4px');
            var value = box.querySelector('.font-12');
            style(value, 'fontSize', '12px'); style(value, 'height', 'auto');
            Array.prototype.forEach.call(box.querySelectorAll('.content-right,.content-center'), function(row) {
              style(row, 'padding', '8px');
            });
            // Ignore the official loading spinner and wait for the actual large SVG.
            var barcode = Array.prototype.find.call(box.querySelectorAll('.qr svg'), function(svg) {
              return number(svg.getAttribute('width')) > 80 && number(svg.getAttribute('height')) > 40;
            });
            var qr = barcode && barcode.closest('.qr');
            if (!qr) return;
            var middle = qr.parentElement, fixed = 0;
            Array.prototype.forEach.call(box.children, function(child) {
              style(child, 'flexShrink', '0');
              if (child !== middle) fixed += child.offsetHeight + margins(child);
            });
            Array.prototype.forEach.call(middle.children, function(child) {
              if (child !== qr) fixed += child.offsetHeight + margins(child);
            });
            var bs = getComputedStyle(box);
            fixed += number(bs.borderTopWidth) + number(bs.borderBottomWidth) + number(bs.paddingTop) + number(bs.paddingBottom);
            var room = Math.max(24, number(box.style.height) - fixed - 4);
            style(qr, 'height', room + 'px'); style(qr, 'lineHeight', 'normal');
            style(qr, 'display', 'flex'); style(qr, 'alignItems', 'center');
            style(qr, 'justifyContent', 'center'); style(qr, 'minWidth', '0px');
            Array.prototype.forEach.call(qr.querySelectorAll('svg'), function(svg) {
              var w = number(svg.getAttribute('width')), h = number(svg.getAttribute('height'));
              if (w > 0 && h > 0 && (!svg.hasAttribute('viewBox') || svg.__parcelOwnViewBox)) {
                var coordinates = '0 0 ' + w + ' ' + h;
                if (svg.getAttribute('viewBox') !== coordinates) svg.setAttribute('viewBox', coordinates);
                svg.__parcelOwnViewBox = true;
              }
              svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
              style(svg, 'width', '100%'); style(svg, 'height', '100%');
              style(svg, 'maxWidth', '100%'); style(svg, 'maxHeight', '100%');
              style(svg, 'display', 'block'); style(svg, 'flexShrink', '1');
            });
          }
          function schedule() {
            if (queued) return; queued = true; requestAnimationFrame(fit);
          }
          window.__parcelIdentityFit = schedule;
          window.addEventListener('resize', schedule);
          new MutationObserver(schedule).observe(document.body, {childList:true, subtree:true, attributes:true, attributeFilter:['class','width','height']});
          schedule(); setTimeout(schedule, 250); setTimeout(schedule, 1000);
          return true;
        })();
    """.trimIndent()
}
