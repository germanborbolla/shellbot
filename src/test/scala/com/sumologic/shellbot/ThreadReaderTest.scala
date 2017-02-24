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
import com.sumologic.shellbase.actor.model.Input
import com.sumologic.sumobot.test.BotPluginTestKit

class ThreadReaderTest extends BotPluginTestKit(ActorSystem("threadReader"))  {

  private val threadId = "1487799797539.0000"
  system.eventStream.subscribe(testActor, classOf[Input])
  "ThreadReader" should {
    val user = mockUser("123", "jshmoe")
    "ignore messages that are not in thread" in {
      val reader = system.actorOf(ThreadReader.props(user, threadId))

      reader ! channelMessage("some text", user = user)

      expectNoMsg()
    }
    "ignore messages in the thread that are not sent by the creating user" in {
      val reader = system.actorOf(ThreadReader.props(user, threadId))

      reader ! channelMessage("some text", user = mockUser("432", "panda"), threadId = Some(threadId))

      expectNoMsg()
    }
    "write the message to the output stream" in {
      val reader = system.actorOf(ThreadReader.props(user, threadId))

      reader ! channelMessage("some text", user = user, threadId = Some(threadId))

      expectMsg(Input("some text"))
    }
  }

}