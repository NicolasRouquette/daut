package daut9_until

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._
import scala.reflect.Selectable.reflectiveSelectable

trait LockEvent extends daut.Event
case class acquire(thread: Int, lock: Int) extends LockEvent
case class release(thread: Int, lock: Int) extends LockEvent

/**
 * A lock acquired by a task t should not be released by any other task while
 * acquired by t, that is, until it is released by t.
 *
 * This monitor illustrates until states.
 */

class TestMonitor extends Monitor[IO, LockEvent]:
  always:
    case acquire(t, l) =>
      Sync[IO].pure(Set(
        until:
          case release(`t`, `l`) => 
            Sync[IO].pure(Set(ok))
        watch:
          case release(_, `l`) => 
            Sync[IO].pure(Set(error))
      ))

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    val m = new TestMonitor
    val program = for
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(release(2, 10))
    yield ()

    program.as(ExitCode.Success)
