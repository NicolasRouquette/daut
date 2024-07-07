package daut5_rules

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time.
 *
 * property ReleaseAcquired: A task cannot release a lock it has not acquired.
 * */

trait Event extends daut.Event
case class acquire(t: Int, x: Int) extends Event
case class release(t: Int, x: Int) extends Event

class AcquireRelease extends Monitor[IO, Event]:
  always:
    case acquire(t, x) =>
      Sync[IO].pure(Set(
        hot:
          case acquire(_, `x`) =>
            Sync[IO].pure(Set(error("double acquire")))
          case release(`t`, `x`) =>
            Sync[IO].pure(Set(ok))
      ))

class ReleaseAcquired extends Monitor[IO, Event]:
  case class Locked(t: Int, x: Int) extends state:
    watch:
      case release(`t`, `x`) =>
        Sync[IO].pure(Set(ok))

  always:
    case acquire(t, x) =>
      Sync[IO].pure(Set(Locked(t, x)))
    case release(t, x) if !Locked(t, x) =>
      Sync[IO].pure(Set(error("release when unlocked")))

class Monitors extends Monitor[IO, Event]:
  monitor(new AcquireRelease, new ReleaseAcquired)

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    val m = new Monitors
    val program = for
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(release(1, 10))
      _ <- m.end()
    yield ()

    program.as(ExitCode.Success)
