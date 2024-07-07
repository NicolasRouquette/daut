package daut8_realtime

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._

/**
 * Property ReleaseWithin: A task acquiring a lock should eventually release
 * it within a given time period.
 */

trait LockEvent extends daut.Event
case class acquire(t:Int, x:Int, ts:Int) extends LockEvent
case class release(t:Int, x:Int, ts:Int) extends LockEvent

class ReleaseWithin(limit: Int) extends Monitor[IO, LockEvent]:
  always:
    case acquire(t, x, ts1) =>
      Sync[IO].pure(Set(
        hot:
          case release(`t`,`x`, ts2) =>
            Sync[IO].pure:
              ts2 - ts1 <= limit
              // ensure(ts2 - ts1 <= limit)
              // check(ts2 - ts1 <= limit)
              Set(ok)
      ))

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    val m = new ReleaseWithin(500)
    val program = for
      _ <- m.verify(acquire(1, 10, 100))
      _ <- m.verify(release(1, 10, 800)) // violates property
      _ <- m.end()
    yield ()

    program.as(ExitCode.Success)


