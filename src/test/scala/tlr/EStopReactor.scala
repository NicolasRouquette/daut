package tlr

import cats.effect.{IO, IOApp, ExitCode, Sync}
import cats.effect.std.Queue
import daut._
import scala.math.Ordering.Implicits.infixOrderingOps
import cats.syntax.all._
import scala.language.implicitConversions

final case class Duration(sec: Int, nanosec: Int)

final case class Timestamp(sec: Int, nanosec: Int):
  def -(other: Timestamp): Duration =
    val secDiff = this.sec - other.sec
    val nanosecDiff = this.nanosec - other.nanosec
    Duration(secDiff, nanosecDiff)

object Timestamp:

  /**
    * To use this method for Ordering[Timestamp], the result must be:
    * - negative if t1 < t2
    * - positive if t1 > t2
    * - zero (if t1 == t2)
    * 
    * @param t1 Timestamp
    * @param t2 Timestamp
    * @return 
    */
  def compare(t1: Timestamp, t2: Timestamp): Int =
    if t1.sec != t2.sec then
      t1.sec - t2.sec
    else
      t1.nanosec - t2.nanosec

  given Ordering[Timestamp] with
    def compare(t1: Timestamp, t2: Timestamp): Int =
      Timestamp.compare(t1, t2)

case class Clock(
  timestamp: Timestamp
) extends Event

final case class EStopInterval(
  waypoint_id: Int,
  percent_completion_trigger: Double,
  release_after_seconds: Int
)

case class NavigatorDriveToWPFeedback(
  timestamp: Timestamp,
  taskId: Int,
  waypoint_id: Int,
  time_to_wp: Double
) extends Event

case class LocomotorFeedback(
    timestamp: Timestamp,
    taskId: Option[Int],
    percent_complete: Double
) extends Event

case class EStopReactor(interval: EStopInterval, commandQueue: Queue[IO, String]) extends Monitor[IO, Event]:

  case class NavigatorDriveToWPFeedbackSeen(t: Timestamp, task_id: Int) extends fact

  watch:
    case NavigatorDriveToWPFeedback(t, task_id, interval.waypoint_id, _) =>
      IO.raiseError(new Error("step1")) *> IO.pure(NavigatorDriveToWPFeedbackSeen(t, task_id))

  watch:
    case LocomotorFeedback(t2, Some(task_id), percent_complete) if 
      interval.percent_completion_trigger <= percent_complete && exists:
        case NavigatorDriveToWPFeedbackSeen(t1, `task_id`) if t1 <= t2 => 
          true
      =>
        val activate = s"ros2 run daut-tlr EStopUpdate.sh true"
        commandQueue.offer(activate) *> IO.pure(hot {
          case Clock(t3) if interval.release_after_seconds <= (t3 - t2).sec =>
            val release = s"ros2 run daut-tlr EStopUpdate.sh false"
            commandQueue.offer(release) *> IO.pure(ok)
        })

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    DautOptions.DEBUG = true
    for
      commandQueue <- Queue.unbounded[IO, String]
      m = EStopReactor(EStopInterval(0, 0.2, 20), commandQueue)
      _ <- m.verify(NavigatorDriveToWPFeedback(Timestamp(0, 0), 0, 10002, 100))
      _ <- m.verify(LocomotorFeedback(Timestamp(1, 0), Some(10002), 0.1))
      _ <- m.verify(LocomotorFeedback(Timestamp(2, 0), Some(10002), 0.3))
      _ <- m.verify(Clock(Timestamp(4,0)))
      _ <- m.verify(Clock(Timestamp(10,0)))
      _ <- m.verify(Clock(Timestamp(15,0)))
      _ <- m.verify(Clock(Timestamp(20,0)))
      _ <- m.verify(Clock(Timestamp(25,0)))
      _ <- m.end()
    yield ExitCode.Success
