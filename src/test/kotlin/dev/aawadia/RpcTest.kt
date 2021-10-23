package dev.aawadia

import dev.aawadia.indierpc.IndieRpcClient
import dev.aawadia.indierpc.IndieRpcServer
import dev.aawadia.indierpc.RpcTarget
import org.junit.Test

class RpcTest {
  @Test
  fun testRpc() {
    val serviceName = "math.svc"
    val indieServer = IndieRpcServer()
    indieServer.registerService(serviceName, MyFakeTestMathService::class)
    indieServer.startServer()
    val indieClient = IndieRpcClient()
    val addResponseResult = indieClient.invokeRpcMethod(
      RpcTarget(serviceName, "sum"),
      MyFakeTestMathService.AddRequest(1, 2),
      MyFakeTestMathService.AddResponse::class.java
    )
    assert(addResponseResult.isSuccess)
    assert(addResponseResult.getOrThrow().sum == 3)
  }
}

class MyFakeTestMathService {
  data class AddRequest(val x: Int, val y: Int)
  data class AddResponse(val sum: Int)

  fun sum(addRequest: AddRequest): AddResponse {
    return AddResponse(addRequest.x + addRequest.y)
  }
}
