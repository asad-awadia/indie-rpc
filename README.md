# IndieRpc
An RPC library for Kotlin services that strives to 
balance developer productivity and performance.

IndieRpc is inspired by Golang's net/rpc package, Twitch's Twirp, and of course gRPC.

# Background

Working with gRPC was always difficult for both backend and client devs.
Backend devs had to write custom tooling to deal with load balancing as it did not play nicely
with existing http load balancers.

For client devs, the generated code was quite unergonomic to use and required a completely orthogonal
architecture to the already existing http layer that was already there.

# Usage

Register classes that implement your service interface. The only methods that will be registered
will be the ones that take in a single parameter of type data class and return a data class.

```kotlin
class MathService {
  data class AddRequest(val x: Int, val y: Int)
  data class AddResponse(val sum: Int)

  fun sum(addRequest: AddRequest): AddResponse {
    return AddResponse(addRequest.x + addRequest.y)
  }
}

val serviceName = "math.svc"

val indieServer = IndieRpcServer()
indieServer.registerService(serviceName, MathService::class)
indieServer.startServer()

val indieClient = IndieRpcClient()

// returns Result<AddResponse>
val addResponseResult = indieClient.invokeRpcMethod(
  RpcTarget(serviceName, "sum"),
  MathService.AddRequest(1, 2),
  MathService.AddResponse::class.java
)
```

## Tech notes

The server and client both use Vert.x. 
Service methods must be thread safe 
and can have blocking code.

## Roadmap

Depending on the interest in this project these are the future enhancements planned

1. Use proper logging instead of println
2. Micrometer metrics
3. Streaming messages via websockets
4. Tcp and udp support
5. Generate client SDK wrapper