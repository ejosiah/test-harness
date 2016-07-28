package com.josiahebhomenye.testharness.client

import java.io.OutputStream
import java.net.{HttpURLConnection, URL}

import com.josiahebhomenye.testharness.{Converters, Headers, ContentType, EmptyHeaders}
import scala.concurrent.{Await, Future, ExecutionContext}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class Result(headers: Headers = Map(), status: Int, response: Array[Byte])

class HttpClient(url: String, method: String, headers: Headers, content: Array[Byte] = new Array(0)){

  var mayBeOutput = Option.empty[OutputStream]

  val connection = Try(new URL(url).openConnection().asInstanceOf[HttpURLConnection]) match {
    case Failure(NonFatal(e)) => throw e
    case Success(con) =>  con
  }

  Try{
    connection setRequestMethod method
    headers foreach (entry => connection.setRequestProperty(entry._1, entry._2))
    if(method == "POST" || method == "PUT"){
      connection setFixedLengthStreamingMode content.length
      connection setDoOutput true
    }
    connection setDoInput true
    connection.connect()
    if(method == "POST" || method == "PUT") {
      mayBeOutput = Some(connection.getOutputStream)
    }
  } match {
    case Failure(e) => throw e
    case _ =>
  }


  def apply()(implicit ec: ExecutionContext): Future[Result] = {
    import Converters._
    val f = Future{
      mayBeOutput.foreach( out => out.write(content) )
      val status = connection.getResponseCode
      val in = if(status > 399) connection.getErrorStream else connection.getInputStream
      var headers = EmptyHeaders()
      connection.getHeaderFields.entrySet().filter(_._1 != null).foreach{ e =>headers = headers ++ Map(e._1 -> e._2.get(0))}
      val resp = Iterator.continually(in.read).takeWhile(_ != -1).map(_.asInstanceOf[Byte]).toArray
      Result(headers, status, resp)

    }
    f.onComplete{
      case _ => connection.disconnect()
    }
    f
  }

}

object HttpClient{

  def get(url: String, headers: Headers = Map())(implicit ec: ExecutionContext) =
    (new HttpClient(url, "GET", headers = headers))()

  def post(url: String, content: Array[Byte], contentType: Option[ContentType] = None, headers: Headers = Map())(implicit ec: ExecutionContext) =
  (new HttpClient(url, "POST", headers, content))()

  def put(url: String, content: Array[Byte], contentType: Option[ContentType] = None, headers: Headers = Map())(implicit ec: ExecutionContext) =
  (new HttpClient(url, "PUT", headers, content))()

  def exchange(url: String, method: String, content: Array[Byte], contentType: Option[ContentType] = None, headers: Headers = Map())(implicit ec: ExecutionContext) =
  (new HttpClient(url, method, headers, content))()



  def main(args: Array[String]): Unit ={
    import scala.concurrent.duration._
    import scala.language.postfixOps
    implicit val ec = ExecutionContext.global
   // val f: Future[Result] = HttpClient.get("http://localhost:10000/?postcode=ZZ01%201ZZ")
    val headers = Map("X-Hmrc-Origin" -> "pac")
    val f: Future[Result] = HttpClient.get("http://localhost:10004/uk/addresses?postcode=ZZ01%201ZZ", headers)


    val result: Result = Await.result(f, 1 minute)
    println(result.headers)
    println(new String(result.response))
  }
}