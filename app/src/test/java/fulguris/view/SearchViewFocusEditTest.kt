package fulguris.view

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import fulguris.SDK_VERSION
import fulguris.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deterministic unit tests for the [SearchView] focus/edit state machine.
 *
 * The address field has three states: unfocused (shows the tab label), navigation-focused
 * (label, no keyboard), and editing (URL shown, selected, keyboard + suggestions). These tests
 * pin the transitions so a regression that leaves the field stuck in the wrong state — e.g. the
 * URL not being selected the instant editing starts, or the edition not ending when the field
 * loses focus — is caught without a device.
 *
 * The view is attached to a real (Robolectric) window with a focusable sibling so focus is moved
 * with [View.requestFocus], which fires the genuine onFocusChanged callback — the same path the
 * host activity relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class SearchViewFocusEditTest {

    /** Records onEditStart / onEditEnd invocations from the host. */
    private class RecordingEditListener : SearchView.EditListener {
        var editStarts = 0
        var editEnds = 0
        override fun onEditStart() {
            editStarts++
        }
        override fun onEditEnd() {
            editEnds++
        }
    }

    private lateinit var view: SearchView
    private lateinit var listener: RecordingEditListener
    private lateinit var focusTarget: android.view.View
    private lateinit var activity: Activity

    private val url = "http://example.com/"

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context: Context = activity

        view = SearchView(context).apply {
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            setText(url)
        }
        // A second focusable sibling so focus can genuinely leave the field (and come back).
        focusTarget = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val root = FrameLayout(context).apply {
            addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(focusTarget, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        activity.setContentView(root)

        listener = RecordingEditListener()
        view.editListener = listener
    }

    private fun focusField() = view.requestFocus()
    private fun focusElsewhere() = focusTarget.requestFocus()

    private fun startEditing() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        view.onKeyDown(event.keyCode, event)
    }

    @Test
    fun `confirm key starts editing and fully selects the url`() {
        focusField()
        val length = url.length
        startEditing()

        assertThat(view.isEditing).isTrue()
        assertThat(listener.editStarts).isEqualTo(1)
        // The core regression: the URL must be fully selected the instant editing starts,
        // not only after the host's delayed guard runs. Otherwise an early keystroke
        // inserts into the URL instead of replacing it.
        assertThat(view.selectionStart).isEqualTo(0)
        assertThat(view.selectionEnd).isEqualTo(length)
    }

    @Test
    fun `confirm key up is swallowed exactly once`() {
        focusField()
        startEditing()
        assertThat(view.shouldSwallowConfirmUp()).isTrue()
        assertThat(view.shouldSwallowConfirmUp()).isFalse()
    }

    @Test
    fun `navigation focus does not start editing`() {
        focusField()
        assertThat(view.isFocusable).isTrue()
        assertThat(view.isEditing).isFalse()
        assertThat(listener.editStarts).isZero()
        assertThat(listener.editEnds).isZero()
    }

    @Test
    fun `focus loss while editing cancels and notifies edit end`() {
        focusField()
        startEditing()
        assertThat(view.isEditing).isTrue()

        focusElsewhere()

        assertThat(view.isEditing).isFalse()
        assertThat(listener.editEnds).isEqualTo(1)
    }

    @Test
    fun `focus loss while not editing does not notify edit end`() {
        focusElsewhere()
        assertThat(view.isEditing).isFalse()
        assertThat(listener.editEnds).isZero()
        assertThat(listener.editStarts).isZero()
    }

    @Test
    fun `repeated edit focus cycles stay consistent`() {
        // Mix and match: enter edit, lose focus, re-enter, lose focus — the machine must never
        // get stuck (e.g. a stale "editing" flag that blocks a later label refresh).
        repeat(5) {
            focusField()
            startEditing()
            assertThat(view.isEditing).isTrue()
            focusElsewhere()
            assertThat(view.isEditing).isFalse()
        }
        assertThat(listener.editStarts).isEqualTo(5)
        assertThat(listener.editEnds).isEqualTo(5)
    }

    @Test
    fun `two stage back first hides keyboard then cancels`() {
        focusField()
        startEditing()
        assertThat(view.isEditing).isTrue()

        // First back: hide keyboard, still editing (suggestion popup remains usable).
        view.performBackAction()
        assertThat(view.isEditing).isTrue()
        assertThat(listener.editEnds).isZero()

        // Second back: cancel the edition.
        view.performBackAction()
        assertThat(view.isEditing).isFalse()
        assertThat(listener.editEnds).isEqualTo(1)
    }

    @Test
    fun `perform back is a no-op when not editing`() {
        view.performBackAction()
        assertThat(view.isEditing).isFalse()
        assertThat(listener.editEnds).isZero()
    }

    @Test
    fun `directional key while navigating is not consumed`() {
        // While focused for navigation (not editing), a directional key must fall through so the
        // framework can move focus out of the field.
        focusField()
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        assertThat(view.onKeyDown(event.keyCode, event)).isFalse()
        assertThat(view.isEditing).isFalse()
    }
}
