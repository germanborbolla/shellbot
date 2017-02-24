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

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.sumologic.shellbase.actor.model.Input
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class InputReaderActorTest extends TestKit(ActorSystem("inputReader")) with WordSpecLike with Eventually with Matchers with BeforeAndAfterAll {

  "InputReaderActor" should {
    "add the incoming text to queue" in {
      val inputQueue = new LinkedBlockingQueue[String]()
      val sut = system.actorOf(InputReaderActor.props(inputQueue))

      sut ! Input("some text")

      inputQueue.poll(5, TimeUnit.SECONDS) should be("some text")
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}