/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.shellbot

import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import com.sumologic.shellbase.{ShellBase, ShellCommand, ShellCommandSet}
import com.sumologic.sumobot.core.model.{IncomingMessage, InstantMessageChannel}
import com.sumologic.sumobot.plugins.BotPlugin
import slack.models.User

import scala.io.Source

/**
  * Created by panda on 2/20/17.
  */
object ShellBot {
  def props(name: String, commands: Seq[ShellCommand]): Props = {
    Props(classOf[ShellBot], name, commands)
  }

  def runCommand(commandSet: ShellCommandSet): Props = {
    Props(classOf[RunCommandActor], commandSet)
  }

  def threadReader(user: User, thread: String, ouputStream: OutputStream): Props = {
    Props(classOf[ThreadReader], user, thread, ouputStream)
  }
}
class ShellBot(name: String, commands: Seq[ShellCommand]) extends BotPlugin {
  override protected def help =
    s"""Execute commands:
      |
      |execute - Run a single command on $name
    """.stripMargin

  private val SingleExecute = matchText(s"execute (.*)")

  private val commandSet = new ShellCommandSet(name, "")
  commandSet.commands ++= commands
  private val runCommandActor = context.actorOf(ShellBot.runCommand(commandSet), "runCommand")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(SingleExecute(command), true, _, sentByUser, parentId, None) =>
      message.respond(s"Executing: `$command` in `$name`", Some(parentId))
      val messageInThread = message.copy(thread_ts = Some(parentId))
      runCommandActor ! Command(messageInThread, command, new Printer(messageInThread))
      val threadReader = context.actorOf(ShellBot.threadReader(sentByUser, parentId, new ByteArrayOutputStream()), s"threadReader-$parentId")
      context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
  }

  override protected def pluginReceive: Receive = {
    case Completed(message, command, successful) =>
      if (successful) {
        message.say("Command succeeded", message.thread_ts)
        message.respond(s"Command `$command` in `$name` finished successfully, full output available on the thread ${urlForThread(message)}.")
      } else {
        message.say("Command failed", message.thread_ts)
        message.respond(s"Command `$command` in `$name` failed, full output available on the thread ${urlForThread(message)}.")
      }
      context.child(s"threadReader-${message.thread_ts.get}").get ! PoisonPill
    case Output(message, bytes) =>
      Source.fromBytes(bytes).getLines().foreach { line =>
        if (line.nonEmpty) {
          message.say(line, message.thread_ts)
        }
      }
  }

  private def urlForThread(message: IncomingMessage): String = {
    val id = message.channel match {
      case x: InstantMessageChannel => x.id
      case x => x.name
    }
    s"https://${state.team.domain}.slack.com/conversation/$id/p${message.thread_ts.get.replace(".","")}"
  }

  class Printer(message: IncomingMessage) extends OutputStream {
    private val bos = new ByteArrayOutputStream()
    override def write(b: Int): Unit = {
      bos.write(b)
    }

    override def flush(): Unit = {
      self ! Output(message, bos.toByteArray)
      bos.reset()
    }
  }
}
case class Output(message: IncomingMessage, bytes: Array[Byte])
case class Command(message: IncomingMessage, command: String, output: OutputStream)
case class Completed(message: IncomingMessage, command: String, successful: Boolean)
class ThreadReader(user: User, watchingThread: String, outputStream: OutputStream) extends Actor with ActorLogging {
  override def receive: Receive = {
    case IncomingMessage(text, _, _, sentByUser, _, Some(thread)) if thread == watchingThread && sentByUser == user =>
      outputStream.write(text.getBytes("UTF-8"))
      outputStream.flush()
  }
}
class RunCommandActor(commandSet: ShellCommandSet) extends Actor {
  override def receive: Receive = {
    case Command(message, command, output) =>
      val printStream = new PrintStream(output, true)
      val successful = Console.withOut(printStream) { Console.withErr(printStream) {
        commandSet.executeLine(ShellBase.parseLine(command))
      }}
      printStream.close()
      sender() ! Completed(message, command, successful)
  }
}
