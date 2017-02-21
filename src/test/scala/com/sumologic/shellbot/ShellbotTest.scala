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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.sumologic.shellbase.{ShellBase, ShellCommand}
import com.sumologic.sumobot.core.model.OutgoingMessage
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.BotPluginTestKit
import org.apache.commons.cli.CommandLine
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._

class ShellbotTest extends BotPluginTestKit(ActorSystem("Shellbot")) with BeforeAndAfterAll {

  private val testShell = new ShellBase("test") {
    override def commands: Seq[ShellCommand] = {
      Seq(new ShellCommand("multi", "multi") {
        override def execute(cmdLine: CommandLine) = {
          println(
            """hello
              |world!""".stripMargin)
          true
        }
      }, new ShellCommand("error", "error") {
        override def execute(cmdLine: CommandLine) = {
          Console.err.println("this is an error")
          false
        }
      })
    }
  }
  testShell.initializeCommands()
  private val sut = system.actorOf(Shellbot.props(testShell))
  sut ! InitializePlugin(null, null, null)

  "Shellbot" when {
    "executing single commands" should {
      "execute the command and send the output" in {
        sut ! instantMessage("execute echo hello world!")
        checkForMessages(Seq("hello world!",
          "Command `echo hello world!` in `test` finished successfully."))
      }
      "send all the lines in the output" in {
        sut ! instantMessage("execute multi")
        checkForMessages(Seq("hello",
          "world!",
          "Command `multi` in `test` finished successfully."))
      }
      "send the output when a command doesn't exist" in {
        sut ! instantMessage("execute badcommand")
        checkForMessages(Seq("test: command badcommand not found",
          "Command `badcommand` in `test` failed."))
      }
      "send stuff in err as well" in {
        sut ! instantMessage("execute error")
        checkForMessages(Seq("this is an error",
          "Command `error` in `test` failed."))
      }
    }
  }

  private def checkForMessages(expectedMessages: Seq[String], timeout: FiniteDuration = 5.seconds): Unit = {
    val messages = outgoingMessageProbe.receiveN(expectedMessages.size, timeout).map(_.asInstanceOf[OutgoingMessage]).map(_.text)
    messages should be(expectedMessages)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

}