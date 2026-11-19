package com.example.data.catalog

import com.example.domain.model.InsertionMethod
import com.example.domain.model.MissionRisk
import com.example.domain.model.MissionScope
import com.example.domain.model.MissionTemplate
import com.example.domain.model.OperatorSpecialty
import com.example.domain.model.TheaterCampaign
import com.example.domain.model.TheaterRegion

/**
 * Session 14b: campaigns V to X in six new theaters (item 48), and a larger single-mission pool with more
 * one-squad High and Extreme missions (items 50 and 88). Each campaign is harder than the one before it.
 */
internal object TheaterExpansion {
    private val A = OperatorSpecialty.ASSAULT
    private val R = OperatorSpecialty.RECON
    private val S = OperatorSpecialty.SIGNALS
    private val B = OperatorSpecialty.BREACHER
    private val M = OperatorSpecialty.MEDIC
    private val G = InsertionMethod.GROUND
    private val H = InsertionMethod.HELICOPTER
    private val AB = InsertionMethod.AIRBORNE

    /** MOS and insertion tags for the new field missions, merged into the catalog's tag table. */
    val tags: Map<String, Pair<List<OperatorSpecialty>, List<InsertionMethod>>> = mapOf(
        "camp_sahr_1" to (listOf(R) to listOf(G, H, AB)),
        "camp_sahr_3" to (listOf(A, M) to listOf(G, H)),
        "camp_sahr_4" to (listOf(A, B) to listOf(G, H, AB)),
        "camp_verdance_1" to (listOf(R, M) to listOf(G, H)),
        "camp_verdance_2" to (listOf(S) to listOf(G, H)),
        "camp_verdance_4" to (listOf(A, R) to listOf(G, H)),
        "camp_meridian_1" to (listOf(B, A) to listOf(G, H)),
        "camp_meridian_3" to (listOf(R, S) to listOf(G, H)),
        "camp_meridian_4" to (listOf(A, B, M) to listOf(G, H)),
        "camp_karst_1" to (listOf(R) to listOf(G, H, AB)),
        "camp_karst_2" to (listOf(M) to listOf(G, H)),
        "camp_karst_4" to (listOf(A, S) to listOf(G, H, AB)),
        "camp_talon_1" to (listOf(R) to listOf(G, H)),
        "camp_talon_2" to (listOf(B) to listOf(G, H)),
        "camp_talon_4" to (listOf(S, A) to listOf(G, H, AB)),
        "camp_talon_5" to (listOf(A, B, M) to listOf(G, H, AB)),
        "camp_ironhold_1" to (listOf(R, S) to listOf(G, H, AB)),
        "camp_ironhold_2" to (listOf(B, A) to listOf(G, H)),
        "camp_ironhold_4" to (listOf(A, M) to listOf(G, H)),
        "camp_ironhold_5" to (listOf(A, B, M) to listOf(G, H, AB)),
        "msn_x_dune_convoy" to (emptyList<OperatorSpecialty>() to listOf(G)),
        "msn_x_canopy_recon" to (listOf(R) to listOf(G, H)),
        "msn_x_rooftop_snatch" to (listOf(R, A) to listOf(G, H)),
        "msn_x_pass_ambush" to (listOf(A) to listOf(G, H, AB)),
        "msn_x_reef_sabotage" to (listOf(B) to listOf(G, H)),
        "msn_x_well_bombing" to (listOf(B, M) to listOf(G, H)),
        "msn_x_temple_relay" to (listOf(S) to listOf(G, H)),
        "msn_x_tunnel_clear" to (listOf(B, A) to listOf(G)),
        "msn_x_summit_array" to (listOf(S, A) to listOf(G, H, AB)),
        "msn_x_atoll_strike" to (listOf(A, M) to listOf(G, H, AB)),
        "msn_x_gate_breach" to (listOf(A, B, M) to listOf(G, H)),
        "msn_x_crash_recovery" to (listOf(M, R) to listOf(G, H, AB))
    )

    private fun field(
        id: String, title: String, theater: TheaterRegion, risk: MissionRisk, success: Int, briefing: String,
        seconds: Int, power: Int, focus: String, supply: Double, cr: Double, sup: Double, intel: Double,
        prs: Double, xp: Int, feed: List<String>, campaignId: String? = null, stage: Int? = null,
        night: Boolean = false, tag: String? = null, platoon: Boolean = false
    ): MissionTemplate {
        // Item 108: platoon stages take two squads (8 to 12 soldiers) and pay about 1.6x
        val pay = if (platoon) PLATOON_REWARD_MULT else 1.0
        return MissionTemplate(
            id = id, title = if (platoon) "$title (Platoon Op)" else title, theater = theater, risk = risk, baseSuccessRatePct = success,
            briefing = briefing, durationSeconds = seconds, minOperators = if (platoon) 8 else 3, maxOperators = if (platoon) 12 else 6,
            recommendedPower = power, primaryFocus = focus, isNightOp = night, supplyCost = if (platoon) supply * 1.4 else supply,
            rewardCredits = cr * pay, rewardSupplies = sup * pay, rewardIntel = intel * pay, rewardPrestige = prs * pay,
            rewardXp = (xp * pay).toInt(),
            commsFeed = feed, campaignId = campaignId, campaignStageOrder = stage,
            isRotatingStandalone = campaignId == null, contractTag = tag,
            requiredSquads = if (platoon) 2 else 1
        )
    }

    private const val PLATOON_REWARD_MULT = 1.6

    private fun staff(
        id: String, title: String, theater: TheaterRegion, risk: MissionRisk, success: Int, briefing: String,
        seconds: Int, power: Int, focus: String, supply: Double, cr: Double, sup: Double, intel: Double,
        prs: Double, xp: Int, feed: List<String>, campaignId: String? = null, stage: Int? = null,
        tag: String? = null
    ) = MissionTemplate(
        id = id, title = title, theater = theater, risk = risk, scope = MissionScope.STAFF_DIPLOMATIC,
        baseSuccessRatePct = success, briefing = briefing, durationSeconds = seconds, minOperators = 1,
        maxOperators = 3, recommendedPower = power, primaryFocus = focus, supplyCost = supply,
        rewardCredits = cr, rewardSupplies = sup, rewardIntel = intel, rewardPrestige = prs, rewardXp = xp,
        commsFeed = feed, campaignId = campaignId, campaignStageOrder = stage,
        isRotatingStandalone = campaignId == null, contractTag = tag
    )

    private val SAHR = TheaterRegion.SAHR_EXPANSE
    private val VERD = TheaterRegion.VERDANCE_BASIN
    private val MERI = TheaterRegion.MERIDIAN_CITY
    private val KARST = TheaterRegion.KARST_PEAKS
    private val TALON = TheaterRegion.TALON_ARCHIPELAGO
    private val IRON = TheaterRegion.IRONHOLD

    val campaignMissions: List<MissionTemplate> = listOf(
        // Campaign V: Sahr Expanse (Moderate to High)
        field("camp_sahr_1", "Stage 1: Dune Line Recon", SAHR, MissionRisk.MODERATE, 58,
            "Scout the dune line ahead of the main force and mark the enemy's water points and fuel caches.",
            100, 84, "Campaign V • Desert Recon", 56.0, 360.0, 130.0, 55.0, 28.0, 280,
            listOf("Teams moving out before first light to beat the heat.", "Fuel cache spotted under camouflage netting.", "Dune line mapped. Assault route chosen."),
            "camp_sahr", 1),
        staff("camp_sahr_2", "Stage 2: Oasis Water Accord", SAHR, MissionRisk.MODERATE, 64,
            "HQ staff negotiate with the oasis elders for water rights and safe passage through their wells.",
            70, 76, "Campaign V • Tribal Diplomacy", 38.0, 300.0, 90.0, 70.0, 40.0, 240,
            listOf("Staff arrive at the oasis council under a white flag.", "Elders agree to share the wells in exchange for medical aid.", "Water accord signed. The column can move."),
            "camp_sahr", 2),
        field("camp_sahr_3", "Stage 3: Salt Flat Convoy", SAHR, MissionRisk.HIGH, 46,
            "Escort the water and fuel convoy across open salt flats with no cover and enemy technicals circling.",
            160, 104, "Campaign V • Convoy Escort", 90.0, 600.0, 200.0, 95.0, 46.0, 430,
            listOf("Convoy rolling. Dust plume visible for miles.", "Technicals closing from the east; gun trucks engaging.", "Convoy through. Forward base supplied."),
            "camp_sahr", 3),
        field("camp_sahr_4", "Stage 4: Mirage Fortress Assault", SAHR, MissionRisk.EXTREME, 34,
            "Storm the desert fortress that controls the only paved road through the Expanse.",
            220, 122, "Campaign V Finale • Assault", 120.0, 940.0, 290.0, 160.0, 68.0, 590,
            listOf("Breach charges set on the outer wall.", "Clearing the keep room by room under mortar fire.", "Fortress taken. The Sahr Expanse is open."),
            "camp_sahr", 4),

        // Campaign VI: Verdance Basin (Moderate to Extreme)
        field("camp_verdance_1", "Stage 1: River Landing", VERD, MissionRisk.MODERATE, 54,
            "Land by boat on the jungle river and set up a casualty collection point for the push inland.",
            110, 92, "Campaign VI • Rescue & Trauma", 60.0, 400.0, 140.0, 60.0, 30.0, 300,
            listOf("Boats beached under the canopy.", "Heat casualties treated at the collection point.", "Landing zone secure. Patrols pushing inland."),
            "camp_verdance", 1),
        field("camp_verdance_2", "Stage 2: Canopy Listening Post", VERD, MissionRisk.HIGH, 46,
            "Climb into the canopy and plant listening gear over the enemy's jungle trail network.",
            150, 106, "Campaign VI • Signals Intel", 84.0, 560.0, 170.0, 120.0, 44.0, 420,
            listOf("Climbers in the canopy at 40 meters.", "Antenna hidden; first intercepts coming in.", "Trail network mapped from the radio traffic."),
            "camp_verdance", 2, night = true),
        staff("camp_verdance_3", "Stage 3: Village Council", VERD, MissionRisk.HIGH, 58,
            "HQ staff meet the river villages to win their trust and their guides before the final push.",
            80, 88, "Campaign VI • Hearts & Minds", 44.0, 380.0, 100.0, 90.0, 50.0, 300,
            listOf("Staff share a meal with the village headmen.", "Guides volunteer after the medics treat the children.", "Villages side with the coalition."),
            "camp_verdance", 3),
        field("camp_verdance_4", "Stage 4: Green Veil Strike", VERD, MissionRisk.EXTREME, 34,
            "Hit the enemy's hidden jungle headquarters under the canopy before it can disperse.",
            230, 124, "Campaign VI Finale • Night Assault", 122.0, 960.0, 290.0, 170.0, 70.0, 600,
            listOf("Guides lead the teams through the swamp at night.", "Contact at the bunker line; close-quarters fight.", "Headquarters destroyed. The Basin is ours."),
            "camp_verdance", 4, night = true),

        // Campaign VII: Meridian City (High to Extreme)
        field("camp_meridian_1", "Stage 1: Outer District Breach", MERI, MissionRisk.HIGH, 46,
            "Breach the barricades on the city's edge and open a lane for the follow-on force.",
            160, 110, "Campaign VII • Urban Breach", 92.0, 640.0, 200.0, 100.0, 48.0, 450,
            listOf("Sappers clearing the first barricade.", "Snipers in the apartment blocks; smoke laid.", "Outer district held. Lane open."),
            "camp_meridian", 1),
        staff("camp_meridian_2", "Stage 2: City Hall Ceasefire", MERI, MissionRisk.HIGH, 56,
            "HQ staff broker a ceasefire with the mayor's office so civilians can leave the central districts.",
            90, 92, "Campaign VII • Coalition Mandate", 48.0, 420.0, 110.0, 110.0, 56.0, 320,
            listOf("Staff enter City Hall with an allied escort.", "Corridors agreed for the civilian evacuation.", "Ceasefire holds. Central districts clearing out."),
            "camp_meridian", 2),
        field("camp_meridian_3", "Stage 3: Glass Tower Overwatch", MERI, MissionRisk.HIGH, 42,
            "Take the tallest tower downtown and run the city's fight from the rooftop with eyes and radios.",
            180, 118, "Campaign VII • Recon & Signals", 100.0, 720.0, 210.0, 150.0, 54.0, 490,
            listOf("Stairwell cleared floor by floor to the roof.", "Observers calling targets across six blocks.", "Downtown under observation. Final assault planned."),
            "camp_meridian", 3, night = true),
        field("camp_meridian_4", "Stage 4: Glass Canyon", MERI, MissionRisk.EXTREME, 32,
            "Assault the fortified government quarter street by street through the canyon of high-rises.",
            250, 130, "Campaign VII Finale • Assault & Trauma", 128.0, 1050.0, 310.0, 180.0, 76.0, 640,
            listOf("Squads stacked at the canyon entrance.", "Heavy fire from three sides; medics working nonstop.", "Government quarter taken. Meridian City is free."),
            "camp_meridian", 4, platoon = true),

        // Campaign VIII: Karst Peaks (High to Extreme)
        field("camp_karst_1", "Stage 1: Thin Air Ascent", KARST, MissionRisk.HIGH, 44,
            "Climb the high pass on foot and set observation posts over the enemy's mountain supply road.",
            170, 116, "Campaign VIII • Mountain Recon", 96.0, 680.0, 220.0, 120.0, 50.0, 470,
            listOf("Teams acclimatizing at the base camp.", "Ropes fixed on the icy last pitch.", "Observation posts dug in above the road."),
            "camp_karst", 1),
        field("camp_karst_2", "Stage 2: Avalanche Rescue", KARST, MissionRisk.HIGH, 46,
            "Dig out an allied patrol buried by an avalanche before the enemy reaches them.",
            150, 112, "Campaign VIII • Rescue & Trauma", 90.0, 620.0, 230.0, 90.0, 56.0, 450,
            listOf("Probe lines sweeping the debris field.", "Three soldiers found alive; medics treating hypothermia.", "Patrol recovered. Enemy turned back at the ridge."),
            "camp_karst", 2),
        staff("camp_karst_3", "Stage 3: Monastery Talks", KARST, MissionRisk.HIGH, 54,
            "HQ staff ask the mountain monastery for use of their pass and their old radio mast.",
            90, 96, "Campaign VIII • Allied Diplomacy", 50.0, 440.0, 120.0, 120.0, 60.0, 340,
            listOf("Staff climb to the monastery gate.", "Abbot agrees on the condition of no fighting inside the walls.", "Pass and radio mast placed at our disposal."),
            "camp_karst", 3),
        field("camp_karst_4", "Stage 4: Eagle's Nest", KARST, MissionRisk.EXTREME, 30,
            "Seize the fortified artillery position on the summit that dominates the whole range.",
            260, 134, "Campaign VIII Finale • Assault & Signals", 132.0, 1120.0, 330.0, 200.0, 80.0, 670,
            listOf("Teams moving up the north face in a whiteout.", "Jammers down; guns engaged at point-blank range.", "Summit taken. The Karst Peaks are secure."),
            "camp_karst", 4, night = true, platoon = true),

        // Campaign IX: Talon Archipelago (High to Extreme, five stages)
        field("camp_talon_1", "Stage 1: Island Hop Recon", TALON, MissionRisk.HIGH, 44,
            "Scout the outer islands by small boat and find which ones hold radar and missile sites.",
            160, 118, "Campaign IX • Coastal Recon", 94.0, 700.0, 220.0, 140.0, 52.0, 480,
            listOf("Boats running dark between the islands.", "Radar site confirmed on the third island.", "Island chain mapped."),
            "camp_talon", 1, night = true),
        field("camp_talon_2", "Stage 2: Reef Mine Clearance", TALON, MissionRisk.HIGH, 42,
            "Clear the mines on the reef channel so the landing craft can get through.",
            170, 120, "Campaign IX • EOD", 98.0, 720.0, 240.0, 130.0, 54.0, 490,
            listOf("Divers and engineers marking mines in the channel.", "Two mines detonated in place.", "Channel clear for landing craft."),
            "camp_talon", 2),
        staff("camp_talon_3", "Stage 3: Fishermen's Compact", TALON, MissionRisk.HIGH, 54,
            "HQ staff meet the island fishing fleets to keep them out of the strike zone and learn the tides.",
            90, 98, "Campaign IX • Coalition Mandate", 52.0, 460.0, 130.0, 130.0, 62.0, 350,
            listOf("Staff board the fleet's flagship trawler.", "Fleet agrees to stay in harbor for three days.", "Tide tables and safe channels shared."),
            "camp_talon", 3),
        field("camp_talon_4", "Stage 4: Radar Island Raid", TALON, MissionRisk.EXTREME, 32,
            "Raid the radar island and blind the archipelago's air defense network.",
            230, 130, "Campaign IX • Signals Strike", 124.0, 1040.0, 310.0, 210.0, 76.0, 630,
            listOf("Landing on the rocks below the radar dome.", "Signals team pulling data while the perimeter holds.", "Radar down. Air defenses blind."),
            "camp_talon", 4, night = true),
        field("camp_talon_5", "Stage 5: Broken Chain", TALON, MissionRisk.EXTREME, 30,
            "Storm the island fortress at the center of the chain while the air defenses are blind.",
            260, 136, "Campaign IX Finale • Task Force Assault", 134.0, 1180.0, 340.0, 210.0, 84.0, 690,
            listOf("Helicopters inbound low over the water.", "Fortress gates breached; heavy close-quarters fighting.", "Fortress taken. The chain is broken."),
            "camp_talon", 5, platoon = true),

        // Campaign X: Ironhold (High to Extreme, five stages, the final campaign)
        field("camp_ironhold_1", "Stage 1: Wall Recon", IRON, MissionRisk.HIGH, 42,
            "Slip close to Ironhold's outer wall and find the weak points in the capital's defenses.",
            180, 124, "Campaign X • Recon & Signals", 100.0, 760.0, 240.0, 170.0, 56.0, 510,
            listOf("Teams in the drainage culverts under the wall.", "Sensor gaps found along the north wall.", "Weak points mapped. Breach plan drafted."),
            "camp_ironhold", 1, night = true),
        field("camp_ironhold_2", "Stage 2: Gatehouse Breach", IRON, MissionRisk.EXTREME, 32,
            "Blow the north gatehouse and hold it open for the main force.",
            230, 132, "Campaign X • Breach & Assault", 126.0, 1080.0, 320.0, 190.0, 78.0, 650,
            listOf("Charges placed on the gatehouse doors.", "Gate down; counterattack from the inner district.", "Gatehouse held. Main force pouring in."),
            "camp_ironhold", 2),
        staff("camp_ironhold_3", "Stage 3: Surrender Terms", IRON, MissionRisk.HIGH, 52,
            "HQ staff offer surrender terms to the garrison commanders to split them from the regime.",
            100, 104, "Campaign X • Coalition Mandate", 56.0, 520.0, 140.0, 160.0, 70.0, 380,
            listOf("Staff cross the line under a flag of truce.", "Two garrison commanders accept the terms.", "Half the garrison stands down."),
            "camp_ironhold", 3),
        field("camp_ironhold_4", "Stage 4: Hospital District", IRON, MissionRisk.EXTREME, 32,
            "Clear the hospital district without harming the patients still inside.",
            240, 134, "Campaign X • Rescue & Assault", 128.0, 1100.0, 330.0, 190.0, 82.0, 660,
            listOf("Squads entering the hospital grounds slowly.", "Enemy using the wards for cover; medics evacuating patients.", "District clear. Patients safe."),
            "camp_ironhold", 4),
        field("camp_ironhold_5", "Stage 5: Last Light", IRON, MissionRisk.EXTREME, 28,
            "Assault the citadel at the heart of Ironhold and end the war.",
            300, 140, "Campaign X Finale • Task Force Valor", 140.0, 1400.0, 380.0, 240.0, 100.0, 760,
            listOf("Every team committed. The citadel is lit up with fire.", "Breaching the inner keep under maximum pressure.", "Citadel taken. All ten campaigns cleared!"),
            "camp_ironhold", 5, night = true, platoon = true)
    )

    /** More single missions for the rotating board, including one-squad High and Extreme missions. */
    val singleMissions: List<MissionTemplate> = listOf(
        field("msn_x_dune_convoy", "Op: Dune Convoy", SAHR, MissionRisk.MODERATE, 56,
            "Escort a supply convoy across the desert to an allied outpost.",
            110, 82, "Convoy Escort", 54.0, 340.0, 150.0, 40.0, 24.0, 270,
            listOf("Convoy formed up at the wadi.", "Sandstorm slowing the column.", "Outpost resupplied."), tag = "SUPPLY RUN"),
        field("msn_x_canopy_recon", "Op: Canopy Watch", VERD, MissionRisk.MODERATE, 56,
            "Watch a jungle trail for two days and count what moves on it.",
            120, 84, "Recon & Stealth", 52.0, 330.0, 110.0, 80.0, 24.0, 270,
            listOf("Hide site built off the trail.", "Porters and mules moving south at dusk.", "Trail traffic logged."), night = true),
        field("msn_x_rooftop_snatch", "Op: Rooftop Snatch", MERI, MissionRisk.HIGH, 44,
            "Grab an enemy courier from a rooftop safe house in the city before he can burn his files.",
            150, 108, "Recon & Assault", 84.0, 560.0, 170.0, 120.0, 44.0, 420,
            listOf("Teams on the neighboring roofs.", "Breach; courier caught with the files.", "Courier and files on the way to HQ."), night = true, tag = "SNATCH & GRAB"),
        field("msn_x_pass_ambush", "Op: High Pass Ambush", KARST, MissionRisk.HIGH, 44,
            "Ambush an enemy supply column in a narrow mountain pass.",
            150, 108, "Assault & Firepower", 84.0, 580.0, 190.0, 90.0, 44.0, 420,
            listOf("Ambush line set on the ridge.", "Lead and rear vehicles hit; column trapped.", "Column destroyed. Teams pulling back up the ridge.")),
        field("msn_x_reef_sabotage", "Op: Reef Sabotage", TALON, MissionRisk.HIGH, 42,
            "Plant charges on an enemy fuel jetty on the reef and slip away before dawn.",
            160, 110, "EOD & Breach", 88.0, 600.0, 180.0, 100.0, 46.0, 430,
            listOf("Swimmers at the jetty pilings.", "Charges set on timers.", "Jetty burning. Teams clear."), night = true),
        field("msn_x_well_bombing", "Op: Poisoned Well", SAHR, MissionRisk.HIGH, 44,
            "Secure a village well the enemy has rigged with explosives and treat the sick villagers.",
            150, 104, "EOD & Trauma", 82.0, 540.0, 200.0, 80.0, 48.0, 410,
            listOf("Perimeter set around the village.", "Device found and defused at the wellhead.", "Village water safe; sick treated.")),
        staff("msn_x_temple_relay", "Diplomatic: Temple Relay Talks", VERD, MissionRisk.MODERATE, 62,
            "HQ staff ask the temple priests to let a radio relay go up on their bell tower.",
            70, 72, "Diplomatic Liaison", 36.0, 280.0, 80.0, 70.0, 34.0, 230,
            listOf("Staff received at the temple gate.", "Priests agree in exchange for a new generator.", "Relay going up on the bell tower.")),
        staff("msn_x_port_customs", "Diplomatic: Port Customs Accord", TALON, MissionRisk.HIGH, 54,
            "HQ staff settle a customs dispute that is holding coalition cargo in the island port.",
            90, 92, "Coalition Mandate", 46.0, 400.0, 120.0, 100.0, 52.0, 310,
            listOf("Staff meet the port authority.", "Cargo inspection terms agreed.", "Coalition cargo released.")),
        field("msn_x_tunnel_clear", "Op: Tunnel Rats", MERI, MissionRisk.EXTREME, 32,
            "Clear an enemy tunnel network under the city's old quarter. No air support down there.",
            220, 122, "Breach & Assault", 118.0, 900.0, 270.0, 160.0, 66.0, 580,
            listOf("Teams dropping into the tunnel entrance.", "Booby traps and close fighting in the dark.", "Tunnel network collapsed behind the teams."), tag = "UNDERGROUND"),
        field("msn_x_summit_array", "Op: Summit Array", KARST, MissionRisk.EXTREME, 32,
            "Destroy an enemy radar array on a mountain summit in a storm that grounds the helicopters home.",
            230, 124, "Signals Strike & Assault", 120.0, 920.0, 280.0, 180.0, 68.0, 590,
            listOf("Teams climbing in the storm.", "Array guards engaged at the summit.", "Array destroyed. Teams descending."), night = true, tag = "HIGH ALTITUDE"),
        field("msn_x_atoll_strike", "Op: Atoll Strike", TALON, MissionRisk.EXTREME, 34,
            "Raid a missile site on a remote atoll and get off before the enemy fleet arrives.",
            220, 120, "Task Force Assault", 116.0, 880.0, 260.0, 150.0, 64.0, 570,
            listOf("Landing on the atoll's lagoon side.", "Launchers destroyed; enemy fleet spotted on radar.", "Teams extracted with minutes to spare."), tag = "FLAGSHIP STRIKE"),
        field("msn_x_gate_breach", "Op: Iron Gate", IRON, MissionRisk.EXTREME, 30,
            "Breach a fortified checkpoint on the road into Ironhold and hold it for the night.",
            240, 126, "Breach & Assault", 122.0, 940.0, 280.0, 160.0, 70.0, 600,
            listOf("Charges on the checkpoint barriers.", "Holding the checkpoint against counterattacks all night.", "Checkpoint held at dawn."), night = true),
        field("msn_x_crash_recovery", "Op: Downed Bird", VERD, MissionRisk.EXTREME, 34,
            "Reach a downed allied helicopter deep in the jungle and bring the crew home alive.",
            210, 118, "Rescue & Trauma", 112.0, 860.0, 300.0, 120.0, 72.0, 560,
            listOf("Teams racing the enemy to the crash site.", "Crew found; medics stabilizing the pilot.", "Crew extracted. Everyone coming home."), tag = "PERSONNEL RECOVERY")
    )

    val campaigns: List<TheaterCampaign> = listOf(
        campaign("camp_sahr", 5, "Campaign V: Op Iron Mirage", SAHR, "Desert • Moderate → Extreme",
            "Map the dune line, win the oasis water rights (HQ Staff), run the salt flat convoy, and storm the Mirage Fortress.",
            900.0, 260.0, 150.0, 85.0, "+10% Op Supplies",
            listOf("camp_sahr_1", "camp_sahr_2", "camp_sahr_3", "camp_sahr_4")),
        campaign("camp_verdance", 6, "Campaign VI: Op Green Veil", VERD, "Jungle • Moderate → Extreme",
            "Land on the river, tap the canopy trails, win the villages (HQ Staff), and hit the hidden jungle headquarters.",
            1050.0, 290.0, 180.0, 95.0, "-8% Mission Time",
            listOf("camp_verdance_1", "camp_verdance_2", "camp_verdance_3", "camp_verdance_4")),
        campaign("camp_meridian", 7, "Campaign VII: Op Glass Canyon", MERI, "Urban • High → Extreme",
            "Breach the outer district, broker a ceasefire (HQ Staff), take the Glass Tower, and assault the government quarter.",
            1200.0, 320.0, 210.0, 110.0, "+2% Field Op Pass Chance",
            listOf("camp_meridian_1", "camp_meridian_2", "camp_meridian_3", "camp_meridian_4")),
        campaign("camp_karst", 8, "Campaign VIII: Op Thin Air", KARST, "High Mountain • High → Extreme",
            "Climb the pass, dig out an avalanche patrol, win the monastery (HQ Staff), and seize the Eagle's Nest.",
            1350.0, 350.0, 240.0, 125.0, "-20% Medbay Recovery Time",
            listOf("camp_karst_1", "camp_karst_2", "camp_karst_3", "camp_karst_4")),
        campaign("camp_talon", 9, "Campaign IX: Op Broken Chain", TALON, "Archipelago • High → Extreme",
            "Scout the islands, clear the reef, keep the fishing fleets clear (HQ Staff), blind the radar, and storm the fortress.",
            1550.0, 400.0, 280.0, 140.0, "+2 Survival on Field Ops",
            listOf("camp_talon_1", "camp_talon_2", "camp_talon_3", "camp_talon_4", "camp_talon_5")),
        campaign("camp_ironhold", 10, "Campaign X: Op Last Light", IRON, "Final • High → Extreme",
            "Scout the wall, breach the gatehouse, split the garrison (HQ Staff), clear the hospital district, and take the citadel.",
            2000.0, 500.0, 350.0, 200.0, "+5% Op Credits, Supplies & Intel",
            listOf("camp_ironhold_1", "camp_ironhold_2", "camp_ironhold_3", "camp_ironhold_4", "camp_ironhold_5"),
            extraReward = " • Unlocks Full Replay")
    )

    private fun campaign(
        id: String, order: Int, title: String, theater: TheaterRegion, difficulty: String, summary: String,
        cr: Double, sup: Double, intel: Double, prs: Double, perk: String, stages: List<String>,
        extraReward: String = ""
    ) = TheaterCampaign(
        id = id, order = order, title = title, theater = theater, difficultyLabel = difficulty, summary = summary,
        rewardSummary = "+${cr.toInt()} CR • +${sup.toInt()} SUP • +${intel.toInt()} INT • +${prs.toInt()} PRS • Theater Perk: $perk$extraReward",
        completionBonusCredits = cr, completionBonusSupplies = sup, completionBonusIntel = intel,
        completionBonusPrestige = prs, theaterBuffLabel = perk, stageMissionIds = stages
    )
}
