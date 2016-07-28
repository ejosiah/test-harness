package com.josiahebhomenye.testharness

import java.io.{PrintWriter, StringWriter}

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelPromise, ChannelOutboundHandlerAdapter, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.CharsetUtil


class RequestLogger extends ChannelInboundHandlerAdapter {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val writer = new PrintWriter(new StringWriter())
    cause.printStackTrace(writer)
    println(s"unable to fulfil request for ${ctx.channel().remoteAddress()} \n${writer.toString}")
    ctx.channel().close()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"received connection from ${ctx.channel().remoteAddress()}")
    super.channelActive(ctx)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
   val request = Unpooled.copiedBuffer(msg.asInstanceOf[ByteBuf]).toString(CharsetUtil.UTF_8).replaceAll("\n", "\n\t")
    println(s"processing request from client ${ctx.channel().remoteAddress()}:\n\t$request\n")
    super.channelRead(ctx, msg)
  }

}

class ResponseLogger extends ChannelOutboundHandlerAdapter{
  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = {
    val reply = Unpooled.copiedBuffer(msg.asInstanceOf[ByteBuf]).toString(CharsetUtil.UTF_8)
    if(!reply.isEmpty) {
      println(s"sending reply to client: ${ctx.channel().remoteAddress()}\n$reply\n")
    }
    super.write(ctx, msg, promise)
  }
}