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

import akka.actor.Props
import com.sumologic.shellbase.{ShellBase, ShellCommand}
import com.sumologic.shellbase.actor.RunCommandActor
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.plugins.PluginsFromConfig
import org.apache.commons.cli.CommandLine

/**
  * Mixin for adding ShellBot to any ShellBase.
  */
trait ShellBot extends ShellBase { shellBase =>
  private val brain = Bootstrap.system.actorOf(Props(classOf[InMemoryBrain]))

  override def commands: Seq[ShellCommand] = {
    Seq(new ShellBotCommandSet(brain))
  }

  override def init(cmdLine: CommandLine): Boolean = {
    if (super.init(cmdLine)) {
      Bootstrap.system.actorOf(RunCommandActor.props(name, commands.filterNot(_.name == "shellbot")), RunCommandActor.Name)
      Bootstrap.bootstrap(brain, PluginsFromConfig)
      true
    } else {
      false
    }
  }
}