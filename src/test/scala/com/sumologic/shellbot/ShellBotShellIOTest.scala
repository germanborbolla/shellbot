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

import java.util.concurrent.LinkedBlockingQueue

import akka.actor.ActorSystem
import com.sumologic.shellbot.model.OutputLine
import com.sumologic.sumobot.test.BotPluginTestKit
import org.scalatest.BeforeAndAfterEach

class ShellBotShellIOTest extends BotPluginTestKit(ActorSystem("shellBotIO")) with BeforeAndAfterEach {

  private val queue = new LinkedBlockingQueue[String]()
  private val activeMessage = instantMessage("text", threadId = Some("thread"))
  private val sut = new ShellBotShellIO(queue, system.eventStream)
  system.eventStream.subscribe(testActor, classOf[OutputLine])
  sut.setActiveMessage(activeMessage)

  "ShellBotShellIO" when {
    "reading a character" should {
      "return the first character of the head of the queue" in {
        queue.put("a")
        sut.readCharacter() should be('a')
      }
      "return the first character if it's in the allowed set" in {
        queue.put("a")
        sut.readCharacter(Seq('a', 'c')) should be('a')
      }
      "do not return invalid characters" in {
        queue.put("b")
        queue.put("c")
        sut.readCharacter(Seq('a', 'c')) should be('c')
      }
    }
    "reading lines" should {
      "return the head of the queue" in {
        queue.put("hello")
        queue.put("world")
        sut.readLine() should be("hello")
        sut.readLine('*') should be("world")
      }
      "send the prompt" in {
        queue.put("panda")
        queue.put("german")
        sut.readLine("what's your name?") should be("panda")
        sut.readLine("what's your name again?", '*') should be("german")

        expectMsg(OutputLine(activeMessage, "what's your name?"))
        expectMsg(OutputLine(activeMessage, "what's your name again?"))
      }
    }
    "printing" should {
      "do nothing on empty println" in {
        sut.println()
        expectNoMsg()
      }
      "send a message when printing" in {
        sut.print(123)
        sut.println("hello world")
        expectMsg(OutputLine(activeMessage, "123"))
        expectMsg(OutputLine(activeMessage, "hello world"))
      }
      "apply the format and then send a message" in {
        sut.printf("%d) %s%n", 1, "forge")
        expectMsg(OutputLine(activeMessage, "1) forge"))
      }
    }
  }

  override protected def afterEach(): Unit = {
    queue.clear()
  }
}