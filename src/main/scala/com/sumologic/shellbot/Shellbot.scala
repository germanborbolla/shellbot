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

import java.io.{ByteArrayOutputStream, StringWriter}

import akka.actor.Props
import com.sumologic.shellbase.ShellBase
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin
import org.apache.commons.io.output.WriterOutputStream

import scala.util.matching.Regex
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

/**
  * Created by panda on 2/20/17.
  */
object Shellbot {
  def props(shellBase: ShellBase): Props = {
    Props(classOf[Shellbot], shellBase)
  }
}
class Shellbot(shellbase: ShellBase) extends BotPlugin {
  override protected def help =
    s"""Execute commands:
      |
      |execute - Run a single command on ${shellbase.name}
    """.stripMargin

  private val SingleExecute = matchText(s"execute (.*)")

  override protected def receiveIncomingMessage: ReceiveIncomingMessage = {
    case message@IncomingMessage(SingleExecute(command), _, _, _) =>
      message.respondInFuture { msg =>
        try {
          log.debug(s"Executing: $command")
          val output = new ByteArrayOutputStream()
          val successful = Console.withOut(output) { Console.withErr(output) {
            shellbase.runCommand(command)
          }}
          Source.fromBytes(output.toByteArray).getLines().foreach { line =>
            msg.say(line)
          }
          if (successful) {
            msg.response(s"Command `$command` in `${shellbase.name}` finished successfully.")
          } else {
            msg.response(s"Command `$command` in `${shellbase.name}` failed.")
          }

        } catch {
          case e: Exception =>
            log.error(e, s"Error while executing: $command")
            msg.response(s"Error while executing: $command")
        }
      }
  }

}
