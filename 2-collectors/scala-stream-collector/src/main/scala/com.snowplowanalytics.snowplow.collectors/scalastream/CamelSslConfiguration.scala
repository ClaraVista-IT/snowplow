package com.snowplowanalytics.snowplow.collectors.scalastream

import javax.net.ssl.SSLContext

import org.apache.camel.util.jsse.{SSLContextParameters, KeyManagersParameters, KeyStoreParameters}
import spray.io.ServerSSLEngineProvider

/**
 * Created by safouane on 30/06/15.
 */
trait CamelSslConfiguration {

    // if there is no SSLContext in scope implicitly the HttpServer uses the default SSLContext,
    // since we want non-default settings in this example we make a custom SSLContext available here
    implicit def sslContext: SSLContext = {

      val keyStoreFile = "claratracking.jks"

      val ksp = new KeyStoreParameters()
      ksp.setResource(keyStoreFile);
      ksp.setPassword("")

      val kmp = new KeyManagersParameters()
      kmp.setKeyStore(ksp)
      kmp.setKeyPassword("")

      val scp = new SSLContextParameters()
      scp.setKeyManagers(kmp)

      val context = scp.createSSLContext()
      context
    }


    implicit def sslEngineProvider: ServerSSLEngineProvider = {
        ServerSSLEngineProvider { engine =>
            engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
            engine.setEnabledProtocols(Array("SSLv3", "TLSv1", "TLSv2"))
            engine
        }
}




}
