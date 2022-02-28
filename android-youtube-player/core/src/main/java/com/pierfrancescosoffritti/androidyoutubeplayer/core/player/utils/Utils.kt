package com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.os.Build
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

internal object Utils {
    @Suppress("DEPRECATION")
    fun isOnline(context: Context): Boolean {
        var connected: Boolean
        (context.let {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = cm.activeNetwork ?: return false
                val actNw = cm.getNetworkCapabilities(networkCapabilities) ?: return false
                connected = actNw.hasCapability(NET_CAPABILITY_INTERNET)
            } else {
                val netInfo = cm.activeNetworkInfo
                connected = netInfo?.isConnectedOrConnecting == true
            }
        })
        return connected
    }

    fun readHTMLFromUTF8File(inputStream: InputStream): String {
        try {
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, "utf-8"))

            var currentLine: String? = bufferedReader.readLine()
            val sb = StringBuilder()

            while (currentLine != null) {
                sb.append(currentLine).append("\n")
                currentLine = bufferedReader.readLine()
            }

            return sb.toString()
        } catch (e: Exception) {
            throw RuntimeException("Can't parse HTML file.")
        } finally {
            inputStream.close()
        }
    }
}