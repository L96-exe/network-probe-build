package jp.ishiguro.networkprobe

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.mlkit.barcode.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.barcode.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var scanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.result)
        scanButton = findViewById(R.id.scan)

        scanButton.setOnClickListener {
            if (isConnectedToWifi()) {
                showResult(
                    "NG\nWi-FiをOFFにしてください\n\n" +
                        "4Gまたは5GをONにしてから、もう一度押してください。",
                    false
                )
            } else {
                openGoogleCodeScanner()
            }
        }
    }

    private fun openGoogleCodeScanner() {
        scanButton.isEnabled = false

        showResult(
            "QR読み取り画面を準備しています…",
            true
        )

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(
            this,
            options
        )

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val qrText = barcode.rawValue

                if (qrText.isNullOrBlank()) {
                    showResult(
                        "NG\nQRコードの内容を読み取れませんでした。",
                        false
                    )
                    scanButton.isEnabled = true
                } else {
                    startProbe(qrText)
                }
            }
            .addOnCanceledListener {
                showResult(
                    "QRコードの読み取りをキャンセルしました。",
                    false
                )
                scanButton.isEnabled = true
            }
            .addOnFailureListener { error ->
                showResult(
                    "NG\nQR読み取り画面を起動できませんでした\n\n" +
                        "${error.javaClass.simpleName}: " +
                        "${error.message ?: "詳細不明"}\n\n" +
                        "Google Play開発者サービスを更新してから再試行してください。",
                    false
                )
                scanButton.isEnabled = true
            }
    }

    private fun isConnectedToWifi(): Boolean {
        val manager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = manager.activeNetwork ?: return false
        val capabilities =
            manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI
        )
    }

    private fun startProbe(qrText: String) {
        scanButton.isEnabled = false

        showResult(
            "検査中…\n画面を閉じないでください。",
            true
        )

        thread {
            try {
                val json = JSONObject(qrText)

                val ip = json.getString("ip")
                val tcpPort = json.getInt("tcp")
                val udpPort = json.getInt("udp")
                val token = json.getString("token")
                val expires = json.getLong("expires")

                validateProbeData(
                    ip,
                    tcpPort,
                    udpPort,
                    token,
                    expires
                )

                val tcpSuccess = sendTcp(
                    ip,
                    tcpPort,
                    token
                )

                val udpSuccess = sendUdp(
                    ip,
                    udpPort,
                    token
                )

                val message = buildString {
                    append("外部検査完了\n\n")
                    append("TCP：")
                    append(
                        if (tcpSuccess) {
                            "送信成功"
                        } else {
                            "NG"
                        }
                    )

                    append("\nUDP：")
                    append(
                        if (udpSuccess) {
                            "送信成功"
                        } else {
                            "NG"
                        }
                    )

                    append("\n\n")
                    append("最終結果はPC画面で確認してください。")
                }

                showResult(
                    message,
                    tcpSuccess || udpSuccess
                )
            } catch (error: Exception) {
                showResult(
                    "NG\n検査できませんでした\n\n" +
                        "${error.javaClass.simpleName}: " +
                        "${error.message ?: "詳細不明"}",
                    false
                )
            } finally {
                runOnUiThread {
                    scanButton.isEnabled = true
                }
            }
        }
    }

    private fun validateProbeData(
        ip: String,
        tcpPort: Int,
        udpPort: Int,
        token: String,
        expires: Long
    ) {
        if (System.currentTimeMillis() / 1000 > expires) {
            throw IllegalArgumentException(
                "QRコードの有効期限が切れています。"
            )
        }

        if (tcpPort !in 1024..65535) {
            throw IllegalArgumentException(
                "TCPポート番号が正しくありません。"
            )
        }

        if (udpPort !in 1024..65535) {
            throw IllegalArgumentException(
                "UDPポート番号が正しくありません。"
            )
        }

        if (token.length < 16) {
            throw IllegalArgumentException(
                "診断トークンが正しくありません。"
            )
        }

        InetAddress.getByName(ip)
    }

    private fun sendTcp(
        ip: String,
        port: Int,
        token: String
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(ip, port),
                    7000
                )

                socket.getOutputStream()
                    .bufferedWriter(Charsets.UTF_8)
                    .use { writer ->
                        writer.write(token)
                        writer.newLine()
                        writer.flush()
                    }
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendUdp(
        ip: String,
        port: Int,
        token: String
    ): Boolean {
        return try {
            val destination =
                InetAddress.getByName(ip)

            val data =
                token.toByteArray(Charsets.UTF_8)

            DatagramSocket().use { socket ->
                val packet = DatagramPacket(
                    data,
                    data.size,
                    destination,
                    port
                )

                socket.send(packet)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun showResult(
        message: String,
        successColor: Boolean
    ) {
        runOnUiThread {
            resultText.text = message

            resultText.setTextColor(
                if (successColor) {
                    Color.rgb(20, 145, 80)
                } else {
                    Color.rgb(210, 45, 50)
                }
            )
        }
    }
}
