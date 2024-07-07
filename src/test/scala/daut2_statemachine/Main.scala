package daut2_statemachine

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time.
 *
 * The property is here stated naming the intermediate state `acquired`, corresponding to writing
 * a state machine. Note, however, that the state machine is parameterized with data.
 */

trait Event extends daut.Event
case class acquire(t: Int, x: Int) extends Event
case class release(t: Int, x: Int) extends Event

class AcquireRelease extends Monitor[IO, Event]:
  always:
    case acquire(t, x) => 
      Sync[IO].delay:
        acquired(t, x)

  def acquired(t: Int, x: Int): state = 
    hot:
      case acquire(_, `x`) => 
        Set(error("lock acquired before released")).pure[IO]
      case release(`t`, `x`) => 
        Set(ok).pure[IO]



object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    val m = new AcquireRelease

    val program = for
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(release(1, 10))
      _ <- m.end()
    yield ()

    program.as(ExitCode.Success)

