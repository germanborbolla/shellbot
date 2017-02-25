package com.sumologic.shellbot

import akka.actor.ActorRef
import com.sumologic.shellbase.{ShellCommand, ShellCommandSet}
import org.apache.commons.cli.{CommandLine, Options}
import com.sumologic.shellbase.cmdline.RichCommandLine._
import com.sumologic.shellbase.cmdline.{CommandLineArgument, CommandLineFlag, CommandLineOption}
import com.sumologic.sumobot.brain.Brain.Store

import scala.util.Random

class ShellBotCommandSet(brain: ActorRef) extends ShellCommandSet("shellbot", "control your bot", aliases = List("bot")) {

  commands += new ShellCommand("getcode", "Get the an access code for a user") {
    val userArgument = new CommandLineArgument("user", 0, false)

    override def maxNumberOfArguments: Int = 1

    override def addOptions(opts: Options): Unit = {
      opts += userArgument
    }

    override def execute(cmdLine: CommandLine): Boolean = {
      val userName = cmdLine.get(userArgument).getOrElse(System.getProperty("user.name"))
      val accessCode = Random.alphanumeric.take(6).mkString("")
      println(s"Access code for $userName is $accessCode")
      brain ! Store(s"accessCode.$userName", accessCode)
      true
    }
  }
}
