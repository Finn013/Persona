package fulguris.activity

import android.app.ActivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.finn013.persona.Persona

/**
 * Persona: the Fulguris browser window of one profile.
 *
 * Concrete subclasses (com.finn013.persona.slots.SlotN) are declared in the manifest, each in its
 * own process and task, so a profile has its own tabs, sessions, history and WebView data.
 */
abstract class ProfileBrowserActivity : MainActivity() {

    private var stripe: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addProfileMark()
        if (savedInstanceState == null) Persona.current?.let {
            android.widget.Toast.makeText(this, "Профиль: ${it.name}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        stripe?.bringToFront()
    }

    /** Thin bar in the profile colour along the top edge (below the status bar), never intercepts touches. */
    private fun addProfileMark() {
        val p = Persona.current ?: return
        val density = resources.displayMetrics.density
        val holder = FrameLayout(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val bar = View(this).apply { setBackgroundColor(p.color) }
        holder.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (4 * density).toInt(), Gravity.TOP))
        ViewCompat.setOnApplyWindowInsetsListener(holder) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            v.translationY = top.toFloat()
            insets
        }
        addContentView(holder, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        stripe = holder
    }

    /** Recents: "Profile · page title" in the profile colour. */
    @Suppress("DEPRECATION")
    override fun setTaskDescription(taskDescription: ActivityManager.TaskDescription?) {
        val p = Persona.current
        if (p == null || taskDescription == null) {
            super.setTaskDescription(taskDescription)
            return
        }
        val label = taskDescription.label?.toString()
        val text = if (label.isNullOrBlank() || label == p.name) p.name else "${p.name} · $label"
        super.setTaskDescription(ActivityManager.TaskDescription(text, taskDescription.icon, p.color or 0xFF000000.toInt()))
    }
}
