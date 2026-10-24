package com.halo.expressassistant.hub

/** Use the live official page's read-only refresh action; no identity value leaves the page. */
internal object CainiaoIdentityBootstrap {
    val script="""
        (function(){
          if(location.hostname!=='pages-fast.m.taobao.com'||!location.pathname.endsWith('/identity-code'))return 'unavailable';
          if(Array.from(document.images).some(e=>e.src.startsWith('data:image')&&e.complete&&e.naturalWidth>100))return 'ready';
          if(window.__doraIdentityRequested)return 'pending';
          const buttons=Array.from(document.querySelectorAll('button')).filter(e=>e.innerText.trim()==='刷新'&&!e.disabled&&e.getBoundingClientRect().height>0);
          if(buttons.length!==1)return 'waiting';
          window.__doraIdentityRequested=true;buttons[0].click();return 'pending';
        })()
    """.trimIndent()
}
