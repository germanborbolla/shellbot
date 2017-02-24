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

import akka.actor.{ActorIdentity, ActorRef, Identify, PoisonPill, Props}
import com.sumologic.shellbase.actor.RunCommandActor
import com.sumologic.shellbase.actor.model.{Command, Commands, Completed, Done, Output}
import com.sumologic.sumobot.core.model.{IncomingMessage, InstantMessageChannel}
import com.sumologic.sumobot.plugins.BotPlugin

import scala.io.Source

/**
  * Bot that listens to slack and executes commands.
  */
object ShellBotPlugin {
  def props(): Props = {
    Props(classOf[ShellBotPlugin])
  }
}
class ShellBotPlugin extends BotPlugin {
  private val name = config.getString("name")

  override protected def help =
    s"""Execute commands:
      |
      |execute - Run a single command on $name
    """.stripMargin

  private val SingleExecute = matchText(s"execute (.*)")
  private val MultiExecute = matchText(s"execute ```(.*)```")

  private var runCommandActor: ActorRef = _

  override protected def pluginPreStart(): Unit = {
    context.actorSelection(s"../${RunCommandActor.Name}") ! Identify("runCommand")
    context.system.eventStream.subscribe(self, classOf[Output])
  }

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(MultiExecute(commands), true, _, _, parentId, None) =>
      message.respond(s"Executing: ```$commands``` in `$name`", Some(parentId))
      val commandSeq = commands.split("\n")
      val messageInThread = message.copy(thread_ts = Some(parentId))
      val threadReader = context.actorOf(Props(classOf[ThreadReader], message.sentByUser, message.ts), s"threadReader-${message.ts}")
      context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
      runCommandActor ! Commands(messageInThread, commandSeq.toSeq)
    case message@IncomingMessage(SingleExecute(command), true, _, _, parentId, None) =>
      message.respond(s"Executing: `$command` in `$name`", Some(parentId))
      val messageInThread = message.copy(thread_ts = Some(parentId))
      runCommandActor ! Command(messageInThread, command)
      val threadReader = context.actorOf(Props(classOf[ThreadReader], message.sentByUser, message.ts), s"threadReader-${message.ts}")
      context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
  }

  override protected def pluginReceive: Receive = {
    case ActorIdentity(id, runCommandOpt) if id == "runCommand" =>
      runCommandOpt.foreach(x => runCommandActor = x)
    case Completed(message, command, successful) =>
      if (successful) {
        message.say("Command succeeded", message.thread_ts)
        message.respond(s"Command `$command` in `$name` finished successfully, full output available on the thread ${urlForThread(message)}.")
      } else {
        message.say("Command failed", message.thread_ts)
        message.respond(s"Command `$command` in `$name` failed, full output available on the thread ${urlForThread(message)}.")
      }
    case Done(message) =>
      context.child(s"threadReader-${message.thread_ts.get}").foreach(_ ! PoisonPill)
    case Output(message, line) =>
      message.say(line, message.thread_ts)
  }

  private def urlForThread(message: IncomingMessage): String = {
    val id = message.channel match {
      case x: InstantMessageChannel => x.id
      case x => x.name
    }
    s"https://${state.team.domain}.slack.com/conversation/$id/p${message.thread_ts.get.replace(".","")}"
  }

}
