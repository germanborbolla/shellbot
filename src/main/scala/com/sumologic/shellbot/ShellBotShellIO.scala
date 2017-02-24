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

import akka.event.EventStream
import com.sumologic.shellbase.ShellIO
import com.sumologic.shellbot.model.OutputLine
import com.sumologic.sumobot.core.model.IncomingMessage

/**
  * ShellIO for the ShellBot.
  */
class ShellBotShellIO(inputQueue: BlockingQueue[String], eventStream: EventStream) extends ShellIO {

  private var activeMessage: IncomingMessage = _

  def setActiveMessage(activeMessage: IncomingMessage): Unit = {
    this.activeMessage = activeMessage
  }

  override def print(x: Any): Unit = {
    println(x)
  }

  override def println(): Unit = {
  }

  override def println(x: Any): Unit = {
    val text = x match {
      case s: String => s
      case _ => x.toString
    }
    eventStream.publish(OutputLine(activeMessage, text))
  }

  override def printf(text: String, xs: Any*): Unit = {
    println(text.replace("%n", "").format(xs: _*))
  }

  override def readCharacter(): Int = {
    inputQueue.take().toCharArray.head
  }

  override def readCharacter(validChars: Seq[Char]): Int = {
    var read = readCharacter()
    while (!validChars.contains(read)) {
      read = readCharacter()
    }
    read
  }

  override def readLine(): String = {
    inputQueue.take()
  }

  override def readLine(maskCharacter: Character): String = {
    readLine()
  }

  override def readLine(prompt: String): String = {
    println(prompt)
    readLine()
  }

  override def readLine(prompt: String, maskCharacter: Character): String = {
    readLine(prompt)
  }
}
