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
import com.sumologic.sumobot.brain.Brain.{Remove, Retrieve, ValueMissing, ValueRetrieved}
import com.sumologic.sumobot.core.model.{GroupChannel, IncomingMessage, InstantMessageChannel, PublicChannel}
import com.sumologic.sumobot.plugins.BotPlugin
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import slack.models.User

import scala.collection.mutable.ListBuffer

import scala.collection.JavaConverters._

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
  private val authorizedUsers = ListBuffer.empty[User]

  override protected def help =
    s"""Execute commands:
      |
      |execute - Run a single command on $name, format: "execute `command` in `$name`"
      |multi - Run multiple commands on $name, format: "execute ```commands``` in `$name`"
      |authorize - Authorize user to run commands, get the code by running `shellBot getCode` on the shell.
    """.stripMargin

  private val SingleExecute = matchText("execute `(.*)` in `(.*)`")
  private val MultiExecute = matchText(s"multi ```(.*)``` in `(.*)`")
  private val Authorize = matchText(s"authorize me in `(.*)` with code (.*)")

  private var runCommandActor: ActorRef = _

  override protected def pluginPreStart(): Unit = {
    context.actorSelection(s"../${RunCommandActor.Name}") ! Identify("runCommand")
    context.system.eventStream.subscribe(self, classOf[Output])
  }

  override protected def initialize(): Unit = {
    val whiteListedUsers = config.getStringList("users").asScala
    authorizedUsers.appendAll(state.users.filter(u => whiteListedUsers.contains(u.name)))
  }

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(MultiExecute(commands, desiredShell), true, _, user, parentId, None) =>
      onlyIfAuthorized(message, desiredShell) {
        message.respond(s"Executing: ```$commands``` in `$name`", Some(parentId))
        val commandSeq = commands.split("\n")
        val messageInThread = message.copy(thread_ts = Some(parentId))
        val threadReader = context.actorOf(Props(classOf[ThreadReader], user, parentId), s"threadReader-$parentId")
        context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
        runCommandActor ! Commands(messageInThread, commandSeq.toSeq)
      }
    case message@IncomingMessage(SingleExecute(command, desiredShell), true, _, user, parentId, None) =>
      onlyIfAuthorized(message, desiredShell) {
        message.respond(s"Executing: `$command` in `$name`", Some(parentId))
        val messageInThread = message.copy(thread_ts = Some(parentId))
        runCommandActor ! Command(messageInThread, command)
        val threadReader = context.actorOf(Props(classOf[ThreadReader], user, parentId), s"threadReader-$parentId")
        context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
      }
    case message@IncomingMessage(Authorize(_, _), _, PublicChannel(_,_), user, _, _) =>
      brain ! Remove(s"accessCode.${user.name}")
      message.respond(s"authorize is not allowed in public channels, access code is now invalid")
    case message@IncomingMessage(Authorize(_, _), _, GroupChannel(_,_), user, _, _) =>
      brain ! Remove(s"accessCode.${user.name}")
      message.respond(s"authorize is not allowed in public channels, access code is now invalid")
    case message@IncomingMessage(Authorize(desiredShell, code), _, InstantMessageChannel(_,_), user, _, _) =>
      if (desiredShell == name) {
        implicit val timeout = Timeout(2.seconds)
        Await.result(brain ? Retrieve(s"accessCode.${user.name}"), 2.seconds) match {
          case ValueRetrieved(_, value) =>
            if (value == code) {
              authorizedUsers.append(user)
              brain ! Remove(s"accessCode.${user.name}")
              message.respond(s"authorized, you can run commands on `$name`")
            } else {
              message.respond("access code is invalid")
            }
          case ValueMissing(_) =>
            message.respond(s"no access code for you, you can add one by executing `shellBot getCode`")
        }
      } else {
        message.respond("wrong shell dude")
      }
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

  private def onlyIfAuthorized(message: IncomingMessage, desiredShell: String)(f: => Unit) = {
    if (authorizedUsers.contains(message.sentByUser) && desiredShell == name) {
      f
    }
  }

  private def urlForThread(message: IncomingMessage): String = {
    val id = message.channel match {
      case x: InstantMessageChannel => x.id
      case x => x.name
    }
    s"https://${state.team.domain}.slack.com/conversation/$id/p${message.thread_ts.get.replace(".","")}"
  }

}
