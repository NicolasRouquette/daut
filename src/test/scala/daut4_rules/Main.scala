package daut4_rules

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._ 

/**
 * A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time. A task cannot release a lock it has not acquired.

 * This monitor illustrates always states, hot states, and the use of a
 * fact (Locked) to record history. This is effectively in part a past time property.
 */

trait LockEvent extends Event
case class acquire(t: Int, x: Int) extends LockEvent
case class release(t: Int, x: Int) extends LockEvent

class AcquireRelease extends Monitor[IO, LockEvent]:
  case class Locked(t: Int, x: Int) extends state:
    hot:
      case acquire(_, `x`) => 
        Sync[IO].delay(Set(error("acquire in locked state")))
      case release(`t`, `x`) => 
        Sync[IO].delay(Set(ok))

  always:
    case acquire(t, x) => 
      Sync[IO].pure(Set(Locked(t, x)))
    case release(t, x) if !Locked(t, x) => 
      Sync[IO].pure(Set(error("release in unlocked state")))


object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    val m = new AcquireRelease
    val program = for
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(acquire(2, 10))
    yield ()

    program.as(ExitCode.Success)
