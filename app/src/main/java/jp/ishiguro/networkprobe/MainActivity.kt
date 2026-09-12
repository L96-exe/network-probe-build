package jp.ishiguro.networkprobe

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class MainActivity:AppCompatActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);findViewById<android.widget.Button>(R.id.scan).setOnClickListener{if(isWifi()){show("NG\nWi-FiをOFFにしてください",false)}else IntentIntegrator(this).setPrompt("PC画面のQRコードを読み取ってください").setBeepEnabled(false).initiateScan()}}
 override fun onActivityResult(r:Int,c:Int,d:android.content.Intent?){val x=IntentIntegrator.parseActivityResult(r,c,d);if(x!=null){if(x.contents!=null)probe(x.contents);else show("キャンセルしました",false)}else super.onActivityResult(r,c,d)}
 private fun isWifi():Boolean{val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val n=cm.activeNetwork?:return false;return cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
 private fun probe(s:String){show("検査中…",true);thread{try{val j=JSONObject(s);val ip=j.getString("ip");val tcp=j.getInt("tcp");val udp=j.getInt("udp");val token=j.getString("token");val exp=j.getLong("expires");if(System.currentTimeMillis()/1000>exp)throw Exception("QRコードの期限切れです")
 var tok=false;try{Socket().use{x->x.connect(InetSocketAddress(ip,tcp),5000);x.getOutputStream().write((token+"\n").toByteArray());x.getOutputStream().flush();tok=true}}catch(_:Exception){}
 var uok=false;try{DatagramSocket().use{x->val b=token.toByteArray();x.send(DatagramPacket(b,b.size,java.net.InetAddress.getByName(ip),udp));uok=true}}catch(_:Exception){}
 show("送信完了\nTCP: ${if(tok)"送信済み" else "失敗"}\nUDP: ${if(uok)"送信済み" else "失敗"}\n\n最終結果はPCに表示されます",tok||uok)}catch(e:Exception){show("NG\n${e.message}",false)}}}
 private fun show(t:String,ok:Boolean){runOnUiThread{findViewById<android.widget.TextView>(R.id.result).apply{text=t;setTextColor(if(ok)Color.rgb(20,145,80) else Color.rgb(210,45,50))}}}
}