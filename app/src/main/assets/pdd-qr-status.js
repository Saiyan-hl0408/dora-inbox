/* Observe only the official QR ticket exchange result. Never retain request
 * bodies, tickets, cookies, user IDs, secrets, or full server responses. */
(function () {
  if (window.__parcelPddQrStatus) return;
  const status=window.__parcelPddQrStatus={stage:'idle',http:0,code:''};
  const relevant=url=>/\/api\/sigerus\/auth\/ticket(?:\?|$)/.test(String(url||''));
  function record(http,body) {
    status.http=Number(http)||0;
    let value;
    try { value=JSON.parse(body); } catch (_) { value={}; }
    const code=value.error_code ?? value.errorCode ?? value.code;
    status.code=/^-?\d{1,12}$/.test(String(code))?String(code):'';
    status.stage=status.http>=200 && status.http<300 && value.success!==false && (!status.code || status.code==='0')?'returned':'rejected';
  }
  const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(method,url){this.__parcelTicket=relevant(url);return open.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(){
    if(this.__parcelTicket) {
      status.stage='exchanging';
      this.addEventListener('load',()=>record(this.status,this.responseText));
      this.addEventListener('error',()=>{status.stage='network_error';status.http=0;status.code='';});
    }
    return send.apply(this,arguments);
  };
  const original=window.fetch;
  if(original)window.fetch=function(input){
    const watch=relevant(typeof input==='string'?input:input?.url);
    if(watch)status.stage='exchanging';
    const pending=original.apply(this,arguments);
    if(watch)pending.then(response=>response.clone().text().then(body=>record(response.status,body))).catch(()=>{status.stage='network_error';status.http=0;status.code='';});
    return pending;
  };
})();
