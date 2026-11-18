package com.example.domain

import com.example.domain.model.MissionTemplate
import com.example.domain.model.TheaterRegion
import kotlin.random.Random

/**
 * Item 50: fresh names for single (rotating) missions every time the board refreshes, like
 * "Op: Iron Lantern" or "Contract: Silent Harbor". Words fit the theater; names never repeat on one board.
 * Names come from the rotation seed, so they stay the same until the board rotates again.
 */
object MissionNameGenerator {

    private val sharedAdjectives = listOf(
        "Iron", "Silent", "Crimson", "Hollow", "Granite", "Broken", "Steel", "Midnight", "Ashen", "Burning",
        "Cold", "Distant", "Fallen", "Gilded", "Hidden", "Last", "Pale", "Quiet", "Rapid", "Sable",
        "Stone", "Swift", "Thunder", "Vigilant", "Wild", "Bitter", "Bright", "Copper", "Final", "Lone"
    )
    private val sharedNouns = listOf(
        "Lantern", "Anvil", "Spear", "Harbor", "Bastion", "Talon", "Compass", "Sentinel", "Banner", "Gauntlet",
        "Arrow", "Shield", "Watch", "Tide", "Forge", "Horizon", "Hammer", "Crown", "Ember", "Viper",
        "Raven", "Sabre", "Citadel", "Keystone", "Meridian", "Ridgeline", "Signal", "Thorn", "Vector", "Warden"
    )
    private val theaterWords: Map<TheaterRegion, Pair<List<String>, List<String>>> = mapOf(
        TheaterRegion.VERIDIAN_RIDGE to (listOf("Pine", "Timber", "Mossy", "Highland", "Green") to listOf("Pines", "Ridge", "Hollow", "Grove", "Summit")),
        TheaterRegion.KESTREL_STRAIT to (listOf("Salt", "Coastal", "Riptide", "Strait", "Tidal") to listOf("Causeway", "Breakwater", "Strait", "Shoal", "Lighthouse")),
        TheaterRegion.BOREAL_RELAY to (listOf("Frozen", "Arctic", "Polar", "Glacial", "Frost") to listOf("Glacier", "Aurora", "Tundra", "Icefield", "Relay")),
        TheaterRegion.OBSIDIAN_HARBOR to (listOf("Black", "Obsidian", "Smoke", "Dockside", "Night") to listOf("Pier", "Drydock", "Wharf", "Quay", "Breaker")),
        TheaterRegion.DIPLOMATIC_CORPS to (listOf("Allied", "Civil", "Marble", "Grand", "Neutral") to listOf("Accord", "Charter", "Envoy", "Assembly", "Concord"))
    )

    /** "Op", "Contract" or "Diplomatic": the prefix of the mission's catalog title. */
    private fun prefixOf(m: MissionTemplate): String =
        m.title.substringBefore(':', missingDelimiterValue = "Op").trim().ifBlank { "Op" }

    fun generate(m: MissionTemplate, rng: Random, taken: Set<String>): String {
        val (tAdj, tNoun) = theaterWords[m.theater] ?: (emptyList<String>() to emptyList())
        val adjectives = sharedAdjectives + tAdj + tAdj
        val nouns = sharedNouns + tNoun + tNoun
        repeat(60) {
            val name = "${prefixOf(m)}: ${adjectives.random(rng)} ${nouns.random(rng)}"
            if (name !in taken) return name
        }
        return "${prefixOf(m)}: ${adjectives.random(rng)} ${nouns.random(rng)} ${taken.size + 1}"
    }

    /** Names for every mission on the board, unique on that board, from the rotation's seed. */
    fun namesFor(missions: List<MissionTemplate>, seed: Long): Map<String, String> {
        val taken = mutableSetOf<String>()
        return missions.associate { m ->
            val name = generate(m, Random(seed xor m.id.hashCode().toLong()), taken)
            taken += name
            m.id to name
        }
    }
}

/** The names currently shown for generated missions; set from game state whenever it changes. */
object MissionNames {
    @Volatile
    var current: Map<String, String> = emptyMap()
}

/** What the player sees: the generated name for a rotating mission, otherwise the catalog title. */
val MissionTemplate.displayTitle: String
    get() = MissionNames.current[id] ?: title
