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

import akka.actor.{ActorIdentity, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.sumologic.shellbase.actor.RunCommandActor
import com.sumologic.shellbase.actor.model.{Command, Commands, Completed, Done, Output}
import com.sumologic.sumobot.brain.{BlockingBrain, InMemoryBrain}
import com.sumologic.sumobot.core.model.OutgoingMessage
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.BotPluginTestKit
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.mock.MockitoSugar
import slack.models.Team
import slack.rtm.RtmState

import scala.concurrent.duration._

class ShellBotPluginTest extends BotPluginTestKit(ActorSystem("Shellbot", ConfigFactory.parseResourcesAnySyntax("application.conf").resolve())) with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar {

  private val runCommandProbe = TestProbe(RunCommandActor.Name)
  private val brain = system.actorOf(Props(classOf[InMemoryBrain]))
  private val blockingBrain = new BlockingBrain(brain)
  private val sut = system.actorOf(ShellBotPlugin.props(), "shell")

  private val threadId = "1487799797539.0000"

  "ShellBotPlugin" when {
    "executing single commands" should {
      "ask the runCommand actor to execute the command and notify that we're executing" in {
        val message = instantMessage(commandText("echo hello world!"), id = threadId)
        sut ! message
        checkForMessages(Seq(inThread("Executing: `echo hello world!` in `test`")))
        runCommandProbe.expectMsg(Command(message.copy(thread_ts = Some(threadId)), "echo hello world!"))
        sut ! Done(message.copy(thread_ts = Some(threadId)))
      }
      "not execute if the user is not in the authorized user list" in {
        val evilUser = mockUser("124", "evil")
        val message = instantMessage(commandText("echo hello world!"), user = evilUser, id = threadId)
        sut ! message
        outgoingMessageProbe.expectNoMsg()
        runCommandProbe.expectNoMsg()
        sut ! Done(message.copy(thread_ts = Some(threadId)))
      }
      "not execute if the shell doesn't match the configured one" in {
        val message = instantMessage(commandText("echo hello world!", "dsh"), id = threadId)
        sut ! message
        outgoingMessageProbe.expectNoMsg()
        runCommandProbe.expectNoMsg()
      }
    }
    "dealing with output" should {
      "send the line" in {
        sut ! Output(instantMessage("text", threadId = Some(threadId)), "are you there?")
        checkForMessages(Seq(inThread("are you there?")))
      }
      "send the messages when the command completes successfully" in {
        sut ! Completed(instantMessage("text", threadId = Some(threadId)), "multi", true)
        checkForMessages(Seq(inThread("Command succeeded"),
          broadcast(s"Command `multi` in `test` finished successfully, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
      "send the messages when the command fails" in {
        sut ! Completed(instantMessage("text", threadId = Some(threadId)), "multi", false)
        checkForMessages(Seq(inThread("Command failed"),
          broadcast(s"Command `multi` in `test` failed, full output available on the thread https://team.slack.com/conversation/125/p14877997975390000.")))
      }
    }
    "executing multiple commands" should {
      "ask the runCommand actor to execute the commands and notify that we're executing" in {
        val message = instantMessage(multiCommandText(Seq("echo hello", "multi", "ask")), id = threadId)
        sut ! message
        checkForMessages(Seq(inThread(
          """Executing: ```echo hello
            |multi
            |ask``` in `test`""".stripMargin)))
        runCommandProbe.expectMsg(Commands(message.copy(thread_ts = Some(threadId)), Seq("echo hello", "multi", "ask")))
        sut ! Done(message.copy(thread_ts = Some(threadId)))
      }
      "not execute if the user is not in the authorized user list" in {
        val evilUser = mockUser("124", "evil")
        val message = instantMessage(multiCommandText(Seq("echo hello", "multi", "ask")), user = evilUser, id = threadId)
        sut ! message
        outgoingMessageProbe.expectNoMsg()
        runCommandProbe.expectNoMsg()
      }
      "not execute if the shell doesn't match the configured one" in {
        val message = instantMessage(multiCommandText(Seq("echo hello", "multi", "ask"), "dsh"), id = threadId)
        sut ! message
        outgoingMessageProbe.expectNoMsg()
        runCommandProbe.expectNoMsg()
      }
    }
    "authorizing users" should {
      "ignore authorize messages from public channels" in {
        blockingBrain.store(s"accessCode.panda", "abcdef")

        val user = mockUser("123", "panda")
        sut ! publicChannelMessage(authorizeText("abcdef"), user = user, addressedToUs = true)

        checkForMessages(Seq(broadcast(s"<@123>: authorize is not allowed in public channels, access code is now invalid")))

        blockingBrain.retrieve(s"accessCode.panda") should be(None)
      }
      "ignore authorize messages from group channels" in {
        blockingBrain.store(s"accessCode.panda", "abcdef")

        val user = mockUser("123", "panda")
        sut ! groupChannelMessage(authorizeText("abcdef"), user = user, addressedToUs = true)

        checkForMessages(Seq(broadcast(s"<@123>: authorize is not allowed in public channels, access code is now invalid")))

        blockingBrain.retrieve(s"accessCode.panda") should be(None)
      }
      "not add the user if the access code doesn't match" in {
        blockingBrain.store(s"accessCode.panda", "treqw")

        val user = mockUser("123", "panda")
        sut ! instantMessage(authorizeText("abcdef"), user = user)

        checkForMessages(Seq(broadcast(s"access code is invalid")))

        blockingBrain.retrieve(s"accessCode.panda") should be(Some("treqw"))
      }
      "not add the user if no access code has been defined" in {
        val user = mockUser("123", "panda")
        sut ! instantMessage(authorizeText("abcdef"), user = user)

        checkForMessages(Seq(broadcast(s"no access code for you, you can add one by executing `shellBot getCode`")))
      }
      "not add the user if the wrong shell" in {
        blockingBrain.store(s"accessCode.panda", "abcdef")

        val user = mockUser("123", "panda")
        sut ! instantMessage(authorizeText("abcdef", "dsh"), user = user)

        checkForMessages(Seq(broadcast(s"wrong shell dude")))

        blockingBrain.retrieve(s"accessCode.panda") should be(Some("abcdef"))
      }
      "add the user if the access code matches" in {
        blockingBrain.store(s"accessCode.panda", "abcdef")

        val user = mockUser("123", "panda")
        sut ! instantMessage(authorizeText("abcdef"), user = user)

        checkForMessages(Seq(broadcast(s"authorized, you can run commands on `test`")))

        blockingBrain.retrieve(s"accessCode.panda") should be(None)
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

  override protected def beforeEach(): Unit = {
    val state = mock[RtmState]
    when(state.team).thenReturn(Team("something", "team", "team", "", 2, false, null, "awesome"))
    when(state.users).thenReturn(Seq(mockUser("123", "jshmoe")))
    sut ! InitializePlugin(state, brain, null)
    sut ! ActorIdentity("runCommand", Some(runCommandProbe.ref))

    blockingBrain.listValues().foreach(x => blockingBrain.remove(x._1))
  }

  private def inThread(message: String, thread: String = threadId): MessageAndThread = {
    MessageAndThread(message, Some(thread))
  }

  private def broadcast(message: String): MessageAndThread = {
    MessageAndThread(message, None)
  }

  private def commandText(command: String, shell: String = "test"): String = {
    s"execute `$command` in `$shell`"
  }

  private def multiCommandText(commands: Seq[String], shell: String = "test"): String = {
    s"multi ```${commands.mkString("\n")}``` in `$shell`"
  }

  private def authorizeText(code: String, shell: String = "test") = {
    s"Authorize me in `$shell` with code $code"
  }

  case class MessageAndThread(message: String, thread: Option[String] = None)
}