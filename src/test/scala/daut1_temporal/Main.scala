package daut1_temporal

import daut._
import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.syntax.applicative._  // Import the extension methods for pure

trait LockEvent extends Event
case class acquire(t: Int, x: Int) extends LockEvent
case class release(t: Int, x: Int) extends LockEvent

class AcquireRelease extends Monitor[IO, LockEvent] {
  always {
    case acquire(t, x) => 
      Sync[IO].delay {
        hot {
          case acquire(_, `x`) => Set(error("Lock acquired twice")).pure[IO]
          case release(`t`, `x`) => Set(ok).pure[IO]
        }
      }
  }
}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    DautOptions.DEBUG = true
    val m = new AcquireRelease

    val program = for {
      _ <- m.verify(acquire(1, 10))
      _ <- m.verify(release(1, 10))
      _ <- m.end()
    } yield ()

    program.as(ExitCode.Success)
  }
}
