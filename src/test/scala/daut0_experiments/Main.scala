package daut0_experiments

import daut0_experiments.Events._
import daut0_experiments.EventStream._
import daut0_experiments.Requirements._
import Steps._
import cats.effect.{IO, IOApp, ExitCode}

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    println(s"---------- starting verification session")

    val eventStream = EventStream.instance
    var requirements = Requirements.instance
    var hasBeenDone: List[Boolean] = List.fill(requirements.length)(false)

    var i: Int = 0
    var ready: Boolean = false
    while !ready do
      var event = eventStream(i)
      println()
      println(s"---------- event ${i}: ${event}")

      requirements.map(x => x.verify(event))
      requirements.map(x => log(x))

      val (i_new, ready_new, hasBeenDone_new) = updateEventIndex(i, eventStream, hasBeenDone, requirements)
      i = i_new
      ready = ready_new
      hasBeenDone = hasBeenDone_new

    println()
    println("---------- report ")
    requirements.map { x =>
      if x.activeStateNames.contains("Initial") || x.activeStateNames.contains("Failed") then
        log(x.stepName, s"FAILED - ${x.activeStateNames}", 15)
      else
        log(x.stepName, "PASSED", 15)
    }

    IO.pure(ExitCode.Success)

  def updateEventIndex(eventIndex: Int, eventStream: List[Event], hasBeenDone: List[Boolean], requirements: List[Step]): (Int, Boolean, List[Boolean]) =
    var newEventIndex: Int = eventIndex
    var newHasBeenDone: List[Boolean] = hasBeenDone

    var didSomeStepSwitchedToDone = false
    for j <- 0.until(requirements.length) if !didSomeStepSwitchedToDone do
      var s = requirements(j)
      if s.activeStateNames.contains("Done") && !newHasBeenDone(j) then
        newEventIndex = revertEventIndexByNTimeUnits(newEventIndex, eventStream, s.dtStable)
        newHasBeenDone = newHasBeenDone.updated(j, true)
        didSomeStepSwitchedToDone = true

    if !didSomeStepSwitchedToDone then newEventIndex += 1

    var isAnalysisFinished = newEventIndex == eventStream.length
    var isEventIndexCorrupted = newEventIndex > eventStream.length || newEventIndex < 0
    if isAnalysisFinished then println(s"FINISHED - verification finished")
    if isEventIndexCorrupted then println(s"ERROR - verification failed: corrupted event index ${newEventIndex}")

    var ready = isAnalysisFinished || isEventIndexCorrupted
    (newEventIndex, ready, newHasBeenDone)

  def revertEventIndexByNTimeUnits(eventIndex: Int, eventStream: List[Event], nTimeUnits: Int): Int =
    println()
    println(s"-------------------- reverting event stream by ${nTimeUnits} time units --------------------")

    var finished: Boolean = false
    var timeEventCounter: Int = 0
    var revertedEventIndex = eventIndex
    while !finished do
      eventStream(revertedEventIndex) match
        case Time(_) => timeEventCounter += 1
        case _ => {}

      if timeEventCounter == nTimeUnits + 1 then finished = true
      else revertedEventIndex -= 1

    revertedEventIndex

  def log(s: Step): Unit =
    log(s"${s.stepName} :", s"${s.activeStateNames.mkString(",")}", 15)

  def log(w1: String, w2: String, indent2: Int): Unit =
    println(w1 + " " * (indent2 - w1.length) + w2)
