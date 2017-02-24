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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.sumologic.shellbase.ShellCommand
import com.sumologic.shellbase.actor.model.{Command, Commands, Completed, Done, Input, Output}
import com.sumologic.shellbase.commands.EchoCommand
import com.sumologic.sumobot.test.BotPluginTestKit
import com.typesafe.config.ConfigFactory
import org.apache.commons.cli.CommandLine
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar

class RunCommandActorTest extends BotPluginTestKit(ActorSystem("Shellbot", ConfigFactory.parseResourcesAnySyntax("application.conf").resolve())) with BeforeAndAfterAll with MockitoSugar with ImplicitSender {

  private val testCommands = Seq(
    new ShellCommand("multi", "multi") {
      override def execute(cmdLine: CommandLine) = {
        println("""hello
                  |world!""".stripMargin
        )
        true
      }
    },
    new ShellCommand("error", "error") {
      override def execute(cmdLine: CommandLine) = {
        Console.err.println("this is an error")
        false
      }
    },
    new ShellCommand("ask", "ask") {
      override def execute(cmdLine: CommandLine) = {
        val name = prompter.askQuestion("who are you?")
        println(s"Hello $name, now die!")
        true
      }
    },
    new EchoCommand)

  private val sut = system.actorOf(RunCommandActor.props("test", testCommands), "runCommand")
  system.eventStream.subscribe(testActor, classOf[Output])

  private val threadId = "1487799797539.0000"
  private val message = instantMessage("something", threadId = Some(threadId))

  "RunCommandActor" when {
    "executing single commands" should {
      "execute the command and send the output" in {
        sut ! command("echo hello world!")
        expectMsg(output("hello world!"))
        expectMsg(completed("echo hello world!", true))
        expectMsg(done())
      }
      "send all the lines in the output" in {
        sut ! command("multi")
        expectMsg(output("hello"))
        expectMsg(output("world!"))
        expectMsg(completed("multi", true))
        expectMsg(done())
      }
      "send the output when a command doesn't exist" in {
        sut ! command("badcommand")
        expectMsg(output("test: command badcommand not found"))
        expectMsg(completed("badcommand", false))
        expectMsg(done())
      }
      "send stuff in err as well" in {
        sut ! command("error")
        expectMsg(output("this is an error"))
        expectMsg(completed("error", false))
        expectMsg(done())
      }
      "read input from the thread and give it to the command" in {
        sut ! command("ask")
        expectMsg(output("who are you?: "))
        system.eventStream.publish(Input("panda"))
        expectMsg(output("Hello panda, now die!"))
        expectMsg(completed("ask", true))
        expectMsg(done())
      }
    }
    "executing multiple commands" should {
      "execute all commands in a ``` block" in {
        sut ! commands(Seq("echo hello world!", "multi", "ask"))
        expectMsg(output("hello world!"))
        expectMsg(completed("echo hello world!", true))
        expectMsg(output("hello"))
        expectMsg(output("world!"))
        expectMsg(completed("multi", true))
        expectMsg(output("who are you?: "))
        system.eventStream.publish(Input("panda"))
        expectMsg(output("Hello panda, now die!"))
        expectMsg(completed("ask", true))
        expectMsg(done())
      }
    }
  }

  private def command(string: String): Command = {
    Command(message, string)
  }

  private def commands(strings: Seq[String]): Commands = {
    Commands(message, strings.iterator)
  }

  private def output(string: String): Output = {
    Output(message, string)
  }

  private def completed(command: String, successful: Boolean): Completed = {
    Completed(message, command, successful)
  }

  private def done(): Done = {
    Done(message)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

}