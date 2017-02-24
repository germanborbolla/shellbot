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

import java.util.concurrent.BlockingQueue

import akka.actor.{Actor, ActorLogging, Props}
import com.sumologic.sumobot.core.model.IncomingMessage
import slack.models.User

/**
  * Listens for slack messages on a thread and for a given user, adds those to the provided queue.
  */
object ThreadReader {
  def props(user: User, watchingThread: String, queue: BlockingQueue[String]): Props = {
    Props(classOf[ThreadReader], user, watchingThread, queue)
  }
}
class ThreadReader(user: User, watchingThread: String, queue: BlockingQueue[String]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case IncomingMessage(text, _, _, sentByUser, _, Some(thread)) if thread == watchingThread && sentByUser == user =>
      queue.put(text)
  }
}

