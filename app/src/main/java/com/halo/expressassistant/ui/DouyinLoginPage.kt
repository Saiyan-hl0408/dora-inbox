package com.halo.expressassistant.ui

/** Changes presentation of the existing official form; never submits or exchanges login credentials. */
object DouyinLoginPage {
    fun official(url:String?):Boolean=runCatching {
        val uri=java.net.URI(url.orEmpty())
        uri.scheme=="https" && uri.host in setOf("www.douyin.com","douyin.com","login.douyin.com","sso.douyin.com")
    }.getOrDefault(false)

    // Inspect the natural official layout, including any verification frame hidden by a previous crop.
    val inspect="""
        (function(){
          var root=document.documentElement,previous=root.getAttribute('data-parcel-dy-mode');root.removeAttribute('data-parcel-dy-mode');
          try {
            var visible=function(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&s.opacity!=='0';};
            var text=Array.from(document.querySelectorAll('[role="dialog"],input')).filter(visible).map(function(e){return (e.innerText||'')+' '+(e.placeholder||'');}).join(' ');
            var challenge=/安全验证|验证身份|请完成验证|人脸验证|人脸识别|扫码验证/.test(text)||Array.from(document.querySelectorAll('iframe')).some(function(e){return visible(e)&&/captcha|verify|security|face|验证/i.test((e.title||'')+' '+(e.id||'')+' '+(e.className||''));});
            return {challenge:challenge};
          }finally {if(previous)root.setAttribute('data-parcel-dy-mode',previous);}
        })()
    """.trimIndent()

    val layout="""
        (function(){
          if(window.__parcelDouyinPhonePage)return window.__parcelDouyinPhonePage.apply();
          var root=document.documentElement,opened=false,timer;
          var style=document.createElement('style');style.id='parcel-douyin-phone-layout';
          style.textContent=`
            html[data-parcel-dy-mode="phone"],html[data-parcel-dy-mode="phone"] body{background:white!important;min-width:0!important;width:100%!important;overflow-x:hidden!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-hide]{display:none!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-path]{display:block!important;position:static!important;transform:none!important;width:100%!important;min-width:0!important;max-width:none!important;height:auto!important;min-height:0!important;overflow:visible!important;margin:0!important;padding:0!important;background:transparent!important;box-shadow:none!important;opacity:1!important;pointer-events:auto!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-form]{display:block!important;position:fixed!important;left:16px!important;top:20px!important;transform:none!important;box-sizing:border-box!important;min-width:0!important;width:var(--parcel-dy-width)!important;max-width:none!important;max-height:var(--parcel-dy-height)!important;overflow-y:auto!important;margin:0!important;padding:0!important;background:white!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-row]{width:100%!important;max-width:none!important;box-sizing:border-box!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-form] input{font-size:16px!important;min-height:46px!important;box-sizing:border-box!important;min-width:0!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-form] input[placeholder]{flex:1 1 0%!important;max-width:none!important;}
            html[data-parcel-dy-mode="phone"] [data-parcel-dy-tap]{min-height:44px!important;display:inline-flex!important;align-items:center!important;justify-content:center!important;font-size:15px!important;}
          `;
          (document.head||root).appendChild(style);
          var inspect=function(){return $inspect;};
          function visible(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
          function apply(){
            root.removeAttribute('data-parcel-dy-mode');
            if(inspect().challenge)return {mode:'challenge'};
            var phone=Array.from(document.querySelectorAll('input')).find(function(e){return visible(e)&&/手机号|手机号码/.test(e.placeholder||'');});
            if(!phone){
              if(!opened){var entry=Array.from(document.querySelectorAll('button,[role="button"],a,span')).find(function(e){return visible(e)&&(e.textContent||'').trim()==='登录';});if(entry){opened=true;entry.click();}}
              return {mode:'waiting'};
            }
            var panel=phone.parentElement;
            while(panel&&panel!==document.body){
              var code=Array.from(panel.querySelectorAll('input')).some(function(e){return /验证码/.test(e.placeholder||'');});
              var submit=Array.from(panel.querySelectorAll('button,[role="button"],div,span')).some(function(e){return (e.textContent||'').trim()==='登录';});
              if(code&&submit)break;panel=panel.parentElement;
            }
            if(!panel||panel===document.body)return {mode:'waiting'};
            document.querySelectorAll('[data-parcel-dy-hide],[data-parcel-dy-path],[data-parcel-dy-form],[data-parcel-dy-tap],[data-parcel-dy-row]').forEach(function(e){['hide','path','form','tap','row'].forEach(function(k){e.removeAttribute('data-parcel-dy-'+k);});});
            var viewport=window.visualViewport||window;
            var width=Math.min(440,Math.max(200,(viewport.width||innerWidth)-32))+'px',height=Math.max(160,(viewport.height||innerHeight)-40)+'px';
            if(root.style.getPropertyValue('--parcel-dy-width')!==width)root.style.setProperty('--parcel-dy-width',width);
            if(root.style.getPropertyValue('--parcel-dy-height')!==height)root.style.setProperty('--parcel-dy-height',height);
            panel.setAttribute('data-parcel-dy-form','');
            Array.from(panel.children).forEach(function(e){e.setAttribute('data-parcel-dy-row','');});
            var child=panel;
            for(var parent=child.parentElement;parent&&parent!==root;child=parent,parent=parent.parentElement){
              parent.setAttribute('data-parcel-dy-path','');
              Array.from(parent.children).forEach(function(e){if(e!==child&&e!==style&&e.tagName!=='SCRIPT')e.setAttribute('data-parcel-dy-hide','');});
            }
            panel.querySelectorAll('button,[role="button"],span,div').forEach(function(e){
              var text=(e.textContent||'').trim();
              if(e.querySelector('input')||!/^(登录|获取验证码|重新发送|重新获取)$/.test(text))return;
              var node=e;
              while(node.parentElement&&node.parentElement!==panel&&(node.parentElement.textContent||'').trim()===text&&!node.parentElement.querySelector('input'))node=node.parentElement;
              node.setAttribute('data-parcel-dy-tap','');
            });
            root.setAttribute('data-parcel-dy-mode','phone');return {mode:'phone'};
          }
          window.__parcelDouyinPhonePage={apply:apply};
          new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(apply,120);}).observe(root,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['style','class','title']});
          if(window.visualViewport)visualViewport.addEventListener('resize',function(){clearTimeout(timer);timer=setTimeout(apply,120);});
          return apply();
        })()
    """.trimIndent()
}
