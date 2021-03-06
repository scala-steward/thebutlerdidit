package org.pfcoperez.thebutlerdidit

import scalajs.js.Dynamic
import org.scalajs.dom
import org.scalajs.dom.{ document, window }
import org.scalajs.dom.Element
import org.scalajs.dom.html.{ Option => _, Element => _, _ }

import jsfacades.Viz
import ThreadDumpParsers.parseReportString

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.raw.Text
import org.pfcoperez.thebutlerdidit.model.Report
import org.pfcoperez.thebutlerdidit.datastructures.SparseGraph
import org.pfcoperez.thebutlerdidit.datastructures.SparseGraph.RenderAttribute

/** This singleton object implements the UI running in the browser
  * by manipulating the DOM directly. It depends on the multiplatform
  * `jstack` output models and parsers which are part of this project as well as
  * on both https://github.com/mdaines/viz.js and Twitter's Bootstrap CSS framework.
  *
  * It certainly can be refactored to make most of the generated components
  * re-usable. If further development of more complex features happens,
  * this technical debt can should be paid first.
  */
object WebUI {

  /** This method takes a string containing the output from `jstack [-l] <PID>`
    * and returns either an string containing a parsing errors (left) or the
    * DOT Graphviz representation of the parsed dependencies tree.
    *
    * @param reportStr Output of `jstack`
    * @param withIsolatedNodes Wether or not threads with no acquired locks and with
    *                          no acquisition requests should be represented in the
    *                          output.
    * @return
    */
  def processReport(reportStr: String, withIsolatedNodes: Boolean): Either[String, String] = {
    val result = parseReportString(reportStr)

    def nodesGraphvizAttributes(report: Report): Report.ThreadId => Seq[RenderAttribute] = {
      (threadId: Report.ThreadId) =>
        val blockedThreads = report.deadLockElements.map(_.blockedThreadName)
        val blockedAttributes = List(
          RenderAttribute("fillcolor", "indianred"),
          RenderAttribute("fontcolor", "white")
        )
        val regularAttributes = Nil
        if (blockedThreads.contains(threadId)) blockedAttributes else regularAttributes
    }

    result.fold(
      { case problem => Left(problem._3.trace().msg) },
      { case (report, _) => Right(report.asGraph.renderGraphviz(withIsolatedNodes, nodesGraphvizAttributes(report))) }
    )
  }

  /** Application tate aside from the DOM, ideally no business logic state should be ingrained in the DOM,
    * This UI was small and simple in the beginning so part of this state (isolated threads toggle)
    * is in fact in the DOM future changes will fix that.
    *
    * Any new logic state should be part of the `State` case class as an attribute.
    *
    * @param previousSuccessfulTransformation Wether or not the UI is currently showing the DOT and graphic representation of a valid input.
    * @param renderEngine Render engine for graphviz: "circo", "dot", "fdp", "neato", "osage", "twopi" ...
    */
  case class State(
      previousSuccessfulTransformation: Boolean,
      renderEngine: String,
      availableRenderEngines: Seq[String],
      defaultRenderEngine: String
  )

  /** The state is contained in a singleton object which can be safely updated and read through the
    * `update` and `getCurrent` methods. Concurrency safety is thus guaranteed.
    */
  object State {
    // Global app state (besides DOM)
    private var current: State = State(
      previousSuccessfulTransformation = false,
      renderEngine = "dot",
      availableRenderEngines = Seq("circo", "dot", "fdp", "neato", "osage", "twopi"),
      defaultRenderEngine = "dot"
    )

    /** Safe atomic update method
      *
      * @param update Function taking the current state and providing a new one which becomes
      *               the new global state. All this happens atomically.
      * @return
      */
    def update(update: State => State): State =
      State.synchronized {
        State.current = update(State.current)
        State.current
      }

    def getCurrent: State =
      State.synchronized(current)
  }

  // Helper classes to represent elements in the Bootstrap layout.

  sealed trait ColumnClass {
    def className: String
  }
  case object Md extends ColumnClass {
    def className: String = "col-md"
  }

  case class MdN(n: Int) extends ColumnClass {
    assert(n > 0)
    assert(n <= 12)

    def className: String = s"col-md-$n"
  }

  /** Method to wrap DOM elements in Bootstrap cells.
    *
    * @param columnClass
    * @param element
    * @return
    */
  def wrappedInColumn(columnClass: ColumnClass)(element: => Element): Element = {
    val column = document.createElement("div")
    column.setAttribute("class", columnClass.className)
    column.appendChild(element)
    column
  }

  def main(args: Array[String]): Unit = {

    // Creates an instance of VizJS Javascript library wrapper.
    val viz = new Viz()

    // Rows in the Bootstrap grid

    val topLevelDiv = document.createElement("div")
    topLevelDiv.setAttribute("class", "container")

    document.body.appendChild(topLevelDiv)

    val headerDiv = document.createElement("div")
    headerDiv.setAttribute("class", "row")
    topLevelDiv.appendChild(headerDiv)

    val textIODiv = document.createElement("div")
    textIODiv.setAttribute("class", "row")
    topLevelDiv.appendChild(textIODiv)

    val optionsDiv = document.createElement("div")
    optionsDiv.setAttribute("class", "row")
    topLevelDiv.appendChild(optionsDiv)

    val actionsDiv = document.createElement("div")
    actionsDiv.setAttribute("class", "row")
    topLevelDiv.appendChild(actionsDiv)

    val resultsRow = document.createElement("div")
    resultsRow.setAttribute("class", "row")
    topLevelDiv.appendChild(resultsRow)

    val resultsDiv = wrappedInColumn(MdN(12)) {
      document.createElement("div")
    }
    resultsRow.appendChild(resultsDiv)

    val iconDiv = wrappedInColumn(MdN(4)) {
      val imageNode = document.createElement("img").asInstanceOf[Image]
      imageNode.src = "logo.png"
      imageNode.style = "padding: 2em"
      imageNode.width = 255
      imageNode
    }

    val textDiv = wrappedInColumn(MdN(8)) {
      val textNode = document.createElement("p").asInstanceOf[Paragraph]
      textNode.setAttribute("class", "text-lg-center")
      textNode.textContent = """
          | This is a self contained web UI running in your browser which takes
          | the output of `jstack [-l] <PID>` and spits out the graph
          | of lock relations between <PID> process threads highlighting
          | threads which participate in deadlocks.
          |
          | There is NO COMMUNICATION with a server, it works off-line and
          | your private data is confined to your box.
        """.stripMargin
      textNode.style = "padding: 2em"
      textNode
    }

    val inputTextNode = document.createElement("textarea").asInstanceOf[TextArea]
    val inputTextDiv = wrappedInColumn(MdN(7)) {
      inputTextNode.cols = 80
      inputTextNode.rows = 32
      inputTextNode.setAttribute("style", "resize:none; width: 100%;")
      inputTextNode
    }

    val outTextNode = document.createElement("textarea").asInstanceOf[TextArea]
    val outTextDiv = wrappedInColumn(MdN(5)) {
      outTextNode.cols = 50
      outTextNode.rows = 32
      outTextNode.setAttribute("style", "resize:none; width: 100%;")
      outTextNode.disabled = true
      outTextNode
    }

    val analyzeButton = document.createElement("button").asInstanceOf[Button]
    val analyzeDiv = wrappedInColumn(MdN(1)) {
      analyzeButton.textContent = "Analyze!"
      analyzeButton.setAttribute("class", "btn btn-default")
      analyzeButton
    }

    val resetButton = document.createElement("button").asInstanceOf[Button]
    val resetDiv = wrappedInColumn(MdN(1)) {
      resetButton.textContent = "Clear"
      resetButton.setAttribute("class", "btn btn-danger")
      resetButton
    }

    val enableIsolatedNodes = document.createElement("input").asInstanceOf[Input]
    val enableIsolatedDiv = wrappedInColumn(MdN(5)) {
      val formDiv = document.createElement("div").asInstanceOf[Div]
      formDiv.setAttribute("class", "form-check")

      enableIsolatedNodes.`type` = "checkbox"
      enableIsolatedNodes.checked = false
      enableIsolatedNodes.disabled = true
      enableIsolatedNodes.id = "showIsolated"
      enableIsolatedNodes.setAttribute("class", "form-check-input")

      val enableIsolatedNodesLabel = document.createElement("label").asInstanceOf[Label]
      enableIsolatedNodesLabel.textContent = "Include threads with no lock-relations"
      enableIsolatedNodesLabel.htmlFor = enableIsolatedNodes.id
      enableIsolatedNodesLabel.setAttribute("class", "form-check-label")
      enableIsolatedNodesLabel.style = "padding: 1.5em"

      formDiv.appendChild(enableIsolatedNodesLabel)
      formDiv.appendChild(enableIsolatedNodes)
      formDiv
    }

    def computeAndRenderResult(): Unit = {
      val reportResult = processReport(inputTextNode.value, enableIsolatedNodes.checked)
      val dotReport    = reportResult.merge

      val newState = if (reportResult.isLeft) {
        outTextNode.setAttribute("class", "col-sm bg-danger")
        State.update(_.copy(previousSuccessfulTransformation = false))
      } else {
        outTextNode.setAttribute("class", "col-sm bg-success")

        val vizOptions = Dynamic.literal(engine = State.getCurrent.renderEngine)

        viz.renderSVGElement(dotReport, vizOptions).toFuture.foreach { graphElement =>
          val prevResultTree = resultsDiv.childNodes
          (0 until prevResultTree.length).foreach { n =>
            val childNode = prevResultTree(n)
            resultsDiv.removeChild(childNode)
          }

          resultsDiv.appendChild(graphElement)
        }

        State.update(_.copy(previousSuccessfulTransformation = true))
      }

      outTextNode.textContent = dotReport
      analyzeButton.disabled = true
      inputTextNode.disabled = true
      enableIsolatedNodes.disabled = !newState.previousSuccessfulTransformation
    }

    val renderModeSelectorDiv = wrappedInColumn(MdN(6)) {
      val formDiv = document.createElement("div").asInstanceOf[Div]
      formDiv.setAttribute("class", "form-check")

      val buttonsGroupDiv = document.createElement("div").asInstanceOf[Div]
      buttonsGroupDiv.style = "padding: 1em"
      buttonsGroupDiv.setAttribute("class", "btn-group btn-group-toggle")
      buttonsGroupDiv.setAttribute("data-toggle", "buttons")
      buttonsGroupDiv.id = "renderSelector"

      val label = document.createElement("label").asInstanceOf[Label]
      label.setAttribute("class", "form-check-label")
      label.htmlFor = buttonsGroupDiv.id
      label.textContent = "Graphviz render mode:"

      def addButton(option: String, active: Boolean = false): Unit = {
        val entry = document.createElement("label").asInstanceOf[Label]
        entry.setAttribute("class", "btn btn-secondary" + (if (active) " active" else ""))
        val entryInput = document.createElement("input").asInstanceOf[Input]
        entryInput.`type` = "radio"
        entryInput.checked = active
        val text = document.createTextNode(option)
        entry.appendChild(entryInput)
        entry.appendChild(text)
        buttonsGroupDiv.appendChild(entry)

        entry.addEventListener(
          "click",
          { (_: dom.MouseEvent) =>
            State.update(_.copy(renderEngine = option))
            if (State.getCurrent.previousSuccessfulTransformation)
              computeAndRenderResult()
          }
        )
      }

      val st = State.getCurrent
      st.availableRenderEngines.foreach { engine =>
        addButton(engine, engine == st.defaultRenderEngine)
      }

      formDiv.appendChild(label)
      formDiv.appendChild(buttonsGroupDiv)

      formDiv
    }

    analyzeButton.addEventListener("click", (e: dom.MouseEvent) => computeAndRenderResult())
    enableIsolatedNodes.addEventListener("click", (e: dom.MouseEvent) => computeAndRenderResult())

    resetButton.addEventListener(
      "click",
      (e: dom.MouseEvent) => window.location.reload(false)
    )

    textIODiv.appendChild(inputTextDiv)
    textIODiv.appendChild(outTextDiv)
    optionsDiv.appendChild(enableIsolatedDiv)
    optionsDiv.appendChild(renderModeSelectorDiv)
    actionsDiv.appendChild(analyzeDiv)
    actionsDiv.appendChild(resetDiv)
    headerDiv.appendChild(iconDiv)
    headerDiv.appendChild(textDiv)
  }
}
