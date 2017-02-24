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
package com.sumologic.shellbase.actor

import java.io.PrintStream
import java.util.concurrent.LinkedBlockingQueue

import akka.actor.{Actor, Props}
import com.sumologic.shellbase.actor.model.{Command, Commands, Completed, Done, Input}
import com.sumologic.shellbase.{ShellBase, ShellCommand, ShellCommandSet}

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
  private val shellBotIO = new ActorShellIO(inputQueue, context.system.eventStream)
  private val commandSet = new ShellCommandSet(name, "")
  private val inputReader = context.actorOf(InputReaderActor.props(inputQueue))

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    commandSet.commands ++= commands
    commandSet.configureIO(shellBotIO)

    context.system.eventStream.subscribe(inputReader, classOf[Input])
  }

  override def receive: Receive = {
    case Command(message, command) =>
      inputQueue.clear()
      shellBotIO.setActiveMessage(message)
      val printStream = new PrintStream(new ThreadPrinter(message, context.system.eventStream), true)
      val successful = Console.withOut(printStream) { Console.withErr(printStream) {
        commandSet.executeLine(ShellBase.parseLine(command))
      }}
      printStream.close()
      sender() ! Completed(message, command, successful)
      sender() ! Done(message)
    case Commands(message, commandsToRun) =>
      inputQueue.clear()
      shellBotIO.setActiveMessage(message)
      val printStream = new PrintStream(new ThreadPrinter(message, context.system.eventStream), true)
      commandsToRun.foreach { command =>
        val successful = Console.withOut(printStream) { Console.withErr(printStream) {
          commandSet.executeLine(ShellBase.parseLine(command))
        }}
        sender() ! Completed(message, command, successful)
      }
      sender() ! Done(message)
      printStream.close()
  }
}

