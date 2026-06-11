package com.yue.browser.data.engine

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object LocalServer {
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set

    fun start(fileToServe: File): String {
        serverSocket?.close()
        serverSocket = ServerSocket(0)
        port = serverSocket!!.localPort
        
        thread {
            try {
                while (true) {
                    val client: Socket = serverSocket!!.accept()
                    thread {
                        try {
                            val out: OutputStream = client.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                            out.write("Content-Type: application/x-xpinstall\r\n".toByteArray())
                            out.write("Content-Length: ${fileToServe.length()}\r\n".toByteArray())
                            out.write("Connection: close\r\n\r\n".toByteArray())
                            
                            val input = FileInputStream(fileToServe)
                            input.copyTo(out)
                            input.close()
                            out.close()
                            client.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                // Server closed
            }
        }
        return "http://127.0.0.1:$port/extension.xpi"
    }
    
    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }
}
