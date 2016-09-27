package com.josiahebhomenye.testharness

import com.josiahebhomenye.testharness.DataSources._
import com.typesafe.config.Config
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpResponse, HttpRequest}
import io.netty.handler.codec.http.HttpHeaderNames._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import com.josiahebhomenye.testharness.NettyToScalaHelpers._


case class ResponseMap(path: String, method: String, response: String, contentType: Option[String] = None, responseHeaders: Map[String, String] = Map())


trait Action extends ((ChannelHandlerContext, HttpRequest, HttpResponse) => Unit){
  def dataProvider: HttpDataSource

  def apply(ctx: ChannelHandlerContext, req: HttpRequest, respHead: HttpResponse): Unit = dataProvider(req).foreach { result =>
    val (ct, headers, data) = result
    prepare(respHead, (ct, headers))
    ctx.write(respHead)
    process(ctx, req, data)
  }

  def process(ctx: ChannelHandlerContext, req: HttpRequest, data: ByteBuf): Unit

  def prepare(response: HttpResponse, header: (Option[ContentType], Headers)): Unit = {
    val (ct, others) = header
    ct.foreach(ct => response.headers().set(CONTENT_TYPE, ct))
    others.foreach((entry) => response.headers().set(entry._1, entry._2))
  }
}


class DefaultAction(override val dataProvider: HttpDataSource) extends Action {
  override def process(ctx: ChannelHandlerContext, req: HttpRequest, data: ByteBuf): Unit = ctx.writeAndFlush(data)
}

object SlowResponseAction{

  def apply(dataProvider: HttpDataSource, config: Config) =
    new SlowResponseAction(dataProvider
      , delay = (config.getInt("delay.min")
        , config.getInt("delay.max"))
      , chunkSize = config.getInt("chunkSize"))
}

class SlowResponseAction(override val dataProvider: HttpDataSource, val delay: (Int, Int), val chunkSize: Int)
  extends  Action{

  override def process(ctx: ChannelHandlerContext, req: HttpRequest, data: ByteBuf): Unit = {
    def anyTimeWithIn(range: (Int, Int)) = {
      val (min, max) = range
      (Random.nextInt(max - min + 1) + min) seconds
    }

    while(data.readableBytes() > 0){
      Thread.sleep(anyTimeWithIn(delay).toMillis)
      val amount = math.min(chunkSize, data.readableBytes())
      ctx.writeAndFlush(data.readBytes(amount))
    }
  }
}

class HeadNoBodyAction(override val dataProvider: HttpDataSource) extends Action {
  override def process(ctx: ChannelHandlerContext, req: HttpRequest, data: ByteBuf): Unit = ctx.writeAndFlush(Unpooled.buffer())
}

class ConnectionTerminatorAction(override val dataProvider: HttpDataSource) extends Action {
  override def process(ctx: ChannelHandlerContext, req: HttpRequest, data: ByteBuf): Unit = {
    val terminationPoint = data.readableBytes()/2
    val halfWay = data.copy(0, terminationPoint)
    ctx.writeAndFlush(halfWay).onSuccess{
      case ch => ch.close()
    }(ExecutionContext.global)
  }
}

object Actions {

  //val responses = Map(("/", "GET") -> new ResponseMap("/", "GET", response = randomEssay))
  val responses = Map(("/?postcode=ZZ01%201ZZ", "GET") -> addressLookup)

  def apply(name: String, config: Config) : Action = name match {
    case "slowResponse" => SlowResponseAction(dataSource(config), config)
    case "connectionTerminator" => new ConnectionTerminatorAction(dataSource(config))
    case "sendHeadWithNoBody" => new HeadNoBodyAction(dataSource(config))
    case _ => new DefaultAction(dataSource(config))
  }

  def dataSource(config: Config) = config.getString("source") match {
    case "responseMap" => new RequestBasedDataSource(responses)
    case "random" => new RandomDataSource(config.getInt("size"))
    case "proxy" => new ProxyDataSource(config.getString("proxy.protocol")
      , config.getString("proxy.host"), config.getInt("proxy.port"), config.getString("proxy.context"))
  }
}