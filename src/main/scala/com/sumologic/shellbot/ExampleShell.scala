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
        addPlugin("shell", Shellbot.props(shellBase = ExampleShell))
        addPlugin("help", Props(classOf[Help]))
      }
    })
    true
  }
}
