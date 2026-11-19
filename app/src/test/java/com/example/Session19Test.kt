package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.*
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Session 19: soldier events (item 69). */
class Session19Test {
    private val now = 10_000_000L
    private fun soldier(id: String, rank: OperatorRank, mos: OperatorSpecialty = OperatorSpecialty.ASSAULT, status: OperatorStatus = OperatorStatus.READY) =
        GameCatalog.createPostBasicRecruit(id, mos, emptySet(), 0).copy(
            id = id, callsign = id.uppercase(), xp = rank.xpRequired, authorizedTier = rank.tier, missionsCompleted = 8,
            unlocked = true, status = status
        )
    private fun pilot(id: String) = soldier(id, OperatorRank.CW2, OperatorSpecialty.AVIATION).copy(
        warrantGrade = 2, completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.WOCS_MISSION_ID, TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID)
    )
    private fun general(id: String) = soldier(id, OperatorRank.BG, OperatorSpecialty.COMMAND, OperatorStatus.HQ_STAFF).copy(
        flagGrade = 1, hqRole = HqStaffRole.CO_COMMANDING_OFFICER
    )
    private fun ctx(ops: List<Operator>, squads: List<TacticalSquad> = emptyList(), deployments: List<ActiveDeployment> = emptyList(),
                    resources: ResourceBundle = ResourceBundle(500.0, 500.0, 100.0, 100.0)) =
        SoldierEventContext(ops, squads, deployments, listOf(FacilityState(FacilityType.AIRFIELD, 3)), resources, now)
    private fun kindsFor(op: Operator, c: SoldierEventContext) = SoldierEvents.candidates(c).filter { it.operatorId == op.id }.map { it.kind }.toSet()

    @After fun reset() { SoldierEvents.effects = CommandEventEffects() }

    @Test
    fun onlySoldiersWhoAreAroundRaiseEvents() {
        val away = soldier("away", OperatorRank.SPC)
        val leave = soldier("leave", OperatorRank.SPC, status = OperatorStatus.ON_LEAVE)
        val medbay = soldier("med", OperatorRank.SPC, status = OperatorStatus.RECOVERING)
        val dep = ActiveDeployment("d1", "msn_sf_school", listOf("away"), now, 1000L, 100, 100)
        val c = ctx(listOf(away, leave, medbay), deployments = listOf(dep))
        assertTrue(SoldierEvents.idleOperators(c).isEmpty())
        assertNull(SoldierEvents.pick(c))
        val ready = soldier("ready", OperatorRank.SPC)
        val c2 = ctx(listOf(away, leave, medbay, ready), deployments = listOf(dep))
        repeat(30) { assertEquals("ready", SoldierEvents.pick(c2, Random(it))!!.operatorId) }
        // HQ staff count as around
        assertEquals(1, SoldierEvents.idleOperators(ctx(listOf(general("g")))).size)
    }

    @Test
    fun eventsFitRankAndPosition() {
        val gen = general("g")
        val gk = kindsFor(gen, ctx(listOf(gen)))
        assertTrue(SoldierEventKind.PRESS_INTERVIEW in gk)
        for (k in listOf(SoldierEventKind.BARRACKS_INSPECTION, SoldierEventKind.TRAINING_REQUEST, SoldierEventKind.SOLDIER_OF_MONTH,
                SoldierEventKind.RANGE_TIME, SoldierEventKind.SQUAD_RIVALRY, SoldierEventKind.LT_FIRST_PLAN)) {
            assertFalse("general got $k", k in gk)
        }
        val pvt = soldier("p", OperatorRank.PFC)
        val pk = kindsFor(pvt, ctx(listOf(pvt)))
        assertTrue(SoldierEventKind.BARRACKS_INSPECTION in pk)
        for (k in listOf(SoldierEventKind.PRESS_INTERVIEW, SoldierEventKind.STAFF_CONFERENCE, SoldierEventKind.DINING_IN, SoldierEventKind.JOINT_SUMMIT)) {
            assertFalse("private got $k", k in pk)
        }
        val av = pilot("av")
        val ak = kindsFor(av, ctx(listOf(av)))
        assertTrue(SoldierEventKind.AIRCRAFT_WRITE_UP in ak && SoldierEventKind.STICK_TIME in ak)
        assertFalse(SoldierEventKind.BARRACKS_INSPECTION in ak)
        assertFalse(SoldierEventKind.RANGE_TIME in ak)
        val sgm = soldier("sgm", OperatorRank.SGM)
        assertFalse(SoldierEventKind.BARRACKS_INSPECTION in kindsFor(sgm, ctx(listOf(sgm))))
    }

    @Test
    fun petitionsComeFirstAndHandOff() {
        val p = soldier("p", OperatorRank.SSG).copy(transferRequested = true)
        val q = soldier("q", OperatorRank.SPC)
        val c = ctx(listOf(p, q))
        repeat(10) {
            val e = SoldierEvents.pick(c, Random(it))!!
            assertEquals(SoldierEventKind.TRANSFER_PETITION, e.kind)
        }
        val e = SoldierEvents.pick(c)!!
        assertEquals(PetitionAction.APPROVE_TRANSFER, SoldierEvents.apply(e, "approve", c).petitionAction)
        assertEquals(PetitionAction.DECLINE_TRANSFER, SoldierEvents.apply(e, "decline", c).petitionAction)
    }

    @Test
    fun eventOddsStayWithinTheLimit() {
        val c = ctx(listOf(soldier("a", OperatorRank.SPC)))
        val rng = Random(42)
        val fired = (1..5000).count { SoldierEvents.roll(c, rng) != null }
        val rate = fired / 5000.0
        assertTrue("rate $rate", rate in (SoldierEvents.EVENT_FIRE_CHANCE - 0.03)..(SoldierEvents.EVENT_FIRE_CHANCE + 0.03))
        assertEquals(5 * 60_000L, SoldierEvents.EVENT_INTERVAL_MILLIS)
        val e = SoldierEvents.pick(c)!!
        assertEquals(SoldierEvents.expiryMillis(), e.expiresAtMillis - e.createdAtMillis)
        assertFalse(SoldierEvents.stillValid(e, c.copy(now = e.expiresAtMillis)))
        assertTrue(SoldierEvents.stillValid(e, c))
        assertEquals(e, PendingSoldierEvent.fromCsv(e.toCsv()))
    }

    @Test
    fun everyChoiceAppliesItsResult() {
        val lt = soldier("lt", OperatorRank.LT2)
        val pvt = soldier("pvt", OperatorRank.PFC)
        val nco = soldier("nco", OperatorRank.SSG)
        val av = pilot("av")
        val gen = general("gen")
        val squadA = TacticalSquad("sqa", "Alpha", listOf("pvt", "nco", "lt", "m1", "m2"), "lt", cohesionXp = 200)
        val squadB = TacticalSquad("sqb", "Bravo", listOf("x"), "x", cohesionXp = 200)
        val x = soldier("x", OperatorRank.SGT, OperatorSpecialty.MEDIC)
        val c = ctx(listOf(lt, pvt, nco, av, gen, x), listOf(squadA, squadB))
        fun ev(kind: SoldierEventKind, op: String, target: String? = null) = PendingSoldierEvent(kind, op, target, now, now + 1_000_000L)

        // Every card's choices apply, and up-front costs match the stated cost
        for (kind in SoldierEventKind.entries) {
            val e = ev(kind, "nco", when (kind) {
                SoldierEventKind.SQUAD_TRANSFER, SoldierEventKind.WANTS_TEAM, SoldierEventKind.SQUAD_TRAINING_DAY, SoldierEventKind.SF_REHEARSAL -> "sqa"
                SoldierEventKind.OP_REQUEST -> "RECON"
                SoldierEventKind.MENTOR_PAIRING, SoldierEventKind.SQUAD_RIVALRY -> "pvt"
                SoldierEventKind.TRAINING_REQUEST -> "msn_air_assault_school"
                else -> null
            })
            for (choice in SoldierEvents.card(e, c).choices) {
                val out = SoldierEvents.apply(e, choice.id, c, Random(1))
                val (cr, sup) = SoldierEvents.choiceCost(kind, choice.id)
                if (out.petitionAction == null && out.banner.isNotBlank() && (cr > 0 || sup > 0)) {
                    assertTrue("$kind/${choice.id} credits", out.creditsDelta <= -cr + 0.001 || out.suppliesDelta <= -sup + 0.001)
                }
            }
        }

        // Specific results
        val train = SoldierEvents.apply(ev(SoldierEventKind.TRAINING_REQUEST, "pvt", "msn_air_assault_school"), "approve", c)
        assertTrue(train.operatorUpdates.single().eventBuffs.schoolDiscountUntilMillis > now)
        val school = GameCatalog.missions.first { it.id == "msn_air_assault_school" }
        assertEquals(0.75, SoldierEvents.schoolDiscountFactor(school, train.operatorUpdates, now), 0.001)

        val move = SoldierEvents.apply(ev(SoldierEventKind.SQUAD_TRANSFER, "pvt", "sqb"), "approve", c)
        assertFalse("pvt" in move.squadUpdates.first { it.id == "sqa" }.memberIds)
        assertTrue("pvt" in move.squadUpdates.first { it.id == "sqb" }.memberIds)

        val opReq = SoldierEvents.apply(ev(SoldierEventKind.OP_REQUEST, "pvt", "DIPLOMATIC"), "approve", c).operatorUpdates.single()
        val diplo = GameCatalog.missions.first { it.scope == MissionScope.STAFF_DIPLOMATIC }
        val combat = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        assertTrue(SoldierEvents.opRequestMet(opReq, diplo, now + 1000L))
        assertFalse(SoldierEvents.opRequestMet(opReq, combat, now + 1000L))
        assertFalse(SoldierEvents.opRequestMet(opReq, diplo, now + SoldierEvents.OP_REQUEST_WINDOW_MILLIS * 2))

        assertEquals(pvt.baseResilience + 1, SoldierEvents.apply(ev(SoldierEventKind.BARRACKS_INSPECTION, "pvt"), "approve", c).operatorUpdates.single().baseResilience)
        assertEquals(SoldierEvents.RANGE_TIME_BONUS_PCT, SoldierEvents.apply(ev(SoldierEventKind.RANGE_TIME, "pvt"), "approve", c).operatorUpdates.single().eventBuffs.rifleQualBonusPct)
        assertEquals(nco.id, SoldierEvents.apply(ev(SoldierEventKind.WANTS_TEAM, "nco", "sqa"), "approve", c).squadUpdates.single().teamLeaderId)

        val leave = SoldierEvents.apply(ev(SoldierEventKind.FAMILY_NEWS, "nco"), "approve", c).operatorUpdates.single()
        assertEquals(OperatorStatus.ON_LEAVE, leave.status)
        assertTrue(leave.recoveryUntilMillis > now)

        val recruit = SoldierEvents.apply(ev(SoldierEventKind.RECRUITING_DUTY, "nco"), "approve", c)
        assertEquals(SoldierEvents.RECRUIT_DISCOUNT_PCT, recruit.effects!!.recruitDiscountPct)
        SoldierEvents.effects = recruit.effects!!
        assertEquals(80.0, SoldierEvents.discountedRecruitCost(100.0), 0.001)

        val grounded = SoldierEvents.apply(ev(SoldierEventKind.AIRCRAFT_WRITE_UP, "av"), "ground", c).effects!!
        SoldierEvents.effects = grounded.copy(heloGroundedUntilMillis = System.currentTimeMillis() + 60_000L)
        val helo = GameCatalog.missions.first { !TacticalCalculator.isSfOperation(it) && InsertionMethod.HELICOPTER in it.allowedInsertions }
        assertEquals("Aircraft grounded for maintenance",
            TacticalCalculator.insertionBlocker(helo, InsertionMethod.HELICOPTER, listOf(pvt), av, listOf(FacilityState(FacilityType.AIRFIELD, 3))))
        SoldierEvents.effects = CommandEventEffects()

        val lead = SoldierEvents.apply(ev(SoldierEventKind.LT_FIRST_PLAN, "lt"), "approve", c).operatorUpdates.single()
        assertTrue(lead.eventBuffs.leadsNextMission)
        val hard = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.EXTREME && !it.requiresSfTeam }
        assertEquals(TacticalCalculator.missionSuccessOddsPct(hard, listOf(lt), emptyList(), emptyList(), emptyList()) - SoldierEvents.LT_PLAN_PASS_PENALTY,
            TacticalCalculator.missionSuccessOddsPct(hard, listOf(lead), emptyList(), emptyList(), emptyList()))

        assertEquals(30.0, SoldierEvents.apply(ev(SoldierEventKind.STAFF_CONFERENCE, "gen"), "approve", c).intelDelta, 0.001)
        assertEquals(0.0, SoldierEvents.apply(ev(SoldierEventKind.STAFF_CONFERENCE, "gen"), "decline", c).creditsDelta, 0.001)

        // Can't pay: nothing happens
        val broke = ctx(listOf(gen), resources = ResourceBundle())
        assertTrue(SoldierEvents.apply(ev(SoldierEventKind.DELEGATION_TOUR, "gen"), "approve", broke).operatorUpdates.isEmpty())
        assertEquals(0.0, SoldierEvents.apply(ev(SoldierEventKind.DELEGATION_TOUR, "gen"), "approve", broke).prestigeDelta, 0.001)
    }

    @Test
    fun buffsRoundTrip() {
        val b = SoldierEventBuffs(123L, OpRequestType.NIGHT, 456L, 10, true)
        assertEquals(b, SoldierEventBuffs.fromCsv(b.toCsv()))
        assertEquals(SoldierEventBuffs(), SoldierEventBuffs.fromCsv(""))
    }
}
