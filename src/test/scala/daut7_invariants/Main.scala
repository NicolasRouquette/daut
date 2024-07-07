package daut7_invariants

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time. At most 4 locks should be acquired at any moment.
 */

trait LockEvent extends daut.Event
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class AcquireReleaseLimit extends Monitor[IO, LockEvent]:
  var count : Int = 0

  invariant ("max four locks acquired") {count <= 4}

  always:
    case acquire(t, x) =>
      count += 1
      Sync[IO].pure(Set(
        hot:
          case acquire(_, `x`) =>
            Sync[IO].pure(Set(error))
          case release(`t`, `x`) =>
            count -= 1
            Sync[IO].pure(Set(ok))
      ))


object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    val m = new AcquireReleaseLimit
    val program = for
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(acquire(2, 20))
      _ <- m.verify(acquire(3, 30))
      _ <- m.verify(acquire(4, 40))
      _ <- m.verify(release(1, 10))
      _ <- m.verify(acquire(5, 50))
      _ <- m.verify(acquire(6, 60))
      _ <- m.verify(release(2, 20))
      _ <- m.verify(release(3, 30))
      _ <- m.verify(release(4, 40))
      _ <- m.verify(release(5, 50))
      _ <- m.verify(release(6, 60))
      _ <- m.end()
    yield ()

    program.as(ExitCode.Success)

