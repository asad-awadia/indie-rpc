package dev.aawadia.indierpc

import com.github.f4b6a3.ulid.UlidCreator
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json

data class Header(
  val success: Boolean = true,
  val error: String = "no-error-msg",
  val requestID: String = UlidCreator.getMonotonicUlid().toString()
)

data class RpcResponse(val header: Header, val payload: Buffer) {
  fun toBuffer(): Buffer = Json.encodeToBuffer(this)
}

data class RpcRequest(val header: Header, val payload: Buffer) {
  fun toBuffer(): Buffer = Json.encodeToBuffer(this)
}

data class RpcTarget(val service: String, val method: String, val version: String = "v1")

fun <T> fromBuffer(buffer: Buffer, messageType: Class<T>): T = Json.decodeValue(buffer, messageType)