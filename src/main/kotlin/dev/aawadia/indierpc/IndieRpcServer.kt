package dev.aawadia.indierpc

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.Json
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaType

private data class ServiceRecord(
  val function: KFunction<*>,
  val service: KClass<*>,
  val cleanInstance: Boolean,
  val serviceInstance: Any?
)

class IndieRpcServer {
  private val serviceRegistry: MutableMap<String, ServiceRecord> = ConcurrentHashMap()
  private val serverStarted = AtomicBoolean(false)

  init {
    DatabindCodec.mapper().registerKotlinModule()
  }

  fun startServer(port: Int = 9999, workerPoolSize: Int = 64, requestTimeoutMs: Long = 5000) {
    if (serverStarted.get()) return

    val vertx = Vertx.vertx(VertxOptions().setWorkerPoolSize(workerPoolSize))
    val router = Router.router(vertx)

    router.post("/:version/:svc/:method")
      .handler(BodyHandler.create())
      .handler(ResponseTimeHandler.create())
      .handler(TimeoutHandler.create(requestTimeoutMs))
      .handler(LoggerHandler.create(LoggerFormat.SHORT))
      .blockingHandler(this::handleRpcPost, false)
      .failureHandler(this::handleFailure)

    val latch = CountDownLatch(1)

    vertx.createHttpServer(HttpServerOptions().setCompressionSupported(true))
      .requestHandler(router)
      .listen(port)
      .onSuccess { println("server started on port ${it.actualPort()}..") }
      .onFailure { it.printStackTrace() }
      .onComplete { serverStarted.set(it.failed().not()); latch.countDown() }

    latch.await()
  }

  fun registerService(name: String, service: KClass<*>, version: String = "v1", cleanInstance: Boolean = true) {

    fun validateServiceMethod(function: KFunction<*>): Boolean {
      return function.visibility == KVisibility.PUBLIC
          && function.parameters.size == 2
          && (function.parameters[1].type.classifier as KClass<*>).isData
          && (function.returnType.classifier as KClass<*>).isData
    }

    val instance = if (cleanInstance) null else service.createInstance()

    fun registerMethod(function: KFunction<*>) {
      serviceRegistry.putIfAbsent(
        "$version.$name.${function.name}",
        ServiceRecord(function, service, cleanInstance, instance)
      )
    }

    service.declaredFunctions
      .filter { validateServiceMethod(it) }
      .forEach { registerMethod(it) }
  }

  private fun invokeRpcMethod(rpcTarget: RpcTarget, rpcRequest: RpcRequest): Buffer {
    val serviceRecord =
      serviceRegistry["${rpcTarget.version}.${rpcTarget.service}.${rpcTarget.method}"] ?: return RpcResponse(
        Header(false, notFoundError("${rpcTarget.version}.$${rpcTarget.service}"), rpcRequest.header.requestID),
        Json.encodeToBuffer(Unit)
      ).toBuffer()

    val serviceInstance =
      if (serviceRecord.cleanInstance) serviceRecord.service.createInstance() else serviceRecord.serviceInstance
    val request = Json.decodeValue(rpcRequest.payload, serviceRecord.function.parameters[1].type.javaType as Class<*>)
    val response =
      serviceRecord.function.call(serviceInstance, request) ?: return nullResponse(rpcRequest.header.requestID)

    return RpcResponse(
      Header(true, "", requestID = rpcRequest.header.requestID), Json.encodeToBuffer(response)
    ).toBuffer()
  }

  private fun nullResponse(requestId: String): Buffer {
    return RpcResponse(
      Header(false, "null returned", requestID = requestId), Json.encodeToBuffer(Unit)
    ).toBuffer()
  }

  private fun handleRpcPost(routingContext: RoutingContext) {
    routingContext.response()
      .end(
        invokeRpcMethod(
          RpcTarget(
            routingContext.pathParam("svc"),
            routingContext.pathParam("method"),
            routingContext.pathParam("version")
          ),
          fromBuffer(routingContext.body, RpcRequest::class.java)
        )
      )
  }

  private fun handleFailure(routingContext: RoutingContext) {
    routingContext.response()
      .setStatusCode(500)
      .end(createFailureBuffer("internal server error"))
  }

  private fun notFoundError(type: String): String = "$type not found"

  private fun createFailureBuffer(errorMsg: String): Buffer =
    RpcResponse(Header(false, errorMsg), Json.encodeToBuffer(Unit)).toBuffer()
}