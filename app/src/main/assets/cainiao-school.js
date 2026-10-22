(function () {
  'use strict';
  if (window.__parcelCainiao) return;
  const api = 'mtop.cainiao.pickup.xiaoyuan.queryMultisiteWaybill';
  const state = window.__parcelCainiao = { events: [], hooked: false, target: '', busy: false };
  // Only the response fields used by our parser cross the WebView boundary. No phones or tokens.
  function safe(response) {
    const data = response && response.data || {};
    const r = data.result || data.data || {};
    return { ret: response && response.ret || [], data: { result: {
      securityPop: r.securityPop ? true : null,
      stationMessageAndPackages: (r.stationMessageAndPackages || []).map(g => ({
        stationMessage: { stationName: g.stationMessage && g.stationMessage.stationName || '',
          stationAddress: g.stationMessage && (g.stationMessage.stationAddress || g.stationMessage.address) || '' },
        waybillPickUpResp: { certainPickupWaybills: (g.waybillPickUpResp && g.waybillPickUpResp.certainPickupWaybills || [])
          .filter(p => String(p.waybillNo || '').toUpperCase() === state.target.toUpperCase())
          .map(p => ({ waybillNo: p.waybillNo, pickupCode: p.pickupCode, status: p.status,
            packageStatus: p.packageStatus, outboundStatus: p.outboundStatus, signTime: p.signTime,
            collectionDay: p.collectionDay, upCpCode: p.upCpCode })) }
      }))
    } } };
  }
  function emit(response) { state.events.push(safe(response)); if (state.events.length > 8) state.events.shift(); }
  state.install = function (number) {
    state.target = number;
    if (state.hooked) return true;
    if (!window.lib || !window.lib.mtop || typeof window.lib.mtop.request !== 'function') return false;
    const original = window.lib.mtop.request;
    state.request = function (options) { return original.call(window.lib.mtop, options); };
    window.lib.mtop.request = function (options) {
      const promise = original.apply(this, arguments);
      if (promise && typeof promise.then === 'function') {
        promise.then(response => {
          const name = String(options && options.api || '').toLowerCase();
          if (name === api.toLowerCase() && String(options.data && options.data.searchValue || '').toUpperCase() === state.target.toUpperCase()) emit(response);
          // Re-read this same waybill only after the user completed the official confirmation.
          if (['mtop.cainiao.pickup.xiaoyuan.addsuswaybill', 'mtop.cainiao.pickup.xiaoyuan.addotherwaybill',
            'mtop.cainiao.pickup.search.confirmedphone'].includes(name) &&
            (response.ret || []).some(x => String(x).startsWith('SUCCESS'))) state.query(state.target);
        }, () => {});
      }
      return promise;
    };
    state.hooked = true;
    return true;
  };
  state.query = async function (number) {
    if (state.busy || !state.install(number)) return;
    state.busy = true;
    try {
      const response = await state.request({ api, v: '1.0',
        data: { searchTypes: JSON.stringify(['03']), searchValue: number, source: 'XY', stationType: 'XY',
          stVersion: '1.1', searchFrom: 'SY', bizVersion: '4.0', terminal: 'ST' },
        ext_headers: { miniappSrc: 'ST', asac: '2A24C032ASAUX2BUE25HHF', sourceUrl: location.href, productType: 'XY' },
        type: 'get', dataType: 'json', H5Request: true, timeout: 10000 });
      emit(response);
    } catch (e) { emit({ret: e && e.ret || ['REQUEST_FAILED']}); }
    finally { state.busy = false; }
  };
  state.fillSearch = function (number) {
    if (!state.install(number)) return false;
    const field = document.querySelector('input#J-search');
    if (!field) return false;
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(field, number);
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
    setTimeout(() => field.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true })), 100);
    return true;
  };
  state.verifyAccount = async function () {
    if (!window.lib || !window.lib.mtop) return false;
    try {
      const r = await window.lib.mtop.request({api:'mtop.cainiao.pickup.search.queryXnPickUpUserPhoneList',v:'1.0',
        data:{source:'XY',stationType:'XY'},type:'get',dataType:'json',H5Request:true,timeout:8000,
        ext_headers:{miniappSrc:'ST',asac:'2A24C032ASAUX2BUE25HHF',sourceUrl:location.href,productType:'XY'}});
      return (r.ret || []).some(x=>String(x).startsWith('SUCCESS'));
    } catch (_) { return false; }
  };
}());
