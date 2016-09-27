package com.josiahebhomenye.testharness

import java.io.FileInputStream

import com.josiahebhomenye.testharness.NettyToScalaHelpers._
import com.josiahebhomenye.testharness.client.HttpClient
import com.typesafe.config.Config
import io.netty.buffer.{ByteBuf, ByteBufHolder, Unpooled}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpRequest}
import io.netty.util.CharsetUtil

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.Random

/**
  * Created by jay on 22/07/2016.
  */
object DataSources {

  type HttpDataSource = DataSource[HttpRequest]

}

import com.josiahebhomenye.testharness.DataSources._

trait DataSource[T] extends ((T) => Option[(Option[ContentType], Headers, ByteBuf)])


class RequestBasedDataSource(responses: Map[(String, String), ResponseMap]) extends HttpDataSource{

  def apply(req: HttpRequest): Option[(Option[ContentType], Headers, ByteBuf)] = {
    val key = (req.uri(), req.method().toString)
    responses.get(key).map{ resp =>
      (resp.contentType, resp.responseHeaders, Unpooled.copiedBuffer(resp.response, CharsetUtil.UTF_8))
    }

  }
}

class RandomDataSource(size: Int) extends HttpDataSource {

  override def apply(req: HttpRequest): Option[(Option[ContentType], Headers, ByteBuf)] = {
    val os = System.getProperty("os.name").toLowerCase()
    val data =
      if (os.contains("mac") || os.contains("linux"))
        hardwareRandomBytes()
      else
        softwareRandomBytes()

    Some(Option.empty[ContentType], EmptyHeaders(), data)
  }


  def hardwareRandomBytes() = {
    val rngStream = new FileInputStream("/dev/random")
    val buf = Unpooled.buffer()
    buf.writeBytes(rngStream, size)
    buf
  }

  def softwareRandomBytes() = {
    val randBytes = new Array[Byte](size)
    Random.nextBytes(randBytes)
    Unpooled.copiedBuffer(randBytes)
  }
}

object EmptyDataSource{
  def apply(config: Config) = {
    import Converters._
    val ct = config.getString("contentType")
    val hConf = config.getConfig("headers")
    var headers = EmptyHeaders()
    hConf.entrySet().foreach{ e =>
      headers = headers ++ Map(e._1 -> e._2.unwrapped().toString)
    }
    new EmptyDataSource(Some(ct), headers)
  }
}

class EmptyDataSource(contentType: Option[ContentType] = None, headers: Headers = EmptyHeaders()) extends HttpDataSource{
  def apply(req: HttpRequest): Option[(Option[ContentType], Headers, ByteBuf)] =
    Some((contentType, headers, Unpooled.EMPTY_BUFFER))
}

class ProxyDataSource(protocol: String, host: String, port: Int, context: String = "") extends HttpDataSource{
  implicit val ec = ExecutionContext.global


  override def apply(req: HttpRequest): Option[(Option[ContentType], Headers, ByteBuf)] = {
    val ctx = if(context == "/") "" else context
    val url = s"$protocol://$host:$port$ctx${req.uri()}"
    val result = req.method().toString match {
      case "GET" =>
        HttpClient.get(url, req.headers()).map{ result =>
          Some(result.headers.get("content-type"), result.headers, Unpooled.copiedBuffer(result.response))
        }
      case method if method == "POST" || method == "PUT" =>
        val content = req.asInstanceOf[ByteBufHolder].content()
        HttpClient.exchange(url, method, content, Some(req.headers().get(HttpHeaderNames.CONTENT_TYPE)), req.headers()).map{ result =>
          Some(result.headers.get("content-type"), result.headers, Unpooled.copiedBuffer(result.response))
        }
    }
    Await.result(result, 1 minute)
  }
}