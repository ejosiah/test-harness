package com.josiahebhomenye.testharness

import io.netty.buffer.Unpooled._
import io.netty.channel.{Channel, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.LastHttpContent._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import com.josiahebhomenye.testharness.NettyToScalaHelpers._
import HttpHeaderNames._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by jay on 21/07/2016.
  */
class HttpRequestHandler(val action: Action)(implicit ec: ExecutionContext) extends SimpleChannelInboundHandler[FullHttpRequest]{

  val Continue = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, EMPTY_BUFFER)
  val Response404 = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, EMPTY_BUFFER)
  val defaultResponse = (req: HttpRequest) => new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.OK)

  override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
    request.if100ContinueExpected( ctx.write(Continue) )

    if(request.uri().contains("favicon.ico")) {
      ctx.writeAndFlush(Response404).onSuccess { case ch => ch.close() }
      return
    }else{
      val response = defaultResponse(request)
      request.ifKeepAlive( response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE) )
      action(ctx, request, response)
    }

    val f: Future[Channel] = ctx.writeAndFlush(EMPTY_LAST_CONTENT)
    f.onComplete{
      case Success(ch) => ch.close()
      case Failure(t) => throw t
    }
  }

}
