package com.example.dakhlokharj

import android.Manifest
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity:ComponentActivity(){
 override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);recreate()}
 private val categories=listOf("خوراک","خرید","خودرو و سوخت","قبض و شارژ","پوشاک","تفریح","درمان","آموزش","انتقال پول","سایر")
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val needed=mutableListOf<String>()
  listOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS).forEach { if(ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED) needed.add(it) }
  if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
  if(needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
  setContent { LedgerUI(this,categories) }
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerUI(ctx:Context,cats:List<String>){
 val db=AppDb.get(ctx); val dao=db.dao(); val scope= rememberCoroutineScope(); val prefs=ctx.getSharedPreferences("bank_patterns",Context.MODE_PRIVATE)
 val txns by dao.all().collectAsState(initial=emptyList())
 var limit by remember { mutableStateOf(prefs.getLong("daily_limit",0L).toString()) }
 var bank by remember{mutableStateOf("")}; var incomePattern by remember{mutableStateOf("")}; var expensePattern by remember{mutableStateOf("")}
 var showSettings by remember{mutableStateOf(false)}; var categoryDialog by remember{mutableStateOf(false)}; var pendingAmount by remember{mutableStateOf(0L)}; var pendingBank by remember{mutableStateOf("")}; var pendingType by remember{mutableStateOf("expense")}; var pendingDate by remember{mutableStateOf(System.currentTimeMillis())}; var manual by remember{mutableStateOf(false)}; var manualAmount by remember{mutableStateOf("")}; var manualType by remember{mutableStateOf("expense")}; var manualCategory by remember{mutableStateOf("سایر")}
 val dayStart=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
 val dayTx=txns.filter{it.dateMillis>=dayStart}; val daySpend=dayTx.filter{it.type=="expense"}.sumOf{it.amount}; val month=Calendar.getInstance().apply{set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis; val monthTx=txns.filter{it.dateMillis>=month}
 val income=monthTx.filter{it.type=="income"}.sumOf{it.amount};val spend=monthTx.filter{it.type=="expense"}.sumOf{it.amount}
 fun fmt(n:Long)=String.format(Locale("fa","IR"),"%,d",n)
 fun parseMoney(raw:String):Long?=raw.replace(",","").replace("٬","").replace(" ","").replace(Regex("[۰-۹]")){(it.value[0].code-0x06F0).toString()}.replace(Regex("[٠-٩]")){(it.value[0].code-0x0660).toString()}.toLongOrNull()
 fun save(amount:Long,type:String,cat:String,b:String,date:Long){scope.launch{dao.insert(Txn(amount=amount,dateMillis=date,bank=b,type=type,category=cat))}}
 fun sound(times:Int){Thread { try { val t=ToneGenerator(AudioManager.STREAM_NOTIFICATION,100); repeat(times){t.startTone(ToneGenerator.TONE_PROP_BEEP,250);Thread.sleep(450)};t.release() }catch(_:Exception){} }.start()}
 // Pending transaction: do not keep SMS content, just extracted fields.
 LaunchedEffect((ctx as Activity).intent?.getLongExtra("pending_amount",0L)){ val a=ctx.intent?.getLongExtra("pending_amount",0L)?:0L;if(a>0){pendingAmount=a;pendingBank=ctx.intent.getStringExtra("pending_bank")?:"بانک";pendingType=ctx.intent.getStringExtra("pending_type")?:"expense";pendingDate=ctx.intent.getLongExtra("pending_time",System.currentTimeMillis());categoryDialog=true;ctx.intent.removeExtra("pending_amount")}}
 LaunchedEffect(daySpend,limit){
  val l=parseMoney(limit)?:0L
  if(l>0){
   val dateKey=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())
   val alarmPrefs=ctx.getSharedPreferences("limit_alerts",Context.MODE_PRIVATE)
   if(daySpend>=l && alarmPrefs.getString("100_$dateKey","")!=dateKey){sound(2);alarmPrefs.edit().putString("100_$dateKey",dateKey).apply()}
   else if(daySpend>=l*85/100 && alarmPrefs.getString("85_$dateKey","")!=dateKey){sound(1);alarmPrefs.edit().putString("85_$dateKey",dateKey).apply()}
  }
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF70D6A0),background=Color(0xFF101010),surface=Color(0xFF1C1C1C))) {
  Scaffold(containerColor=Color(0xFF101010),topBar={TopAppBar(title={Text("دخل‌وخرج",fontWeight=FontWeight.Bold)},actions={IconButton(onClick={showSettings=true}){Icon(Icons.Default.Settings,"تنظیمات")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF101010),titleContentColor=Color.White))},floatingActionButton={FloatingActionButton(onClick={manual=true}){Icon(Icons.Default.Add,"ثبت")}}){p->
   Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SummaryCard("درآمد ماه",fmt(income),Color(0xFF70D6A0),Modifier.weight(1f));SummaryCard("خرج ماه",fmt(spend),Color(0xFFFF8C8C),Modifier.weight(1f))}
    SummaryCard("مانده خالص",fmt(income-spend),Color.White,Modifier.fillMaxWidth())
    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF242424)),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text("سقف خرج امروز",fontWeight=FontWeight.Bold);Text("${fmt(daySpend)} / ${fmt(parseMoney(limit)?:0L)} تومان",fontSize=18.sp);val l=parseMoney(limit)?:0L; LinearProgressIndicator(progress={if(l>0)(daySpend.toFloat()/l).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth().padding(top=8.dp),color=if(l>0&&daySpend>=l)Color.Red else Color(0xFF70D6A0));Text("هشدار در ۸۵٪ و ۱۰۰٪",fontSize=12.sp,color=Color.Gray)}}
    Text("تراکنش‌های اخیر",fontWeight=FontWeight.Bold,fontSize=18.sp)
    txns.take(40).forEach{t->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202020)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(t.type=="income")"واریز · ${t.bank}" else "${t.category} · ${t.bank}");Text(SimpleDateFormat("yyyy/MM/dd",Locale("fa","IR")).format(Date(t.dateMillis)),color=Color.Gray,fontSize=12.sp)};Text((if(t.type=="income")"+" else "−")+fmt(t.amount),color=if(t.type=="income")Color(0xFF70D6A0) else Color(0xFFFF8C8C))}}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={exportPdf(ctx,monthTx,income,spend)}){Icon(Icons.Default.PictureAsPdf,null);Spacer(Modifier.width(4.dp));Text("PDF ماه")};OutlinedButton(onClick={showSettings=true}){Text("الگوهای بانک")}}
   }
  }}
  if(categoryDialog){AlertDialog(onDismissRequest={categoryDialog=false},title={Text(if(pendingType=="income")"ثبت واریز" else "انتخاب دسته هزینه")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("${fmt(pendingAmount)} تومان · $pendingBank");if(pendingType=="expense")Text("این برداشت بابت چه بوده؟");if(pendingType=="expense")cats.forEach{cat->OutlinedButton(onClick={save(pendingAmount,pendingType,cat,pendingBank,pendingDate);categoryDialog=false},modifier=Modifier.fillMaxWidth()){Text(cat)}}}},confirmButton={if(pendingType=="income")TextButton(onClick={save(pendingAmount,pendingType,"درآمد",pendingBank,pendingDate);categoryDialog=false}){Text("ثبت واریز")}},dismissButton={TextButton(onClick={categoryDialog=false}){Text("بعداً")}})}
  if(manual){AlertDialog(onDismissRequest={manual=false},title={Text("ثبت تراکنش دستی")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(manualAmount,onValueChange={manualAmount=it},label={Text("مبلغ تومان")},placeholder={Text("مثلاً 250000")});Row{TextButton(onClick={manualType="income"}){Text("درآمد")};TextButton(onClick={manualType="expense"}){Text("هزینه")}};if(manualType=="expense")cats.forEach{TextButton(onClick={manualCategory=it}){Text(if(manualCategory==it)"✓ $it" else it)}}}},confirmButton={TextButton(onClick={parseMoney(manualAmount)?.takeIf{it>0}?.let{save(it,manualType,manualCategory,"دستی",System.currentTimeMillis())};manual=false}){Text("ثبت")}})}
  if(showSettings){AlertDialog(onDismissRequest={showSettings=false},title={Text("تنظیمات")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("سقف خرج روزانه (تومان)");OutlinedTextField(limit,onValueChange={limit=it.filter(Char::isDigit);prefs.edit().putLong("daily_limit",parseMoney(limit)?:0L).apply()},label={Text("سقف روزانه")});Divider();Text("تعریف الگوی بانک (Regex؛ مبلغ باید گروه اول باشد)");OutlinedTextField(bank,onValueChange={bank=it},label={Text("نام بانک")});OutlinedTextField(incomePattern,onValueChange={incomePattern=it},label={Text("Regex واریز")});OutlinedTextField(expensePattern,onValueChange={expensePattern=it},label={Text("Regex برداشت")});Text("نمونه: .*مبلغ[: ]+([0-9,]+).*",fontSize=12.sp);prefs.getStringSet("banks",emptySet()).orEmpty().forEach{Text("الگو ثبت شده: $it")}}},confirmButton={TextButton(onClick={if(bank.isNotBlank()){val b=bank.trim();val set=prefs.getStringSet("banks",emptySet()).orEmpty().toMutableSet().apply{add(b)};prefs.edit().putStringSet("banks",set).putString("${b}_income",incomePattern).putString("${b}_expense",expensePattern).apply()};showSettings=false}){Text("ذخیره")}},dismissButton={TextButton(onClick={showSettings=false}){Text("بستن")}})}
 }
@Composable fun SummaryCard(title:String,value:String,color:Color,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Color(0xFF222222)),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text(title,color=Color.Gray);Text(value,fontWeight=FontWeight.Bold,color=color,fontSize=20.sp);Text("تومان",fontSize=11.sp,color=Color.Gray)}}}

fun exportPdf(ctx:Context,transactions:List<Txn>,income:Long,expense:Long){
 val doc=PdfDocument();val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,1).create());val paint=Paint().apply{textSize=18f;color=android.graphics.Color.BLACK};val smaller=Paint().apply{textSize=13f;color=android.graphics.Color.BLACK};var y=60f
 fun line(s:String){page.canvas.drawText(s,540f,y,paint);y+=30}
 line("گزارش مالی ماهانه - دخل و خرج");line("مجموع درآمد: ${income} تومان");line("مجموع هزینه: ${expense} تومان");line("مانده: ${income-expense} تومان");y+=10;page.canvas.drawText("جزئیات تراکنش",540f,y,paint);y+=30
 transactions.take(24).forEach{if(y>780)return@forEach;page.canvas.drawText("${it.bank} | ${it.category} | ${it.type} | ${it.amount} تومان",540f,y,smaller);y+=22}
 doc.finishPage(page);val file=File(ctx.getExternalFilesDir(null),"monthly-report.pdf");try{FileOutputStream(file).use{doc.writeTo(it)};Toast.makeText(ctx,"PDF ذخیره شد: ${file.absolutePath}",Toast.LENGTH_LONG).show()}catch(e:Exception){Toast.makeText(ctx,"خطا در ساخت PDF",Toast.LENGTH_SHORT).show()};doc.close()
}
