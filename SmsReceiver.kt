package com.example.dakhlokharj

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.regex.Pattern

/** Reads SMS briefly to extract a configured amount/type. Message text is not retained. */
class SmsReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val prefs=context.getSharedPreferences("bank_patterns",Context.MODE_PRIVATE)
  for(sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)){
   // Process each message only in memory; never persist the raw SMS body.
   if(sms.messageBody.isNullOrBlank()) continue
   val body=sms.messageBody.orEmpty()
   var match:Triple<String,Long,String>?=null
   bankLoop@ for(bank in prefs.getStringSet("banks", emptySet()).orEmpty()) for(type in listOf("income","expense")) {
    val regex=prefs.getString("${bank}_${type}","") ?: ""
    if(regex.isBlank())continue
    try { val m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(body); if(m.find()) { val amount=m.group(1)?.replace(",","")?.replace("٬","")?.replace(" ","")?.replace(Regex("[۰-۹]")){(it.value[0].code-0x06F0).toString()}?.replace(Regex("[٠-٩]")){(it.value[0].code-0x0660).toString()}?.toLongOrNull(); if(amount!=null && amount>0){match=Triple(bank,amount,type);break@bankLoop} } } catch(_:Exception){}
   }
   val hit=match?:continue
   val i=Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP;putExtra("pending_amount",hit.second);putExtra("pending_bank",hit.first);putExtra("pending_type",hit.third);putExtra("pending_time",sms.timestampMillis)}
   val requestCode=(sms.timestampMillis % Int.MAX_VALUE).toInt()
   val pi=PendingIntent.getActivity(context,requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("txns","تراکنش بانکی",NotificationManager.IMPORTANCE_HIGH).apply { description="اعلان تراکنش شناسایی شده" })
   val n=NotificationCompat.Builder(context,"txns").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(if(hit.third=="income")"واریز شناسایی شد" else "برداشت شناسایی شد").setContentText("${hit.second} · ${hit.first} — برای ثبت باز کن").setContentIntent(pi).setAutoCancel(true).build()
   try{NotificationManagerCompat.from(context).notify(hit.first.hashCode()+hit.third.hashCode(),n)}catch(_:SecurityException){}
  }
 }
}
