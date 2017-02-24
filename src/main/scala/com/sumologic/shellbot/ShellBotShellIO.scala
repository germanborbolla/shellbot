package com.sumologic.shellbot

import java.util.concurrent.BlockingQueue

import akka.actor.ActorRef
import com.sumologic.shellbase.ShellIO
import com.sumologic.sumobot.core.model.IncomingMessage

/**
  * ShellIO for the ShellBot.
  */
class ShellBotShellIO(inputQueue: BlockingQueue[String], bot: ActorRef) extends ShellIO {

  private var activeMessage: IncomingMessage = _

  def setActiveMessage(activeMessage: IncomingMessage): Unit = {
    this.activeMessage = activeMessage
  }

  override def print(x: Any): Unit = {
    println(x)
  }

  override def println(): Unit = {
  }

  override def println(x: Any): Unit = {
    val text = x match {
      case s: String => s
      case _ => x.toString
    }
    bot ! OutputLine(activeMessage, text)
  }

  override def printf(text: String, xs: Any*): Unit = {
    println(text.replace("%n", "").format(xs: _*))
  }

  override def readCharacter(): Int = {
    inputQueue.take().toCharArray.head
  }

  override def readCharacter(validChars: Seq[Char]): Int = {
    var read = readCharacter()
    while (!validChars.contains(read)) {
      read = readCharacter()
    }
    read
  }

  override def readLine(): String = {
    inputQueue.take()
  }

  override def readLine(maskCharacter: Character): String = {
    readLine()
  }

  override def readLine(prompt: String): String = {
    println(prompt)
    readLine()
  }

  override def readLine(prompt: String, maskCharacter: Character): String = {
    readLine(prompt)
  }
}
