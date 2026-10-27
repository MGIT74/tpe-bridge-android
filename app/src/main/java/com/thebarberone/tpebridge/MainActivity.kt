package com.thebarberone.tpebridge
import android.app.*
import android.os.*
import android.content.*
import android.widget.*
class MainActivity: Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 val prefs=getSharedPreferences("config",MODE_PRIVATE)
 val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,36,32,20) }
 val fields=listOf("server" to "URL serveur HTTPS","salon" to "Slug du salon","key" to "Clé du pont","ip" to "IP du TPE","port" to "Port TPE (8888)","pos" to "Numéro de caisse (2)","printerIp" to "IP de l'imprimante réseau","printerPort" to "Port imprimante (9100)")
 val edits=fields.map { (k,h) -> EditText(this).also { it.hint=h; it.setSingleLine(); it.setText(prefs.getString(k,if(k=="server") "https://app.thebarberone.com" else if(k=="port") "8888" else if(k=="pos") "2" else if(k=="printerPort") "9100" else "")); layout.addView(it) } }
 val status=TextView(this).apply { text="Le pont doit rester actif sur le Wi-Fi du salon."; layout.addView(this) }
 val start=Button(this).apply { text="Enregistrer et démarrer"; layout.addView(this) }
 start.setOnClickListener {
 val values=fields.indices.associate { fields[it].first to edits[it].text.toString().trim() }
 if(values.values.any { it.isBlank() } || !values["server"]!!.startsWith("https://")) { status.text="Tous les champs sont requis, serveur en HTTPS."; return@setOnClickListener }
 prefs.edit().apply { values.forEach { (k,v)->putString(k,v) } }.apply()
 try { androidxStart(); status.text="Service démarré : vérifiez la notification." } catch(e:Exception) { status.text=e.message }
 }
 val stop=Button(this).apply { text="Arrêter le pont"; layout.addView(this) }
 stop.setOnClickListener { stopService(Intent(this,BridgeService::class.java)); status.text="Pont arrêté" }
 val scroll=ScrollView(this);scroll.addView(layout);setContentView(scroll)
 }
 private fun androidxStart() { val i=Intent(this,BridgeService::class.java); if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i) }
}