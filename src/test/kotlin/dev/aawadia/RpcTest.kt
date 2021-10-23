package dev.aawadia

import dev.aawadia.indierpc.IndieRpcClient
import dev.aawadia.indierpc.IndieRpcServer
import dev.aawadia.indierpc.RpcTarget
import org.junit.Test

class RpcTest {
  @Test
  fun testRpc() {
    val serviceName = "math.svc"
    val const = 5
    val x = 1
    val y = 2
    val indieServer = IndieRpcServer()
    indieServer.registerService(serviceName, MyFakeTestMathService(const))
    indieServer.startServer()
    val indieClient = IndieRpcClient()
    val addResponseResult = indieClient.invokeRpcMethod(
      RpcTarget(serviceName, "sum"),
      MyFakeTestMathService.AddRequest(x, y),
      MyFakeTestMathService.AddResponse::class.java
    )
    assert(addResponseResult.isSuccess)
    assert(addResponseResult.getOrThrow().sum == (x + y + const))
  }
}

class MyFakeTestMathService(private val const: Int) {
  data class AddRequest(val x: Int, val y: Int)
  data class AddResponse(val sum: Int)

  fun sum(addRequest: AddRequest): AddResponse {
    return AddResponse(addRequest.x + addRequest.y + const)
  }
}
