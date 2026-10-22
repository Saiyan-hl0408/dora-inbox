package com.halo.expressassistant.hub

import android.app.Activity
import android.content.*
import android.net.Uri
import android.widget.Toast
import com.halo.expressassistant.data.Store

object IdentityLauncher {
    fun native(context:Context,key:String):Boolean {
        val route=IdentityRoutes.route(key)
        for(url in route.appUris)try {
            val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(route.packageName)
            if(context !is Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent);return true
        } catch(_:android.content.ActivityNotFoundException){} catch(_:SecurityException){}
        return false
    }
    fun open(context:Context,key:String,accountId:String="") {
        if(key=="cainiao"){open(context,"taobao",accountId);return}
        if(key=="taobao") {
            val accounts=Store.accounts(context,"taobao").filter {it.enabled}
            val selected=accounts.firstOrNull {it.id==accountId}
            fun show(id:String){context.startActivity(Intent(context,TaobaoIdentityActivity::class.java).putExtra("account_id",id))}
            when {
                accountId.isNotBlank() && selected==null->Toast.makeText(context,"该淘宝账号已移除或暂停，请在绑定页检查",Toast.LENGTH_LONG).show()
                selected!=null->show(selected.id)
                accounts.size==1->show(accounts.single().id)
                accounts.isEmpty()->show("")
                else->OfficialAccountSheet.show(context,"taobao",accounts,accountId){show(it.id)}
            }
            return
        }
        if(key=="jd") {
            val accounts=Store.accounts(context,"jd").filter {it.enabled}
            fun show(id:String){context.startActivity(Intent(context,IdentityCodeActivity::class.java).putExtra("channel",key).putExtra("account_id",id))}
            val selected=accounts.firstOrNull {it.id==accountId}
            when {
                accountId.isNotBlank() && selected==null->Toast.makeText(context,"该京东账号已移除或暂停，请在绑定页检查",Toast.LENGTH_LONG).show()
                selected!=null->show(selected.id)
                accounts.size==1->show(accounts.single().id)
                accounts.size>1->OfficialAccountSheet.show(context,"jd",accounts,accountId){show(it.id)}
                else->show("")
            }
            return
        }
        if(key=="pdd") {
            if(native(context,key))return
            val launch=context.packageManager.getLaunchIntentForPackage(IdentityRoutes.route(key).packageName)
            if(launch!=null) {
                if(context !is Activity)launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launch)
                    Toast.makeText(context,"未能直达身份码，请在拼多多原 App 中打开，并核对当前账号",Toast.LENGTH_LONG).show()
                } catch(_:ActivityNotFoundException) {
                    Toast.makeText(context,"无法打开拼多多，请手动在原 App 出示身份码",Toast.LENGTH_LONG).show()
                } catch(_:SecurityException) {
                    Toast.makeText(context,"请手动打开拼多多，并在原 App 出示身份码",Toast.LENGTH_LONG).show()
                }
            } else Toast.makeText(context,"未安装拼多多，请安装后在原 App 出示身份码",Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(context,"请选择支持的官方取件服务",Toast.LENGTH_LONG).show()
    }
    fun openApp(context:Context,key:String) {
        if(native(context,key))return
        val launch=context.packageManager.getLaunchIntentForPackage(IdentityRoutes.route(key).packageName)
        if(launch!=null)context.startActivity(launch) else Toast.makeText(context,"未安装对应平台 App",Toast.LENGTH_LONG).show()
    }
}
