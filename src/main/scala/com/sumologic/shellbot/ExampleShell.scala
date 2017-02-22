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

import akka.actor.{ActorSystem, Props}
import com.sumologic.shellbase.example.RNGCommandSet
import com.sumologic.shellbase.{ShellBase, ShellCommand}
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.plugins.PluginCollection
import com.sumologic.sumobot.plugins.help.Help
import org.apache.commons.cli.CommandLine

/**
  * Created by panda on 2/20/17.
  */
object ExampleShell extends ShellBase("example") {
  override def commands: Seq[ShellCommand] = {
    Seq(
      new RNGCommandSet
    )
  }

  /**
    * Process additional command line options.
    */
  override def init(cmdLine: CommandLine): Boolean = {
    super.init(cmdLine)
    Bootstrap.bootstrap(Props(classOf[InMemoryBrain]), new PluginCollection {
      override def setup(implicit system: ActorSystem) = {
        addPlugin("shell", ShellBot.props(name, commands))
        addPlugin("help", Props(classOf[Help]))
      }
    })
    true
  }
}
