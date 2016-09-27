package com.josiahebhomenye

import java.util.Map.Entry

import com.typesafe.config.ConfigValue
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.channel.nio.{NioEventLoopGroup => NettyNioEventLoopGroup}
import io.netty.handler.codec.http.{HttpHeaders, HttpUtil, HttpRequest}
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, ExecutionContext, CanAwait, TimeoutException}
import scala.util.{Failure, Success, Try}
import scala.language.implicitConversions
import java.util.function.Consumer

/**
  * Created by jay on 20/07/2016.
  */
package object testharness {

  type ContentType = String
  type Headers = Map[String, String]
  val EmptyHeaders = () => Map.empty[String, String]

  private class CFFuture(cf: ChannelFuture) extends Future[Channel]{

    var mayBeValue: Option[Try[Channel]] = None

    cf.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture): Unit =
        if(future.isSuccess)
          mayBeValue = Some(Success(future.channel()))
        else
          mayBeValue = Some(Failure(future.cause()))

    })

    override def onComplete[U](func: Try[Channel] => U)(implicit executor: ExecutionContext): Unit =
      cf.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture): Unit = {
          if(future.isSuccess){
            func(Success(future.channel()))
          }else{
            func(Failure(future.cause()))
          }
        }
    })

    override def isCompleted: Boolean = cf.isDone

    override def value: Option[Try[Channel]] = mayBeValue


    @throws[InterruptedException](classOf[InterruptedException])
    @throws[TimeoutException](classOf[TimeoutException])
    override def ready(atMost: Duration)(implicit permit: CanAwait): CFFuture.this.type = {
      if(!cf.await(atMost.toMillis)) throw new TimeoutException
      this
    }

    @throws[Exception](classOf[Exception])
    override def result(atMost: Duration)(implicit permit: CanAwait): Channel =
      if (!cf.await(atMost.toMillis))
        throw new TimeoutException
      else cf.channel()
  }

  object Converters {
    type Entry[A, B] = java.util.Map.Entry[A, B]
    type JSet[E] = java.util.Set[E]
    type JCollection[E] = java.util.Collection[E]

    implicit def convert[T](consume: T => Unit) : Consumer[T] = new Consumer[T](){
      def accept(t: T): Unit = consume(t)
    }

    implicit def collectionOfEntryToSeqOfTuple[A, B](config: JCollection[Entry[A, B]]): Seq[(A, B)] = {
      var result: Seq[(A, B)] = Seq()
      config.forEach{e: Entry[A, B]  => result = result ++ Seq((e.getKey, e.getValue))}
      result
    }
    implicit def convert(func: Unit  => Unit): Runnable = new Runnable(){ def run(): Unit = func() }

    implicit def convert(runnable: Runnable): Thread = new Thread(runnable)

  }
  object NettyToScalaHelpers{

    implicit def convert(cf: ChannelFuture) : Future[Channel] = new CFFuture(cf)

    implicit def convert[C <: Channel](f: C => ChannelPipeline) : ChannelInitializer[C] = new ChannelInitializer[C] {
      override def initChannel(ch: C): Unit = f(ch)
    }

    implicit def convert(headers: HttpHeaders) : Map[String, String] = {
      var result = Map.empty[String, String]
      Converters.collectionOfEntryToSeqOfTuple(headers.entries()).foreach{ e => result = result ++ Map(e._1 -> e._2) }
      result
    }

    implicit def convert(byteBuf: ByteBuf): Array[Byte] = {
      val data = new Array[Byte](byteBuf.readableBytes())
      byteBuf.readBytes(data)
      data
    }

  }

  implicit class RichHttpRequest(underlying: HttpRequest){

    def if100ContinueExpected(body: => Unit): Unit =
      if(HttpUtil.is100ContinueExpected(underlying)) body

    def ifKeepAlive(body: => Unit): Unit =
      if(HttpUtil.isKeepAlive(underlying)) body
  }

  object NioEventLoopGroup {

    def apply(): NettyNioEventLoopGroup = new NettyNioEventLoopGroup()
  }

  val randomEssay =
    """
      |A major change that has occurred in the Western family is an increased incidence in divorce. Whereas in the past, divorce was a relatively rare occurrence, in recent times it has become quite commonplace. This change is borne out clearly in census figures. For example thirty years ago in Australia, only one marriage in ten ended in divorce; nowadays the figure is more than one in three (Australian Bureau of Statistics, 1996: p.45). A consequence of this change has been a substantial increase in the number of single parent families and the attendant problems that this brings (Kilmartin, 1997).
      |
      |An important issue for sociologists, and indeed for all of society, is why these changes in marital patterns have occurred. In this essay I will seek to critically examine a number of sociological explanations for the 'divorce phenomenon' and also consider the social policy implications that each explanation carries with it. It will be argued that the best explanations are to be found within a broad socio-economic framework.
      |
      |One type of explanation for rising divorce has focused on changes in laws relating to marriage. For example, Bilton, Bonnett and Jones (1987) argue that increased rates of divorce do not necessarily indicate that families are now more unstable. It is possible, they claim, that there has always been a degree of marital instability. They suggest that changes in the law have been significant, because they have provided unhappily married couples with 'access to a legal solution to pre-existent marital problems' (p.301). Bilton et al. therefore believe that changes in divorce rates can be best explained in terms of changes in the legal system. The problem with this type of explanation however, is that it does not consider why these laws have changed in the first place. It could be argued that reforms to family law, as well as the increased rate of divorce that has accompanied them, are the product of more fundamental changes in society.
      |
      |Another type of explanation is one that focuses precisely on these broad societal changes. For example, Nicky Hart (cited in Haralambos, 1995) argues that increases in divorce and marital breakdown are the result of economic changes that have affected the family. One example of these changes is the raised material aspirations of families, which Hart suggests has put pressure on both spouses to become wage earners. Women as a result have been forced to become both homemakers and economic providers. According to Hart, the contradiction of these two roles has lead to conflict and this is the main cause of marital breakdown. It would appear that Hart's explanation cannot account for all cases of divorce - for example, marital breakdown is liable to occur in families where only the husband is working. Nevertheless, her approach, which is to relate changes in family relations to broader social forces, would seem to be more probing than one that looks only at legislative change.
      |
      |The two explanations described above have very different implications for social policy, especially in relation to how the problem of increasing marital instability might be dealt with. Bilton et al. (1995) offer a legal explanation and hence would see the solutions also being determined in this domain. If rises in divorce are thought to be the consequence of liberal divorce laws, the obvious way to stem this rise is to make them less obtainable. This approach, one imagines, would lead to a reduction in divorce statistics; however, it cannot really be held up as a genuine solution to the problems of marital stress and breakdown in society. Indeed it would seem to be a solution directed more at symptoms than addressing fundamental causes. Furthermore, the experience of social workers, working in the area of family welfare suggests that restricting a couple's access to divorce would in some cases serve only to exacerbate existing marital problems (Johnson, 1981). In those cases where violence is involved, the consequences could be tragic. Apart from all this, returning to more restrictive divorce laws seems to be a solution little favoured by Australians. (Harrison, 1990).
      |
      |Hart (cited in Haralambos, 1995), writing from a Marxist-feminist position, traces marital conflict to changes in the capitalist economic system and their resultant effect on the roles of men and women. It is difficult to know however, how such an analysis might be translated into practical social policies. This is because the Hart program would appear to require in the first place a radical restructuring of the economic system. Whilst this may be desirable for some, it is not achievable in the present political climate. Hart is right however, to suggest that much marital conflict can be linked in some way to the economic circumstances of families. This is borne out in many statistical surveys which show consistently that rates of divorce are higher among socially disadvantaged families (McDonald, 1993). This situation suggests then that social policies need to be geared to providing support and security for these types of families. It is little cause for optimism however, that in recent years governments of all persuasions have shown an increasing reluctance to fund social welfare programs of this kind.
      |
      |It is difficult to offer a comprehensive explanation for the growing trend of marital breakdown; and it is even more difficult to find solutions that might ameliorate the problems created by it. Clearly though, as I have argued in this essay, the most useful answers are to be found not within a narrow legal framework, but within a broader socio-economic one.
      |
      |Finally, it is worth pointing out that, whilst we may appear to be living in a time of increased family instability, research suggests that historically, instability may have been the norm rather than the exception. As Bell and Zajdow (1997) point out, in the past, single parent and step families were more common than is assumed - although the disruptive influence then was not divorce, but the premature death of one or both parents. This situation suggests that in studying the modern family, one needs to employ a historical perspective, including the possibility of looking to the past in searching for ways of dealing with problems in the present.
    """.stripMargin

  val addressLookup = new ResponseMap(
    path = "/?postcode=ZZ01 1ZZ",
    method = "GET",
    contentType = Some("application/json"),
    response =
      """
        |[
        |    {
        |        "id": "GB990091234501",
        |        "address": {
        |            "lines": [
        |                "1 Test Street"
        |            ],
        |            "town": "Testtown",
        |            "postcode": "ZZ01 1ZZ",
        |            "country": {
        |                "code": "UK",
        |                "name": "United Kingdom"
        |            }
        |        },
        |        "language": "en"
        |    },
        |    {
        |        "id": "GB990091234502",
        |        "address": {
        |            "lines": [
        |                "2 Test Street"
        |            ],
        |            "town": "Testtown",
        |            "postcode": "ZZ01 1ZZ",
        |            "country": {
        |                "code": "UK",
        |                "name": "United Kingdom"
        |            }
        |        },
        |        "language": "en"
        |    },
        |    {
        |        "id": "GB990091234503",
        |        "address": {
        |            "lines": [
        |                "3 Test Street"
        |            ],
        |            "town": "Testtown",
        |            "postcode": "ZZ01 1ZZ",
        |            "country": {
        |                "code": "UK",
        |                "name": "United Kingdom"
        |            }
        |        },
        |        "language": "en"
        |    }
        |]
      """.stripMargin

  )
}
