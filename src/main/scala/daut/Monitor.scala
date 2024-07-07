package daut

import cats.effect.{IO, Sync}
import cats.implicits._

import scala.collection.mutable.{Map => MutMap}
import scala.reflect.Selectable.reflectiveSelectable
import scala.compiletime.uninitialized

import java.io.{BufferedWriter, FileWriter, PrintWriter}

object DautOptions:
  var DEBUG: Boolean = false
  var PRINT_ERROR_BANNER = false

object Util:
  def debug(msg: => String): Unit =
    if DautOptions.DEBUG then println(s"$msg")

  def time[R](text: String)(block: => R): R =
    val t1 = System.currentTimeMillis()
    val result = block
    val t2 = System.currentTimeMillis()
    val ms = (t2 - t1).toFloat
    val sec = ms / 1000
    println(s"\n--- Elapsed $text time: " + sec + "s" + "\n")
    result

import daut.Util._

case class MonitorError() extends RuntimeException

trait Event

class Monitor[F[_]: Sync, E <: Event]:
  thisMonitor =>

  case class InitialEvent(eventNr: Long, event: E)

  private class States:
    private var mainStates: Set[state] = Set()
    private val indexedStates: MutMap[Any, Set[state]] = MutMap()

    def getAllStates: Set[state] = mainStates.union(indexedStates.values.flatten.toSet)
    def getMainStates: Set[state] = mainStates
    def getIndexes: Set[Any] = indexedStates.keySet.toSet
    def getIndexedSet(index: Any): Set[state] = indexedStates(index)

    def initial(s: state): Unit =
      mainStates += s

    def applyEvent(event: E)(using F: Sync[F]): F[Unit] =
      var transitionTriggered: Boolean = false
      val key = keyOf(event)
      key match
        case None =>
          for
            _ <- applyEventToStateSet(event)(mainStates).map:
              case None => ()
              case Some(newStates) =>
                mainStates = newStates
                transitionTriggered = true
          
            _ <- indexedStates.toList.traverse:
                case (index, ss) =>
                  applyEventToStateSet(event)(ss).map:
                      case None => ()
                      case Some(newStates) =>
                        indexedStates += (index -> newStates)
                        transitionTriggered = true
                    
              
          yield ()
        case Some(index) =>
          val ss = indexedStates.getOrElse(index, mainStates)
          applyEventToStateSet(event)(ss).map:
              case None => ()
              case Some(newStates) =>
                indexedStates += (index -> newStates)
                transitionTriggered = true

    private def applyEventToStateSet(event: E)(states: Set[state])(using F: Sync[F]): F[Option[Set[state]]] =
      var transitionTriggered: Boolean = false
      var statesToRemove: Set[state] = emptyStateSet
      var statesToAdd: Set[state] = emptyStateSet
      var newStates = states
      states.toList
      .traverse:
        sourceState =>
          sourceState(event).map:
              case None => ()
              case Some(targetStates) =>
                transitionTriggered = true
                statesToRemove += sourceState
                targetStates.foreach:
                  case `error` | `ok` => ()
                  case `stay` => statesToAdd += sourceState
                  case targetState => statesToAdd += targetState
            
      .map:
        _ =>
          if transitionTriggered then
            newStates --= statesToRemove
            newStates ++= statesToAdd
            Option(newStates)
          else None

  private var monitorAtTop: Boolean = true
  protected def keyOf(event: E): Option[Any] = None
  protected def relevant(event: E): Boolean = true
  private val monitorName = this.getClass.getSimpleName
  private var monitors: List[Monitor[F, E]] = List()
  private var abstractMonitors: List[Monitor[F, ? <: Event]] = List()
  private val states = new States()
  private var invariants: List[(String, Unit => Boolean)] = Nil
  private var initializing: Boolean = true
  private var endCalled: Boolean = false
  private var errorCount = 0
  var eventNumber: Long = 0
  private var recordings: List[String] = List()
  var STOP_ON_ERROR: Boolean = false
  var SHOW_TRANSITIONS: Boolean = false

  def record(message: String): Unit =
    recordings = recordings :+ s"- Recording [${monitorName}] $message"

  def showTransitions(flag: Boolean = true): Monitor[F, E] =
    SHOW_TRANSITIONS = flag
    for monitor <- monitors do
      monitor.showTransitions(flag)
    this

  def monitor(monitors: Monitor[F, E]*): Unit =
    for monitor <- monitors do
      monitor.monitorAtTop = false
    this.monitors ++= monitors

  def monitorAbstraction[E <: Event](monitor: Monitor[F, E]): Monitor[F, E] =
    abstractMonitors = abstractMonitors :+ monitor
    monitor

  def stopOnError(): Monitor[F, E] =
    STOP_ON_ERROR = true
    for monitor <- monitors do
      monitor.stopOnError()
    this

  protected type Transitions = PartialFunction[E, F[Set[state]]]

  private def noTransitions(using F: Sync[F]): Transitions =
    case _ if false => Sync[F].pure(Set.empty[state])

  private val emptyStateSet: Set[state] = Set()

  protected def invariant(inv: => Boolean): Unit =
    invariants ::= ("", (_: Unit) => inv)
    check(inv, "")

  protected def invariant(e: String)(inv: => Boolean): Unit =
    invariants ::= (e, (_: Unit) => inv)
    check(inv, e)

  def getAllStates: Set[state] = states.getAllStates

  protected trait state:
    thisState =>

    protected var name: String = "anonymous"
    private[daut] var transitions: Transitions = noTransitions
    private var transitionsInitialized: Boolean = false
    private[daut] var isInitial: Boolean = false
    private[daut] var isFinal: Boolean = true
    var initialEvent: Option[InitialEvent] = None

    infix def watch(ts: Transitions): state =
      if transitionsInitialized then return thisMonitor.watch(ts)
      transitionsInitialized = true
      name = "watch"
      transitions = ts
      this

    infix def always(ts: Transitions): state =
      if transitionsInitialized then return thisMonitor.always(ts)
      transitionsInitialized = true
      name = "always"
      transitions = ts.andThen(_.map(_ + this))
      this

    infix def hot(ts: Transitions): state =
      if transitionsInitialized then return thisMonitor.hot(ts)
      transitionsInitialized = true
      name = "hot"
      transitions = ts
      isFinal = false
      this

    infix def wnext(ts: Transitions): state =
      if transitionsInitialized then return thisMonitor.wnext(ts)
      transitionsInitialized = true
      name = "wnext"
      transitions = ts.orElse:
        case _ => 
          Sync[F].pure(Set(error))
      this

    infix def next(ts: Transitions): state =
      if transitionsInitialized then return thisMonitor.next(ts)
      transitionsInitialized = true
      name = "next"
      transitions = ts.orElse:
        case _ => 
          Sync[F].pure(Set(error))
      isFinal = false
      this

    infix def unless(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new:
      def watch(ts2: Transitions): state =
        if transitionsInitialized then return thisMonitor.unless(ts1).watch(ts2)
        transitionsInitialized = true
        name = "until"
        transitions = ts1.orElse(ts2.andThen(_.map(_ + thisState)))
        thisState

    infix def until(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new:
      def watch(ts2: Transitions): state =
        if transitionsInitialized then return thisMonitor.until(ts1).watch(ts2)
        transitionsInitialized = true
        name = "until"
        transitions = ts1.orElse(ts2.andThen(_.map(_ + thisState)))
        isFinal = false
        thisState

    def apply(event: E): F[Option[Set[state]]] = 
      Sync[F].defer:
        if transitions.isDefinedAt(event) then
          val theInitialEvent: Option[InitialEvent] = initialEvent match
            case None => Some(InitialEvent(eventNumber, event))
            case _ => initialEvent
          transitions(event).map:
            newStates =>
              newStates.foreach:
                case `error` => reportErrorOnEvent(event, this.initialEvent)
                case `ok` | `stay` => ()
                case ns => if !ns.isInitial then ns.initialEvent = theInitialEvent
              Some(newStates)
      
        else Sync[F].pure(None)

    if initializing then thisMonitor.initial(this)

  protected trait fact extends state

  protected trait anonymous extends state:
    infix def label(values: Any*): state =
      name += values.map(_.toString).mkString("(", ",", ")")
      this

    override def toString: String = name

  protected case object stay extends state
  protected case object ok extends state
  protected case object error extends state

  protected def error(msg: String): state =
    println("\n*** " + msg + "\n")
    error

  protected case class during(es1: E*)(es2: E*) extends state:
    private val begin = es1.toSet
    private val end = es2.toSet
    private[daut] var on: Boolean = false

    def ==>(b: Boolean): Boolean =
      !on || b

    def startsTrue: during =
      on = true
      this

    this.always:
      case e =>
        if begin.contains(e) then
          on = true
        else if end.contains(e) then
          on = false
        Sync[F].pure(Set.empty[state])
    thisMonitor.initial(this)

  protected given Conversion[during, Boolean] = _.on

  protected infix def watch(ts: Transitions): anonymous = new anonymous:
    this.watch(ts)

  protected infix def always(ts: Transitions): anonymous = new anonymous:
    this.always(ts)

  protected infix def hot(ts: Transitions): anonymous = new anonymous:
    this.hot(ts)

  protected infix def wnext(ts: Transitions): anonymous = new anonymous:
    this.wnext(ts)

  protected infix def next(ts: Transitions): anonymous = new anonymous:
    this.next(ts)

  protected infix def unless(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new:
    infix def watch(ts2: Transitions): anonymous = new anonymous:
      this.unless(ts1).watch(ts2)

  protected infix def until(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new:
    infix def watch(ts2: Transitions): anonymous = new anonymous:
      this.until(ts1).watch(ts2)

  def exists(pred: PartialFunction[state, Boolean]): Boolean =
    val alwaysFalse: PartialFunction[state, Boolean] =
      case _ => false
    states.getAllStates exists (pred orElse alwaysFalse)

  protected def map(pf: PartialFunction[state, Set[state]]): Object { def orelse(otherwise: => Set[state]): Set[state]} = new:
    def orelse(otherwise: => Set[state]): Set[state] =
      val matchingStates = states.getAllStates filter pf.isDefinedAt
      if matchingStates.nonEmpty then
        (for matchingState <- matchingStates yield pf(matchingState)).flatten
      else
        otherwise

  protected def ensure(b: Boolean): state =
    if b then ok else error

  protected def check(b: Boolean): Unit =
    if !b then reportError("check failed")

  protected def check(b: Boolean, e: String): Unit =
    if !b then reportError(e)

  protected def initial(s: state): Unit =
    s.isInitial = true
    states.initial(s)

  protected given state2Set: Conversion[Set[state], F[Set[state]]] = states => Sync[F].pure(states)

  protected given Conversion[state, Boolean] = states.getAllStates contains _

  protected given Conversion[Unit, Set[state]] = _ => Set(ok)

  protected given Conversion[Boolean, Set[state]] = b => Set(if b then ok else error)

  protected given Conversion[state, Set[state]] = Set(_)

  protected given Conversion[(state, state), Set[state]] = s => Set(s._1, s._2)

  protected given Conversion[(state, state, state), Set[state]] = s => Set(s._1, s._2, s._3)

  protected given Conversion[List[state], Set[state]] = _.toSet

  protected given Conversion[state, Object {def &(s2: state): Set[state]}] = s1 => new:
    def &(s2: state): Set[state] = Set(s1, s2)

  protected given Conversion[Set[state], Object {def &(s: state): Set[state]}] = set => new:
    def &(s: state): Set[state] = set + s

  protected given Conversion[Boolean, Object {def ==>(q: Boolean): Boolean}] = p => new:
    def ==>(q: Boolean): Boolean = !p || q

  def verify(events: List[E])(using F: Sync[F]): F[this.type] =
    events.traverse(event => verify(event)).map(_ => end()).map(_ => this)

  def verify(event: E, eventNr: Long = 0)(using F: Sync[F]): F[this.type] =
    for
      _ <- F.delay { if eventNr > 0 then eventNumber = eventNr else eventNumber += 1 }
      _ <- if (initializing) F.delay { initializing = false } else F.unit
      _ <- F.delay { verifyBeforeEvent(event) }
      _ <- if (monitorAtTop) F.delay { debug("\n===[" + event + "]===\n") } else F.unit
      _ <- if relevant(event) then {
              for
                _ <- states.applyEvent(event)
                _ <- invariants.toList.traverse { case (e, inv) => check(inv(()), e).pure[F] }.void
                _ <- monitors.traverse(_.verify(event).void)
              yield ()
            } else
              Sync[F].unit
      _ <- if (monitorAtTop && DautOptions.DEBUG) F.delay { printStates() } else F.unit
      _ <- F.delay { verifyAfterEvent(event) }
    yield this

  def end()(using F: Sync[F]): F[this.type] =
    if !endCalled then
      endCalled = true
      for
        _ <- Sync[F].delay(debug(s"Ending Daut trace evaluation for $monitorName"))
        _ <- Sync[F].delay:
          val theEndStates = states.getAllStates
          val hotStates = theEndStates.filter(!_.isFinal)
          if hotStates.nonEmpty then
            println()
            println(s"*** Non final Daut $monitorName states:")
            println()
            hotStates.foreach: hotState =>
              print(hotState)
              reportErrorAtEnd(hotState.initialEvent)
              println()
        _ <- monitors.traverse(_.end().void)
        _ <- abstractMonitors.traverse(_.end().void)
        _ <- Sync[F].delay(println(s"Monitor $monitorName detected $errorCount errors!"))
      yield this
    else
      Sync[F].pure(this)

  def apply(event: E)(using F: Sync[F]): F[this.type] = verify(event)
  def apply(events: List[E])(using F: Sync[F]): F[this.type] = verify(events)

  protected def verifyBeforeEvent(event: E): Unit = {}
  protected def verifyAfterEvent(event: E): Unit = {}
  protected def callBack(): Unit = {}

  def getErrorCount: Int =
    var count = errorCount
    for m <- monitors do count += m.getErrorCount
    count

  def printStates(): Unit =
    println(s"--- $monitorName:")
    println("[memory] ")
    for s <- states.getMainStates do
      println(s"  $s")
    println()
    for index <- states.getIndexes do
      println(s"[index=$index]")
      for s <- states.getIndexedSet(index) do
        println(s"  $s")
    println()
    for m <- monitors do m.printStates()

  def getRecordings(): List[String] =
    var allRecordings: List[String] = recordings
    for monitor <- monitors do
      allRecordings = allRecordings ++ monitor.getRecordings()
    allRecordings

  protected def reportErrorOnEvent(event: E, initialEvent: Option[InitialEvent]): Unit =
    println("\n*** ERROR")
    initialEvent match
      case None =>
      case Some(trigger) =>
        println(s"trigger event: ${trigger.event} event number ${trigger.eventNr}")
    println(s"current event: $event event number $eventNumber")
    reportError("Error occurred during event processing")

  protected def reportErrorAtEnd(initialEvent: Option[InitialEvent]): Unit =
    println("\n*** ERROR AT END OF TRACE")
    initialEvent match
      case None =>
      case Some(trigger) =>
        println(s"trigger event: ${trigger.event} event number ${trigger.eventNr}")
    reportError("Error occurred at the end of trace")

  protected def reportError(e: String): F[Unit] =
    Sync[F].delay:
      println("***********")
      println(s"ERROR : $e")
      println("***********")
      errorCount += 1
      println(s"$monitorName error # $errorCount")
      if DautOptions.PRINT_ERROR_BANNER then
        println(
          s"""
            |███████╗██████╗ ██████╗  ██████╗ ██████╗
            |██╔════╝██╔══██╗██╔══██╗██╔═══██╗██╔══██╗
            |█████╗  ██████╔╝██████╔╝██║   ██║██████╔╝
            |██╔══╝  ██╔══██╗██╔══██╗██║   ██║██╔══██╗
            |███████╗██║  ██║██║  ██║╚██████╔╝██║  ██║
            |╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝
            |
          """.stripMargin)
      callBack()
      if STOP_ON_ERROR then
        println("\n*** terminating on first error!\n")
        Sync[F].raiseError(MonitorError())
      else Sync[F].unit

object Monitor:
  private var jsonWriter: PrintWriter = uninitialized
  private var jsonEncoder: Any => Option[String] = uninitialized

  var SHOW_TRANSITIONS: Boolean = false

  def logTransitionsAsJson(fileName: String, encoder: Any => Option[String]): Unit =
    jsonWriter = new PrintWriter(new BufferedWriter(new FileWriter(fileName, false)))
    jsonEncoder = encoder

  def isWriterInitialized: Boolean = jsonWriter != null

  def logTransition(obj: Any): Unit =
    if isWriterInitialized then
      val json = jsonEncoder(obj)
      json match
        case None =>
        case Some(string) =>
          jsonWriter.println(string)
          jsonWriter.flush()

  def closeJsonFile(): Unit =
    if jsonWriter != null then
      jsonWriter.close()

class Abstract[F[_]: Sync, E <: Event] extends Monitor[F, E]:
  private val abstraction = new scala.collection.mutable.ListBuffer[E]()
  private var recordAll: Boolean = false

  def record(flag: Boolean): Abstract[F, E] =
    recordAll = flag
    this

  def push(event: E): Unit =
    abstraction += event

  def trace: List[E] = abstraction.toList

  override def verify(event: E, eventNr: Long = 0)(using F: Sync[F]): F[this.type] =
    if recordAll then push(event)
    super.verify(event, eventNr)

class Translate[F[_]: Sync, E1 <: Event, E2 <: Event] extends Monitor[F, E1]:
  private val abstraction = new scala.collection.mutable.ListBuffer[E2]()

  def push(event: E2): Unit =
    abstraction += event

  def trace: List[E2] = abstraction.toList
