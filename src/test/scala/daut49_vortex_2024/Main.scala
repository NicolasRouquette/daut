package daut49_vortex_2024

import daut.{Monitor}

/*
This example concerns the dispatch and completion of commands on a spacecraft.
The following requirements must be monitored.

- Each command dispatch and completion is identified by a task id, a command number, and time.
- Each command that is dispatched must eventually be completed within 20 seconds, with same task id and cmd nr.
- Once a command is dispatched it cannot be dispatched again with same task id and cmd nr before it completes.
- After a command completion the monitor must send a message to a database informing about it.
- The average of execution times of commands must be printed out, and be less than or equal to 15 seconds.
 */

class DB {
  def executed(taskId: Int, cmdNum: Int, time1: Int, time2: Int): Unit = {
    println(s"command executed($taskId,$cmdNum,$time1,$time2)")
  }
}

enum Event {
  case Dispatch(taskId: Int, cmdNum: Int, time: Int)
  case Complete(taskId: Int, cmdNum: Int, time: Int)
}

import Event._

// Simple Monitor:

class CommandMonitor1 extends Monitor[Event] {
  always {
    case Dispatch(taskId, cmdNum, time1) =>
      hot {
        case Dispatch(`taskId`, `cmdNum`, _) => error
        case Complete(`taskId`, `cmdNum`, time2) if time2 - time1 <= 20 => ok
      }
  }
}

// Complex Monitor:



class CommandMonitor2(db: DB) extends Monitor[Event] {
  var execTimes: List[Int] = List()

  def average(list: List[Int]): Double = list.sum.toDouble / list.size

  always {
    case Dispatch(taskId, cmdNum, time1) =>
      hot {
        case Dispatch(`taskId`, `cmdNum`, _) => error
        case Complete(`taskId`, `cmdNum`, time2) if time2 - time1 <= 20 =>
          db.executed(taskId, cmdNum, time1, time2)
          execTimes = execTimes :+ (time2-time1)
      }
  }

  override def end(): this.type = {
    val averageExecutionTime = average(execTimes)
    check(averageExecutionTime <= 15, s"average execution time > 15")
    println(s"Average = $averageExecutionTime")
    super.end()
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val db = DB()
    val m = new CommandMonitor2(db)
    m.verify(Dispatch(10, 1, 1000))
    m.verify(Complete(10, 1, 1010))
    m.verify(Dispatch(12, 1, 1000))
    m.end()
  }
}





