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
import com.sumologic.shellbase.actor.model.Output
import com.sumologic.sumobot.test.BotPluginTestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually

class ThreadPrinterTest extends BotPluginTestKit(ActorSystem("inputReader")) with WordSpecLike with Eventually with Matchers with BeforeAndAfterAll {

  system.eventStream.subscribe(testActor, classOf[Output])
  "ThreadPrinter" should {
    "send the lines in the output stream when flushing" in {
      val message = instantMessage("text")
      val sut = new ThreadPrinter(message, system.eventStream)

      sut.write( """hello
          |world""".stripMargin.getBytes())
      sut.flush()

      expectMsgAllOf(Output(message, "hello"),
        Output(message, "world"))
    }
    "not send empty lines" in {
      val message = instantMessage("text")
      val sut = new ThreadPrinter(message, system.eventStream)

      sut.write( """hello
                   |world
                 """.stripMargin.getBytes())
      sut.flush()

      expectMsgAllOf(Output(message, "hello"),
        Output(message, "world"))
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}