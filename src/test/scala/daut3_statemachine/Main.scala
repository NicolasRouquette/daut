package daut3_statemachine

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._
import scala.language.implicitConversions

trait TaskEvent extends daut.Event
case class start(task: Int) extends TaskEvent
case class stop(task: Int) extends TaskEvent

/**
 * Tasks should be executed (started and stopped) in increasing order
 * according to task numbers, starting from task 0, with no other events in between, hence:
 * start(0),stop(0),start(1),stop(1),start(2),stop(2),... A started task should eventually
 * be stopped.
 *
 * This monitor illustrates next-states (as in Finite State Machines) and
 * state machines.
 */

class StartStop extends Monitor[IO, TaskEvent]:
  def start(task: Int): state =
    wnext:
      case start(`task`) => Sync[IO].delay(stop(task))

  def stop(task: Int): state =
    next:
      case stop(`task`) => Sync[IO].delay(start(task + 1))

  start(0)

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    val m = new StartStop
    val program = for
      _ <- m.verify(start(0))
      _ <- m.verify(stop(0))
      _ <- m.verify(start(1))
      _ <- m.verify(stop(1))
      _ <- m.verify(start(3))
      _ <- m.verify(stop(3))
      _ <- m.end()
    yield ()
    program.as(ExitCode.Success)
