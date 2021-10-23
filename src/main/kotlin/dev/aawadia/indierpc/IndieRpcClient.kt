package dev.aawadia.indierpc

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class IndieRpcClient(private val host: String = "localhost", private val port: Int = 9999) {
  private val v: Vertx = Vertx.vertx()
  private val client = v.createHttpClient(
    HttpClientOptions().setPipelining(true)
      .setPipeliningLimit(64)
      .setKeepAlive(true)
      .setMaxPoolSize(64)
      .setTryUseCompression(true)
  )

  fun <T> invokeRpcMethod(
    target: RpcTarget,
    requestBody: Any,
    responseType: Class<T>,
    timeoutSeconds: Long = 5
  ): Result<T> {
    val latch = CountDownLatch(1)
    val result = AtomicReference(Result.failure<T>(Throwable("failed")))
    client.request(HttpMethod.POST, port, host, "/${target.version}/${target.service}/${target.method}")
      .compose { it.send(RpcRequest(Header(), Json.encodeToBuffer(requestBody)).toBuffer()) }
      .compose { it.body() }
      .onSuccess { result.set(processResponseBuffer(it, responseType)) }
      .onFailure { result.set(Result.failure(it)) }
      .onComplete { latch.countDown() }
    latch.await(timeoutSeconds, TimeUnit.SECONDS)
    return result.get()
  }

  private fun <T> processResponseBuffer(buffer: Buffer, responseType: Class<T>): Result<T> {
    val rpcResponse = fromBuffer(buffer, RpcResponse::class.java)
    return when {
      rpcResponse.header.success -> runCatching { Json.decodeValue(rpcResponse.payload, responseType) }
      else -> Result.failure(Throwable(rpcResponse.header.error))
    }
  }
}
