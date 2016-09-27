package com.josiahebhomenye.testharness

import java.net.InetSocketAddress
import com.josiahebhomenye.testharness
import com.typesafe.config.Config
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import com.josiahebhomenye.testharness.NettyToScalaHelpers._
import scala.concurrent.{ExecutionContext, Future}

object Servers{

  def apply(config: Config)(implicit ec: ExecutionContext): Seq[Server] = {
    val size = config.getInt("harness.servers.count")
    var servers = Seq[Server]()
    for(i <- 0 until size){
      val serverConfig = config.getConfig(s"harness.servers.server$i")
      val (name, port, action) = props(serverConfig, i)
      servers = servers ++ Seq(new Server(port, name, testharness.Actions(action, serverConfig)))
    }
    servers
  }

  def props(config: Config, i: Int) =
    (config.getString("name"),
      config.getInt("port"),
      config.getString("action"))

}

class Server(val port: Int, val name: String, action: => Action)(implicit ec: ExecutionContext){

  var mayBeGroup: Option[EventLoopGroup] = None

  def run: Future[Channel] = {
    val group: EventLoopGroup = NioEventLoopGroup()
    val future: Future[Channel] =
      new ServerBootstrap()
        .group(group)
        .channel(classOf[NioServerSocketChannel])
        .childHandler { ch: Channel =>
          ch.pipeline()
            .addLast(new HttpServerCodec)
            .addLast(new HttpObjectAggregator(65536))
            .addLast(new HttpRequestHandler(action))
        }.bind(new InetSocketAddress(port))

    future onSuccess  {
      case _ => mayBeGroup = Some(group)
    }

    future
  }

  def isRunning = mayBeGroup.isDefined

  def stop() = mayBeGroup.foreach(group => group.shutdownGracefully().sync())
}
