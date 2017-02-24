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

import java.io.PrintStream
import java.util.concurrent.LinkedBlockingQueue

import akka.actor.{Actor, PoisonPill, Props}
import com.sumologic.shellbase.{ShellBase, ShellCommand, ShellCommandSet}
import com.sumologic.shellbot.model.{Command, Completed}
import com.sumologic.sumobot.core.model.IncomingMessage

/**
  * Executes shell commands, overrides Console.out and err during the execution.
  */
object RunCommandActor {
  val Name = "runCommand"
  def props(name: String, commands: Seq[ShellCommand]): Props = {
    Props(classOf[RunCommandActor], name, commands)
  }
}
class RunCommandActor(name: String, commands: Seq[ShellCommand]) extends Actor {

  private val inputQueue = new LinkedBlockingQueue[String]()
  private val shellBotIO = new ShellBotShellIO(inputQueue, context.system.eventStream)
  private val commandSet = new ShellCommandSet(name, "")
  commandSet.commands ++= commands
  commandSet.configureIO(shellBotIO)

  override def receive: Receive = {
    case Command(message, command) =>
      inputQueue.clear()
      shellBotIO.setActiveMessage(message)
      val threadReader = context.actorOf(Props(classOf[ThreadReader], message.sentByUser, message.ts, inputQueue), s"threadReader-${message.thread_ts.get}")
      context.system.eventStream.subscribe(threadReader, classOf[IncomingMessage])
      val printStream = new PrintStream(new ThreadPrinter(message, context.system.eventStream), true)
      val successful = Console.withOut(printStream) { Console.withErr(printStream) {
        commandSet.executeLine(ShellBase.parseLine(command))
      }}
      printStream.close()
      context.child(s"threadReader-${message.thread_ts.get}").get ! PoisonPill
      sender() ! Completed(message, command, successful)
  }
}

