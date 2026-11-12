package com.finn013.persona

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline for home-screen shortcuts: persona://open?p=<profile id>&u=<url>.
 * Same class name and URI format as Persona 0.1, so shortcuts pinned with 0.1 keep working.
 */
class BrowserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val p = Profiles.get(this, data?.getQueryParameter("p"))
        if (p == null) {
            Toast.makeText(this, "Профиль не найден", Toast.LENGTH_SHORT).show()
        } else {
            Persona.open(this, p, data?.getQueryParameter("u"))
        }
        finish()
    }

    companion object {
        fun windowUri(p: Profile, url: String?): Uri = Uri.Builder().scheme("persona").authority("open")
            .appendQueryParameter("p", p.id).apply { if (url != null) appendQueryParameter("u", url) }.build()
    }
}
