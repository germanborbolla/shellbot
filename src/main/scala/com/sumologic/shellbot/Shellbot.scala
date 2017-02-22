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

import akka.actor.{Actor, Props}
import com.sumologic.shellbase.ShellBase
import com.sumologic.sumobot.core.model.{IncomingMessage, InstantMessageChannel}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.io.Source

/**
  * Created by panda on 2/20/17.
  */
object Shellbot {
  def props(shellBase: ShellBase): Props = {
    Props(classOf[Shellbot], shellBase)
  }
}
class Shellbot(shellBase: ShellBase) extends BotPlugin {
  override protected def help =
    s"""Execute commands:
      |
      |execute - Run a single command on ${shellBase.name}
    """.stripMargin

  private val SingleExecute = matchText(s"execute (.*)")

  private val runCommandActor = context.actorOf(Props(classOf[RunCommandActor], shellBase), "runCommand")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(SingleExecute(command), true, _, _, parentId, None) =>
      message.respond(s"Executing: `$command` in `${shellBase.name}`", Some(parentId))
      val messageInThread = message.copy(thread_ts = Some(parentId))
      runCommandActor ! Command(messageInThread, command, new Printer(messageInThread))
  }

  override protected def pluginReceive: Receive = {
    case Completed(message, command, successful) =>
      if (successful) {
        message.say("Command succeeded", message.thread_ts)
        message.respond(s"Command `$command` in `${shellBase.name}` finished successfully, full output available on the thread ${urlForThread(message)}.")
      } else {
        message.say("Command failed", message.thread_ts)
        message.respond(s"Command `$command` in `${shellBase.name}` failed, full output available on the thread ${urlForThread(message)}.")
      }
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
class RunCommandActor(shellBase: ShellBase) extends Actor {
  override def receive: Receive = {
    case Command(message, command, output) =>
      val printStream = new PrintStream(output, true)
      val successful = Console.withOut(printStream) { Console.withErr(printStream) {
        shellBase.runCommand(command)
      }}
      printStream.close()
      sender() ! Completed(message, command, successful)
  }
}
