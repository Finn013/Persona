package com.finn013.persona.slots

import dagger.hilt.android.AndroidEntryPoint
import fulguris.activity.ProfileBrowserActivity

// One Fulguris browser activity per profile slot. Each one is declared in the manifest with its own
// process (":personaN") and task affinity, see com.finn013.persona.Persona.

@AndroidEntryPoint
class Slot0 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot1 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot2 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot3 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot4 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot5 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot6 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot7 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot8 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot9 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot10 : ProfileBrowserActivity()

@AndroidEntryPoint
class Slot11 : ProfileBrowserActivity()
