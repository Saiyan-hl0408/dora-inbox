/* Keep only transport status for the three read-only endpoints. Never retain
 * request/response bodies, headers, URL parameters, identifiers or credentials. */
(function () {
  if (window.__parcelJdlTransport || !window.fetch) return;
  const status = window.__parcelJdlTransport = {};
  const routes = {'/relatives/queryRelativeIncludeSelfList':'accounts','/order/searchOrderEnhance':'list',
    '/search/getOrderEnhance':'detail','/search/getOrderEnhancePublic':'detail'};
  const original = window.fetch;
  window.fetch = function (input) {
    let key;
    try {
      const url = new URL(typeof input === 'string' || input instanceof URL ? String(input) : input.url, location.href);
      if (url.protocol === 'https:' && ['lop-proxy.jd.com','proxy.jdl.com'].includes(url.hostname)) key = routes[url.pathname];
    } catch (_) {}
    if (key) status[key] = {http:0,result:'pending'};
    const pending = original.apply(this,arguments);
    if (key) pending.then(response => {status[key] = {http:response.status,result:'returned'};},
      () => {status[key] = {http:0,result:'network_error'};});
    return pending;
  };
})();
