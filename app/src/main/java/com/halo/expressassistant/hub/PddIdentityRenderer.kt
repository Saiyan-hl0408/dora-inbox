package com.halo.expressassistant.hub

import android.content.Context

/** Offline SVG renderer. Only the identity string reaches this document; no cookies or network. */
internal object PddIdentityRenderer {
    fun document(context:Context):String {
        val barcode=context.assets.open("JsBarcode.code128.min.js").bufferedReader().use {it.readText()}
        val qr=context.assets.open("qrcode-generator.js").bufferedReader().use {it.readText()}
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
            *{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:white;font-family:sans-serif;color:#18283d}
            #stage{position:relative;width:100%;height:100%}.normal{padding:26px 18px 12px;width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:space-between;gap:8px}
            .head{text-align:center;flex:none}.mobile{font-size:23px;font-weight:600;line-height:1.4}.caption{font-size:13px;color:#596d82;line-height:1.6;margin:8px 0 0}
            .barcode{width:100%;height:126px;display:block;flex:none}.value{font:12px/1.6 Arial,sans-serif;overflow-wrap:anywhere;text-align:center;color:#42556a;max-width:100%;flex:none}
            .privacy{font-size:12px;line-height:1.6;color:#596d82;text-align:center;margin:0;flex:none}
            button{font:14px sans-serif;min-height:48px;border:0;border-radius:11px;background:#edf5ff;color:#006aff;padding:0 20px;flex:none}button:focus-visible{outline:2px solid #006aff;outline-offset:2px}
            .qr svg{width:223px;height:223px;max-width:100%;display:block;flex:none}.rotor{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%) rotate(90deg);display:flex;flex-direction:column;align-items:center;gap:14px}
            .rotor .barcode{height:180px}.rotor .value{font-size:13px;white-space:nowrap;letter-spacing:.5px}.full-qr{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:24px;padding:24px}.full-qr svg{width:min(310px,100%);height:auto;max-height:75%;flex:none}
            </style></head><body><div id="stage"></div><script>$barcode</script><script>$qr</script><script>
            (function(){var current='',qrMode=false,expanded=false,mobile='';window.clearIdentity=function(){current='';document.getElementById('stage').replaceChildren()};
            window.drawIdentity=function(value,isQr,isExpanded,phone){current=value;qrMode=isQr;expanded=!!isExpanded;mobile=phone||'';var stage=document.getElementById('stage');stage.replaceChildren();if(!value)return;
              var box=document.createElement('div');box.className=expanded?(isQr?'full-qr':'rotor'):(isQr?'normal qr':'normal');stage.appendChild(box);
              if(!expanded){var head=document.createElement('div');head.className='head';var label=document.createElement('div');label.className='mobile';label.textContent=mobile;head.appendChild(label);var caption=document.createElement('p');caption.className='caption';caption.textContent='出示身份码取件';head.appendChild(caption);box.appendChild(head);}
              if(isQr){var code=qrcode(0,'M');code.addData(value);code.make();var qrBox=document.createElement('div');qrBox.innerHTML=code.createSvgTag({cellSize:5,margin:16,scalable:true});box.appendChild(qrBox.firstChild);}
              else{if(expanded)box.style.width=Math.max(180,stage.clientHeight-40)+'px';var svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.classList.add('barcode');box.appendChild(svg);JsBarcode(svg,value,{format:'CODE128',displayValue:false,height:174,width:2,margin:14});svg.setAttribute('viewBox','0 0 '+svg.getAttribute('width')+' '+svg.getAttribute('height'));svg.setAttribute('preserveAspectRatio','none');if(expanded)svg.style.height=Math.max(100,stage.clientWidth-120)+'px';}
              var text=document.createElement('div');text.className='value';text.textContent=value;box.appendChild(text);
              if(!expanded){var note=document.createElement('p');note.className='privacy';note.textContent='请勿随意分享身份码';box.appendChild(note);var button=document.createElement('button');button.textContent='⛶ 放大出示';button.setAttribute('aria-label','放大出示');button.onclick=function(){if(window.DoraPddLayout)DoraPddLayout.expand()};box.appendChild(button);}
            };window.addEventListener('resize',function(){if(current)window.drawIdentity(current,qrMode,expanded,mobile)});})();
            </script></body></html>""".trimIndent()
    }
}
