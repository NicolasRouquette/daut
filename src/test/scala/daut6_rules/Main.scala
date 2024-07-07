package daut6_rules

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._

trait TaskEvent extends daut.Event
case class start(task: Int) extends TaskEvent
case class stop(task: Int) extends TaskEvent

/**
 *
 * Tasks should be executed (started and stopped) in increasing order according
 * to task numbers, staring from task 0: 0, 1, 2, ...
 *
 * This monitor illustrates next-states (as in Finite State Machines) and
 * state machines.
 */

class TestMonitor extends Monitor[IO, TaskEvent]:
  case class Start(task: Int) extends state:
    wnext:
      case start(`task`) => 
        Sync[IO].pure(Set(Stop(task)))

  case class Stop(task: Int) extends state:
    next:
      case stop(`task`) =>
        Sync[IO].pure(Set(Start(task + 1)))

  Start(0)


object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    val m = new TestMonitor
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


