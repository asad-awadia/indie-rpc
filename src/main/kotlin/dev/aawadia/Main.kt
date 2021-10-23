package dev.aawadia

import dev.aawadia.indierpc.IndieRpcClient
import dev.aawadia.indierpc.IndieRpcServer
import dev.aawadia.indierpc.RpcTarget
import java.security.MessageDigest
import java.util.*

class ShaService {
  data class ShaRequest(val data: String)
  data class ShaResponse(val sha: String)

  fun sha(shaRequest: ShaRequest): ShaResponse {
    return ShaResponse(MessageDigest.getInstance("SHA-256").digest(shaRequest.data.encodeToByteArray()).toHexString())
  }

  private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}

fun main() {
  val rpc = IndieRpcServer()
  rpc.registerService("sha.svc", ShaService::class)
  rpc.startServer()
  val client = IndieRpcClient()
  val result: Result<ShaService.ShaResponse> = client.invokeRpcMethod(
    RpcTarget("sha.svc", "sha"),
    ShaService.ShaRequest(UUID.randomUUID().toString()),
    ShaService.ShaResponse::class.java
  )
  println(result)
}