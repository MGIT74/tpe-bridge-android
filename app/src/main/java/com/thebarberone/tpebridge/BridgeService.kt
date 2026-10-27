package com.thebarberone.tpebridge
import android.app.*
import android.content.*
import android.os.*
import org.json.JSONObject
import java.net.*
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
class BridgeService: Service() {
 private val running=AtomicBoolean(false)
 private var worker:Thread?=null
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
 if(Build.VERSION.SDK_INT>=26) {
 val channel=NotificationChannel("bridge","TPE Bridge",NotificationManager.IMPORTANCE_LOW)
 (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
 }
 val notification=if(Build.VERSION.SDK_INT>=26)
 Notification.Builder(this,"bridge").setContentTitle("TPE Bridge actif").setContentText("Connexion caisse et terminal Nepting").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
 else Notification.Builder(this).setContentTitle("TPE Bridge actif").setContentText("Connexion caisse et terminal Nepting").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
 startForeground(1,notification)
 if(running.compareAndSet(false,true)) { worker=Thread { loop() }.apply { start() } }
 return START_STICKY
 }
 override fun onDestroy(){running.set(false);worker?.interrupt();super.onDestroy()}
 private fun request(path:String, body:JSONObject?=null):JSONObject {
 val p=getSharedPreferences("config",MODE_PRIVATE)
 val base=p.getString("server","")!!.trimEnd('/')
 val salon=p.getString("salon","")!!
 val suffix=if(body==null) "?salon="+java.net.URLEncoder.encode(salon,"UTF-8") else ""
 val url=URL(base+path+suffix)
 val c=(url.openConnection() as HttpURLConnection).apply {
 connectTimeout=10000;readTimeout=15000
 setRequestProperty("X-Bridge-Key",p.getString("key","")!!)
 setRequestProperty("X-Salon-Slug",salon)
 if(body!=null) {requestMethod="POST";doOutput=true;setRequestProperty("Content-Type","application/json")}
 }
 try {
 if(body!=null) c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
 val stream=if(c.responseCode in 200..299) c.inputStream else c.errorStream
 val content=stream?.bufferedReader()?.use {it.readText()} ?: "{}"
 if(c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}: $content")
 return JSONObject(content)
 } finally {c.disconnect()}
 }
 private fun loop(){
 while(running.get() && !Thread.currentThread().isInterrupted) {
 try {
 val prints=request("/api/tpe/bridge/poll").optJSONArray("jobs")
 if(prints!=null) for(i in 0 until prints.length()) {
 val job=prints.getJSONObject(i)
 val ack=JSONObject().put("job_id",job.getString("id"))
 try {
   printTicket(job.getString("text"),job.optString("mode","escpos"))
   ack.put("ok",true)
 } catch(e:Exception) {
   ack.put("ok",false).put("error",e.message ?: "Impression réseau impossible")
 }
 // Do not replay a ticket after uncertain ACK: avoid duplicate paper/drawer.
 try {request("/api/tpe/bridge/ack",ack)}catch(_:Exception){}
 }
 val jobs=request("/api/tpe/bridge/charge-poll").optJSONArray("jobs")
 if(jobs!=null) for(i in 0 until jobs.length()) {
 val job=jobs.getJSONObject(i)
 val ack=JSONObject().put("job_id",job.getString("id"))
 try {ack.put("result",charge(job.getInt("amount_cents")))}
 catch(e:Exception){ack.put("error",e.message ?: "Erreur TPE")}
 try {request("/api/tpe/bridge/charge-ack",ack)}catch(_:Exception){}
 }
 }catch(_:Exception){}
 try {Thread.sleep(3000)}catch(_:InterruptedException){break}
 }
 }

 private fun printTicket(text:String,mode:String) {
  val prefs=getSharedPreferences("config",MODE_PRIVATE)
  val host=prefs.getString("printerIp","")!!.trim()
  val port=prefs.getString("printerPort","9100")!!.toInt()
  if(host.isEmpty() || port !in 1..65535) throw IOException("IP/port imprimante non configurés")
  val bytes=when(mode) {
   "drawer" -> {
     // The existing server encodes drawer ESC/POS bytes as Latin-1 characters.
     ByteArray(text.length){i->text[i].code.toByte()}
   }
   "text" -> text.toByteArray(Charsets.UTF_8)
   else -> {
     val cp850=java.nio.charset.Charset.forName("IBM850")
     byteArrayOf(0x1B,0x40,0x1B,0x74,0x02) +
     text.toByteArray(cp850) +
     byteArrayOf(0x0A,0x0A,0x0A,0x1D,0x56,0x42,0x00)
   }
  }
  Socket().use {socket->
   socket.connect(InetSocketAddress(host,port),5000)
   socket.soTimeout=5000
   socket.getOutputStream().write(bytes)
   socket.getOutputStream().flush()
  }
 }
 private fun tlv(tag:String,value:String)=tag+value.length.toString().padStart(3,'0')+value
 private fun charge(amount:Int):JSONObject {
 require(amount>0)
 val p=getSharedPreferences("config",MODE_PRIVATE)
 val pos=p.getString("pos","2")!!.filter{it.isDigit()}.ifEmpty{"2"}
 val frame=tlv("CZ","0320")+tlv("CJ",pos.padStart(12,'0').takeLast(12))+
 tlv("CA",pos.padStart(2,'0').takeLast(2))+tlv("CB",amount.toString())+
 tlv("CD","0")+tlv("CE","978")+"\r\n"
 val socket=Socket()
 socket.connect(InetSocketAddress(p.getString("ip","")!!,p.getString("port","8888")!!.toInt()),10000)
 socket.soTimeout=120000
 try {
 socket.getOutputStream().write(frame.toByteArray(Charsets.US_ASCII));socket.getOutputStream().flush()
 val data=StringBuilder();val buf=ByteArray(1024)
 while(true) {
 val n=socket.getInputStream().read(buf);if(n<0) break
 data.append(String(buf,0,n,Charsets.US_ASCII))
 val fields=parse(data.toString())
 if(fields.containsKey("AE")) return interpret(fields)
 if(data.length>10000) throw IOException("Réponse TPE trop longue")
 }
 throw IOException("Connexion TPE fermée sans réponse complète")
 }finally{socket.close()}
 }
 private fun parse(s:String):Map<String,String> {
 val clean=s.replace("\r","").replace("\n","");val out=mutableMapOf<String,String>();var i=0
 while(i+5<=clean.length){
 val tag=clean.substring(i,i+2);val len=clean.substring(i+2,i+5).toIntOrNull()
 if(!tag.matches(Regex("[A-Z0-9]{2}"))||len==null){i++;continue}
 if(i+5+len>clean.length)break
 out[tag]=clean.substring(i+5,i+5+len);i+=5+len
 }
 return out
 }
 private fun interpret(f:Map<String,String>):JSONObject {
 val code=f["AE"] ?: throw IOException("Champ AE absent")
 val ok=code=="10"
 return JSONObject().put("success",ok).put("resultCode",code)
 .put("failureCode",if(ok)JSONObject.NULL else f["AF"]?:code)
 .put("failureReason",if(ok)JSONObject.NULL else "Paiement refusé/annulé (AE=$code)")
 .put("authNumber",f["AC"]?:JSONObject.NULL)
 .put("amountCents",f["CB"]?.toIntOrNull()?:JSONObject.NULL)
 .put("currency",f["CE"]?:JSONObject.NULL)
 }
}