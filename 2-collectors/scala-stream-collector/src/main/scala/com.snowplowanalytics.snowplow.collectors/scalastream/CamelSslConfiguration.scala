package com.snowplowanalytics.snowplow.collectors.scalastream

import javax.net.ssl.SSLContext

import org.apache.camel.util.jsse.{SSLContextParameters, KeyManagersParameters, KeyStoreParameters}
import spray.io.ServerSSLEngineProvider

/**
 * Created by safouane on 30/06/15.
 */
trait CamelSslConfiguration {

  // if there is no SSLContext in scope implicitly the HttpServer uses the default SSLContext,
  implicit def sslContext: SSLContext = {

    val keyStoreFile = ""
    val keyStorePassword = ""
    val filePassword = ""

    val ksp = new KeyStoreParameters()
    ksp.setResource(keyStoreFile);
    ksp.setPassword(keyStorePassword)

    val kmp = new KeyManagersParameters()
    kmp.setKeyStore(ksp)
    kmp.setKeyPassword(filePassword)

    val scp = new SSLContextParameters()
    scp.setKeyManagers(kmp)

    val context = scp.createSSLContext()
    context
  }


  implicit def sslEngineProvider: ServerSSLEngineProvider = {
    ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }
  }




}