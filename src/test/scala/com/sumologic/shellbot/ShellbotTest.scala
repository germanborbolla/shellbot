package com.sumologic.shellbot

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.sumologic.shellbase.{ShellBase, ShellCommand}
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.BotPluginTestKit
import org.apache.commons.cli.CommandLine
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._

class ShellbotTest extends BotPluginTestKit(ActorSystem("Shellbot")) with BeforeAndAfterAll {

  private val testShell = new ShellBase("test") {
    override def commands: Seq[ShellCommand] = {
      Seq(new ShellCommand("multi", "multi") {
        override def execute(cmdLine: CommandLine) = {
          println(
            """hello
              |world!""".stripMargin)
          true
        }
      }, new ShellCommand("error", "error") {
        override def execute(cmdLine: CommandLine) = {
          Console.err.println("this is an error")
          false
        }
      })
    }
  }
  testShell.initializeCommands()
  private val sut = system.actorOf(Shellbot.props(testShell))
  sut ! InitializePlugin(null, null, null)

  "Shellbot" when {
    "executing single commands" should {
      "execute the command and send the output" in {
        sut ! instantMessage("execute echo hello world!")
        confirmOutgoingMessage(5.seconds) {
          msg =>
            val exoectedOutput =
              """[test] Command: echo hello world! finished successfully.
                |hello world!
                |""".stripMargin
            msg.text should be(exoectedOutput)
        }
      }
      "send all the lines in the output" in {
        sut ! instantMessage("execute multi")
        confirmOutgoingMessage(5.seconds) {
          msg =>
            val expectedOutput =
              f"""[test] Command: multi finished successfully.
                  |hello
                  |world!
                  |""".stripMargin
            msg.text should be(expectedOutput)
        }
      }
      "send the output when a command fails" in {
        sut ! instantMessage("execute badcommand")
        confirmOutgoingMessage(5.seconds) {
          msg =>
            val expectedOutput =
              """[test] Command: badcommand failed.
                  |test: command badcommand not found
                  |""".stripMargin
            println(msg.text)
            println(expectedOutput)
            msg.text should be(expectedOutput)
        }
      }
      "send stuff in err as well" in {
        sut ! instantMessage("execute error")
        confirmOutgoingMessage(5.seconds) {
          msg =>
            val expectedOutput =
              """[test] Command: error failed.
                  |this is an error
                  |""".stripMargin
            msg.text should be(expectedOutput)
        }
      }
    }

  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

}