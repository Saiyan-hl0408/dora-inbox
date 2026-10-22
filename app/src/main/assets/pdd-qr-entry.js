(function () {
  if (location.protocol !== 'https:' || location.hostname !== 'mobile.yangkeduo.com' || location.pathname !== '/login.html') return 'waiting';
  function visible(node) {const r=node.getBoundingClientRect();return r.width>0 && r.height>0;}
  const qr=Array.from(document.querySelectorAll('.qr-code-login-container,.qr-code-box,.success-area')).find(visible);
  if (qr) {
    // Offer only the official QR mode. Leave all safety/identity-verification controls intact.
    const alternatives=new Set(['手机号登录','手机登录','验证码登录','短信登录','短信验证码登录','密码登录','微信登录','其他登录方式']);
    Array.from(document.querySelectorAll('button,a,span,div')).forEach(node=>{
      if (alternatives.has(node.textContent.trim()) && !node.contains(qr))node.style.display='none';
    });
    return 'ready';
  }
  if (/安全验证|完成验证|拖动滑块|点击验证/.test(document.body ? document.body.innerText : '')) return 'verification';
  if (window.__parcelhubQrOpened) return 'waiting';
  const entry=Array.from(document.querySelectorAll('button,a,span,div')).find(node=>node.textContent.trim()==='扫码登录' && visible(node));
  if (!entry) return 'waiting';
  window.__parcelhubQrOpened=true;
  entry.click();
  return 'opened';
}());
