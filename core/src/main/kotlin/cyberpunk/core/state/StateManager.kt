package cyberpunk.core.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.viewport.Viewport
import cyberpunk.core.transition.Transition
import cyberpunk.core.transition.TransitionFBO

/**
 * Initializes the [StateManager] with it a [Camera] and [Viewport].
 * @param camera   camera that will use a virtual resolution.
 * @param viewport viewport that will adapt the game screen to the physical devices.
 */
class StateManager(val camera: Camera, val viewport: Viewport) {

  /**
   * The currently running [State]. May be null.
   */
  private var currentState: State? = null

  /**
   * The next [State]. Null unless there's a [Transition] ongoing.
   */
  private var nextState: State? = null

  /**
   * [Transition] used to go from one [State] to another. May be null.
   */
  private var transition: Transition? = null

  /**
   * Holds the frame buffer objects (FBOs) and flipped regions
   * that are used during transitions.
   */
  private val transitionFBO = TransitionFBO()

  /**
   * [Batch] used to render everything.
   */
  private val batch = SpriteBatch()

  /**
   * Sets a [State] to be the currently running one. Optionally, pass
   * a [Transition] to make this change more pleasant.
   * @param state       new current state.
   * @param transition  optional transition used to move from states.
   */
  @JvmOverloads
  fun go(state: State, transition: Transition? = null) {
    if (currentState == null) {
      currentState = state
      Gdx.input.inputProcessor = currentState
    } else {
      currentState?.dispose()
      Gdx.input.inputProcessor = null
      nextState = state
      this.transition = transition?.apply { start() }
    }
    resize(Gdx.graphics.width, Gdx.graphics.height)
  }

  /**
   * Updates the [currentState], if there is one.
   * Also updates any on-going [Transition].
   * @param delta time elapsed between this moment and the last update call.
   */
  fun update(delta: Float) {
    currentState?.update(delta)
    transition?.let {
      if (it.running()) it.update(delta)
    }
  }

  /**
   * Handles rendering within three case scenarios:
   * I. There's no next state - and, thus, no transition ongoing.
   * II. Transition was ongoing but just completed.
   * III. Transition is still in progress.
   */
  fun render() {
    if (nextState == null) {
      currentState?.render(batch)
    } else {
      transition?.let {
        if (it.completed()) {
          currentState = nextState
          nextState = null
          it.finish()
          currentState?.render(batch)
          Gdx.input.inputProcessor = currentState
        } else {
          transitionFBO.wrapCurrent { currentState?.render(batch) }
          transitionFBO.wrapNext { nextState?.render(batch)  }
          val (currentRegion, nextRegion) = transitionFBO.getFlippedRegions()
          transition?.render(batch, currentRegion, nextRegion)
        }
      }
    }
  }

  /**
   * Resizes the [currentState], [nextState] and this class' [Viewport].
   * Here, the [FrameBuffer] objects are disposed manually and re-defined.
   * @param width  new screen width.
   * @param height new screen height.
   */
  fun resize(width: Int, height: Int) {
    viewport.update(width, height)
    batch.projectionMatrix = camera.combined
    currentState?.resize(width, height)
    nextState?.resize(width, height)
    resizeFBOs(width, height)
  }

  /**
   * Pauses the [currentState] and the [nextState].
   */
  fun pause() {
    currentState?.pause()
    nextState?.pause()
  }

  /**
   * Resumes the [currentState] and the [nextState].
   */
  fun resume() {
    currentState?.resume()
    nextState?.resume()
  }

  /**
   * Disposes the [currentState], [nextState] and the [FrameBuffer] objects.
   */
  fun dispose() {
    currentState?.dispose()
    nextState?.dispose()
    transitionFBO.dispose()
  }

  /**
   * Resizes the [FrameBuffer] objects in every [StateManager.resize] call.
   * @param width   new screen width.
   * @param height  new screen height.
   */
  private fun resizeFBOs(width: Int, height: Int) = transitionFBO.resize(width, height)
}