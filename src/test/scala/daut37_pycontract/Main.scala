package daut37_pycontract

import daut._
import util.control.Breaks._

/**
  * =====================================
  * COMPARISON TO PyContract on MSL file,
  * which is not made public.
  * =====================================
  */

trait Event

case class Dispatch(time: String, cmd: String) extends Event

case class Complete(time: String, cmd: String) extends Event

case class Failure(time: String) extends Event

class CommandExecution extends Monitor[Event] {
  override def keyOf(event: Event): Option[Any] =
    event match {
      case Dispatch(_, cmd) => Some(cmd)
      case Complete(_, cmd) => Some(cmd)
      case Failure(_) => None
    }

  always {
    case Dispatch(_, cmd) => DoComplete(cmd)
  }

  case class DoComplete(cmd: String) extends fact {
    hot {
      case Complete(_, `cmd`) => ok
    }
  }

  // Note:
  // Equality for anonymous states generated by this function
  // are only equal by object identity. This means that more
  // errors are reported than if using the case class above.
  // No false negatives or false positives, just more reports
  // of the same error. This is an argument for using case classes
  // for defining states as above.
  //
  // def doComplete(cmd: String): state =
  //   hot {
  //     case Complete(_, `cmd`) => ok
  // }
}

object Test {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = false
    val m = new CommandExecution

    val goodTrace1: List[Event] = List(
      Dispatch("1", "A"),
      Dispatch("2", "B"),
      Complete("3", "A"),
      Complete("4", "B"),
    )

    val badTrace1: List[Event] = List(
      Dispatch("1", "A"),
      Dispatch("2", "B"),
      Complete("3", "A"),
      Complete("4", "C"),
    )

    m.verify(badTrace1)
  }
}

/**
  * CSV parser.
  */

class FastCSVReader(fileName: String) {
  // https://github.com/osiegmar/FastCSV

  import java.io.File
  import de.siegmar.fastcsv.reader.CsvReader
  import de.siegmar.fastcsv.reader.CsvRow
  import java.nio.charset.StandardCharsets
  import scala.collection.JavaConverters._

  val file = new File(fileName)
  val csvReader = new CsvReader
  val csvParser = csvReader.parse(file, StandardCharsets.UTF_8)
  var row: CsvRow = csvParser.nextRow()

  def hasNext: Boolean = row != null

  def next(): List[String] = {
    val line = row.getFields.asScala.toList
    row = csvParser.nextRow()
    line
  }
}

/**
  * Analyzing log.
  */

class LogReader(fileName: String) {
  val reader = new FastCSVReader(fileName)
  var lineNr: Long = 0

  def hasNext: Boolean = reader.hasNext

  def next: Option[Event] = {
    val line = reader.next()
    lineNr += 1
    line(0) match {
      case "CMD_DISPATCH" =>
        Some(Dispatch(line(2), line(1)))
      case "CMD_COMPLETE" =>
        Some(Complete(line(2), line(1)))
      case "SEQ_EVR_WAIT_CMD_COMPLETED_FAILURE" =>
        Some(Failure(line(1)))
      case _ => None
    }
  }
}

object VerifyNokiaLog {
  def main(args: Array[String]): Unit = {
    val csvFile = new LogReader("src/test/scala/daut37_pycontract/log_msl_timed.csv")
    val monitor = new CommandExecution
    Util.time("processing log") {
      while (csvFile.hasNext) {
        csvFile.next match {
          case None =>
          case Some(event) =>
            monitor.verify(event, csvFile.lineNr)
        }
      }
      monitor.end()
      println(s"${csvFile.lineNr} lines processed")
    }
  }
}
