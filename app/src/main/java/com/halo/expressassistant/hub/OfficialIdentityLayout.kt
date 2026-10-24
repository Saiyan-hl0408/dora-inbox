package com.halo.expressassistant.hub

import android.webkit.JavascriptInterface
import android.webkit.WebView

/** Only layout dimensions cross this bridge. Code values, images and credentials stay in the page. */
internal object OfficialIdentityLayout {
    fun attach(page:WebView,onLayout:(Boolean,Int)->Unit) {
        page.addJavascriptInterface(object {
            @JavascriptInterface fun layout(expanded:Boolean,height:Int){page.post {onLayout(expanded,height.coerceIn(240,720))}}
        },"DoraIdentityLayout")
    }
    fun apply(page:WebView,channel:String){page.evaluateJavascript(SCRIPT.replace("__CHANNEL__",channel),null)}
    fun collapse(page:WebView){page.evaluateJavascript("window.__doraIdentityCollapse&&window.__doraIdentityCollapse()",null)}
    val SCRIPT="""
        (function(){
          if(/login|passport|plogin/i.test(location.hostname+location.pathname))return false;
          if(window.__doraApplyIdentityLayout){window.__doraApplyIdentityLayout();return true;}
          var channel='__CHANNEL__',queued=false,last='',collapsedInitially=false,normalHeight=420,cainiaoRotate=null;
          function style(n,k,v){if(n&&n.style[k]!==v)n.style[k]=v;}
          function prefix(name){return document.querySelector('[class^="'+name+'--"],[class*=" '+name+'--"]');}
          function emit(full,height){var key=full+':'+height;if(key===last)return;last=key;try{DoraIdentityLayout.layout(full,Math.ceil(height));}catch(e){}}
          function jdToggle(){var row=document.querySelector('.qr-box .content-center');var control=row&&row.querySelector('.change-size,button');if(control)control.click();}
          window.__doraIdentityCollapse=function(){if(channel==='jd'){if(document.querySelector('.qr-box.plus'))jdToggle();}
            else {var row=prefix('rotate-button');var back=row&&Array.from(row.querySelectorAll('button')).find(function(b){return /返回|缩小/.test(b.innerText);});if(back)back.click();}};
          function fit(){
            queued=false;var vw=document.documentElement.clientWidth||innerWidth,vh=innerHeight;if(vw<48||vh<48)return;
            if(channel==='jd'){
              var box=document.querySelector('.qr-box'),wrapper=box&&box.closest('.wrapper');if(!box||!wrapper)return;
              var full=box.classList.contains('plus');
              if(!collapsedInitially){collapsedInitially=true;if(full){jdToggle();schedule();return;}}
              style(document.body,'margin','0px');style(document.body,'background','white');
              style(wrapper,'background','white');style(wrapper,'padding','0px');style(wrapper,'width',vw+'px');style(wrapper,'minHeight','0px');style(wrapper,'overflow','hidden');style(wrapper,'height',(full?vh:405)+'px');
              style(box,'boxSizing','border-box');style(box,'border','0px');style(box,'boxShadow','none');style(box,'borderRadius','0px');style(box,'margin','0px');
              style(box,'display','flex');style(box,'flexDirection','column');style(box,'justifyContent','space-between');style(box,'position','relative');
              style(box,'padding',full?'12px 20px':'20px 18px 10px');style(box,'fontFamily','sans-serif');style(box,'fontSize','13px');style(box,'lineHeight','1.6');
              style(box,'width',(full?vh-24:vw)+'px');style(box,'height',(full?vw:405)+'px');
              style(box,'left',full?'100%':'0px');style(box,'right','auto');style(box,'transform',full?'rotate(90deg)':'none');style(box,'transformOrigin','0 0');
              var phone=box.querySelector('.font-18'),value=box.querySelector('.font-12');
              style(phone,'fontSize','23px');style(phone,'height','auto');style(phone,'paddingBottom','8px');style(phone,'display',full?'none':'block');
              style(value,'fontSize','12px');style(value,'height','auto');style(value,'overflowWrap','anywhere');style(value,'whiteSpace','normal');style(value,'paddingTop','12px');
              var qr=box.querySelector('.qr');style(qr,'height',full?Math.max(100,vw-100)+'px':'126px');style(qr,'lineHeight','normal');style(qr,'margin',full?'0px':'22px 0 0');
              if(qr)Array.from(qr.querySelectorAll('svg')).forEach(function(svg){var w=parseFloat(svg.getAttribute('width')),h=parseFloat(svg.getAttribute('height'));if(w>80&&h>40){svg.setAttribute('viewBox','0 0 '+w+' '+h);svg.setAttribute('preserveAspectRatio','none');style(svg,'width','100%');style(svg,'height','100%');style(svg,'display','block');}});
              Array.from(box.querySelectorAll('.content-right,.content-center')).forEach(function(row){style(row,'padding','8px');style(row,'minHeight','48px');style(row,'display',full?'none':'flex');style(row,'justifyContent','center');style(row,'alignItems','center');});
              Array.from(box.children).forEach(function(child){if(child!==qr?.parentElement&&!child.classList.contains('content-right')&&!child.classList.contains('content-center'))style(child,'display',full?'none':'');});
              normalHeight=405;emit(full,normalHeight);
            }else{
              var identity=prefix('identity');if(!identity)return;
              var image=identity.querySelector('img[src^="data:image"]');if(!image||!image.complete||image.naturalWidth<80)return;
              var full=Array.from(identity.classList).some(function(c){return c.indexOf('rotate-wrap--')===0;});
              var rotated=prefix('rotate');if(rotated)cainiaoRotate=rotated;else if(cainiaoRotate&&identity.contains(cainiaoRotate))rotated=cainiaoRotate;
              var wrap=prefix('wraper'),phone=prefix('phone'),code=prefix('code'),refresh=prefix('refresh'),back=prefix('rotate-button'),ad=prefix('ad');
              style(document.body,'margin','0px');style(document.body,'background','white');
              style(identity,'boxSizing','border-box');style(identity,'background','white');style(identity,'border','0px');style(identity,'boxShadow','none');style(identity,'margin','0px');style(identity,'width',vw+'px');style(identity,'padding',full?'0px':'28px 18px 12px');style(identity,'height',full?vh+'px':'auto');style(identity,'minHeight','0px');style(identity,'position','relative');
              if(rotated){style(rotated,'position',full?'absolute':'static');style(rotated,'left',full?'50%':'auto');style(rotated,'top',full?'50%':'auto');style(rotated,'width',full?Math.max(240,vh-64)+'px':'100%');style(rotated,'height',full?Math.max(140,vw-40)+'px':'auto');style(rotated,'transform',full?'translate(-50%,-50%) rotate(90deg)':'none');style(rotated,'margin','0px');}
              style(wrap,'width','100%');style(wrap,'padding','0px');style(wrap,'margin','0px');style(wrap,'textAlign','center');
              style(phone,'fontSize','23px');style(phone,'lineHeight','1.5');style(phone,'margin','0px 0 8px');style(phone,'display',full?'none':'block');
              style(image,'width','100%');style(image,'height',full?Math.max(100,vw-100)+'px':'126px');style(image,'objectFit','fill');style(image,'margin',full?'0px':'28px 0 16px');style(image,'display','block');
              style(code,'fontSize','12px');style(code,'lineHeight','1.6');style(code,'margin','12px 0');style(code,'overflowWrap','anywhere');style(code,'whiteSpace','normal');
              style(refresh,'display',full?'none':'flex');style(refresh,'justifyContent','center');style(refresh,'gap','12px');style(refresh,'margin','18px 0 0');
              if(refresh)Array.from(refresh.querySelectorAll('button')).forEach(function(b){style(b,'height','48px');style(b,'minWidth','100px');style(b,'background','#f0f6fc');style(b,'border','0px');style(b,'borderRadius','11px');style(b,'color','#33516e');});
              style(back,'display','none');style(ad,'display',full?'none':'block');
              Array.from(document.querySelectorAll('[class^="banner--"],[class*=" banner--"]')).forEach(function(b){style(b,'display','none');});
              if(!full){
                var identityRect=identity.getBoundingClientRect(),contentBottom=identityRect.top+Math.max(identity.offsetHeight,identity.scrollHeight);
                if(ad&&getComputedStyle(ad).display!=='none')contentBottom=Math.max(contentBottom,ad.getBoundingClientRect().bottom);
                normalHeight=Math.max(360,Math.min(720,Math.ceil(contentBottom+window.scrollY)));
              }
              emit(full,normalHeight);
            }
          }
          function schedule(){if(!queued){queued=true;requestAnimationFrame(fit);}}
          window.__doraApplyIdentityLayout=schedule;
          window.addEventListener('resize',schedule);
          new MutationObserver(schedule).observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['class','src','width','height']});
          schedule();setTimeout(schedule,250);setTimeout(schedule,1000);return true;
        })();
    """.trimIndent()
}
