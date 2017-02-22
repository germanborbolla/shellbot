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
import com.sumologic.shellbase.commands.EchoCommand
import com.sumologic.shellbase.{ShellCommand, ShellCommandSet}
import com.sumologic.sumobot.core.model.OutgoingMessage
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.BotPluginTestKit
import org.apache.commons.cli.CommandLine
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import slack.models.Team
import slack.rtm.RtmState

import scala.concurrent.duration._
class ShellbotTest extends BotPluginTestKit(ActorSystem("Shellbot")) with BeforeAndAfterAll with MockitoSugar {

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
    new EchoCommand)

  private val sut = system.actorOf(Shellbot.props("test", testCommands))
  private val state = mock[RtmState]
  when(state.team).thenReturn(Team("something", "team", "team", "", 2, false, null, "awesome"))
  sut ! InitializePlugin(state, null, null)

  private val threadId = "1487799797539.0000"

  "Shellbot" when {
    "executing single commands" should {
      "execute the command and send the output" in {
        sut ! instantMessage("execute echo hello world!", id = threadId)
        checkForMessages(Seq(inThread("Executing: `echo hello world!` in `test`"),
          inThread("hello world!"),
          inThread("Command succeeded"),
          broadcast(s"Command `echo hello world!` in `test` finished successfully, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
      "send all the lines in the output" in {
        sut ! instantMessage("execute multi", id = threadId)
        checkForMessages(Seq(inThread("Executing: `multi` in `test`"),
          inThread("hello"),
          inThread("world!"),
          inThread("Command succeeded"),
          broadcast(s"Command `multi` in `test` finished successfully, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
      "send the output when a command doesn't exist" in {
        sut ! instantMessage("execute badcommand", id = threadId)
        checkForMessages(Seq(inThread("Executing: `badcommand` in `test`"),
          inThread("test: command badcommand not found"),
          inThread("Command failed"),
          broadcast(s"Command `badcommand` in `test` failed, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
      "send stuff in err as well" in {
        sut ! instantMessage("execute error", id = threadId)
        checkForMessages(Seq(inThread("Executing: `error` in `test`"),
          inThread("this is an error"),
          inThread("Command failed"),
          broadcast(s"Command `error` in `test` failed, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
    }
  }

  private def checkForMessages(expectedMessages: Seq[MessageAndThread], timeout: FiniteDuration = 5.seconds): Unit = {
    val messages = outgoingMessageProbe.receiveN(expectedMessages.size, timeout).map(_.asInstanceOf[OutgoingMessage]).map(x => MessageAndThread(x.text, x.thread))
    messages should be(expectedMessages)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def inThread(message: String, thread: String = threadId): MessageAndThread = {
    MessageAndThread(message, Some(thread))
  }

  private def broadcast(message: String): MessageAndThread = {
    MessageAndThread(message, None)
  }
  case class MessageAndThread(message: String, thread: Option[String] = None)
}