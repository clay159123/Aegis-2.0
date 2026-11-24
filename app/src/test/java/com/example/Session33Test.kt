package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCompositionRules
import com.example.domain.SquadCompositionRules.Kind
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Session33Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s33", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual"))
    private var n = 0
    private fun soldier(tier: RankTier, xp: Int, sf: Boolean = false, warrant: Boolean = false): Operator = base.copy(
        id = "op_s33_${n++}",
        xp = xp,
        authorizedTier = if (warrant) RankTier.WARRANT else tier,
        warrantGrade = if (warrant) 1 else 0,
        completedMissionIds = base.completedMissionIds + if (sf) setOf("msn_sf_school") else emptySet()
    )
    private fun officer(sf: Boolean = false) = soldier(RankTier.OFFICER, OperatorRank.LT2.xpRequired, sf)
    private fun warrant(sf: Boolean = false) = soldier(RankTier.WARRANT, OperatorRank.WO1.xpRequired, sf, warrant = true)
    private fun snco(sf: Boolean = false) = soldier(RankTier.SNCO, OperatorRank.SFC.xpRequired, sf)
    private fun nco(sf: Boolean = false) = soldier(RankTier.NCO, OperatorRank.SGT.xpRequired, sf)
    private fun junior() = soldier(RankTier.SOLDIER, OperatorRank.SPC.xpRequired)

    @Test
    fun fullDetachmentAndFullSquadFollowTheAgreedMixes() {
        // Chris's mixes
        assertEquals(Kind.FULL_DETACHMENT, SquadCompositionRules.qualifies(listOf(officer(true), warrant(true), snco(true), nco(true), nco(true), nco(true))))
        assertEquals(Kind.FULL_SQUAD, SquadCompositionRules.qualifies(listOf(officer(), warrant(), snco(), nco(), junior(), junior())))
        // The warrant is optional
        assertEquals(Kind.FULL_SQUAD, SquadCompositionRules.qualifies(listOf(officer(), snco(), nco(), junior(), junior(), junior())))
        assertEquals(Kind.FULL_DETACHMENT, SquadCompositionRules.qualifies(listOf(officer(true), snco(true), nco(true), nco(true))))
        // Misses: a second NCO on a regular squad, no officer, a junior on an SF team
        assertNull(SquadCompositionRules.qualifies(listOf(officer(), snco(), nco(), nco(), junior(), junior())))
        assertNull(SquadCompositionRules.qualifies(listOf(snco(), nco(), junior(), junior())))
        assertTrue(SquadCompositionRules.missing(listOf(snco(), nco(), junior())).contains("Needs an officer"))
        val sfWithJunior = listOf(officer(true), snco(true), nco(true), soldier(RankTier.SOLDIER, OperatorRank.SPC.xpRequired, sf = true))
        assertNull(SquadCompositionRules.qualifies(sfWithJunior))
    }

    @Test
    fun compositionAddsThreePercentOnFieldMissionsOnly() {
        val field = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.requiredSquads <= 1 && it.risk == MissionRisk.MODERATE }
        val school = GameCatalog.missions.first { it.scope == MissionScope.TRAINING }
        val full = listOf(officer(), snco(), nco(), junior())
        assertEquals(Kind.FULL_SQUAD to 3, SquadCompositionRules.teamBonus(field, full, emptyList()))
        assertNull(SquadCompositionRules.teamBonus(school, full, emptyList()))
        assertNull(SquadCompositionRules.teamBonus(field, listOf(snco(), nco(), junior(), junior()), emptyList()))
    }

    @Test
    fun fromSergeantUpThePlayerPinsEachRank() {
        // A SGT whose XP reaches SSG stays SGT, promotable, until the rank is pinned
        val sgt = nco().copy(pinnedRank = OperatorRank.SGT, xp = OperatorRank.SSG.xpRequired + 10)
        assertEquals(OperatorRank.SGT, sgt.rank)
        assertEquals(OperatorRank.SSG, sgt.pinEligibleRank)
        val ssg = sgt.promotedTo(OperatorRank.SSG)
        assertEquals(OperatorRank.SSG, ssg.rank)
        assertEquals(OperatorRank.SSG, ssg.pinnedRank)
        // Not enough XP: no P
        assertNull(nco().copy(pinnedRank = OperatorRank.SGT).pinEligibleRank)
        // A board gate is still a board, never a pin
        val sgtAtSfc = ssg.copy(xp = OperatorRank.SFC.xpRequired + 10)
        assertEquals(OperatorRank.SSG, sgtAtSfc.rank)
        assertNull(sgtAtSfc.pinEligibleRank)
        // Officers pin too
        val lt = officer().copy(pinnedRank = OperatorRank.LT2, xp = OperatorRank.CPT.xpRequired)
        assertEquals(OperatorRank.LT2, lt.rank)
        assertEquals(OperatorRank.LT1, lt.pinEligibleRank)
    }

    @Test
    fun juniorRanksStayAutomaticAndUnpinnedSoldiersFollowXp() {
        val pv2 = soldier(RankTier.SOLDIER, OperatorRank.SPC.xpRequired)
        assertEquals(OperatorRank.SPC, pv2.rank)
        assertNull(pv2.pinEligibleRank)
        // No pin recorded yet (not saved): the rank follows XP as before
        val unpinned = nco().copy(xp = OperatorRank.SSG.xpRequired)
        assertEquals(OperatorRank.SSG, unpinned.rank)
        assertNull(unpinned.pinEligibleRank)
    }

    @Test
    fun hqCapsNeverShowP() {
        // A seated S-1 type billet tops out at MSG for enlisted soldiers
        val role = HqStaffRole.entries.first { it.rankCapFor(OperatorRank.MSG) == OperatorRank.MSG }
        val capped = snco().copy(pinnedRank = OperatorRank.MSG, xp = OperatorRank.SGM.xpRequired + 50, status = OperatorStatus.HQ_STAFF, hqRole = role)
        assertEquals(OperatorRank.MSG, capped.rank)
        assertNull(capped.pinEligibleRank)
        // Below the cap the P shows
        val under = capped.copy(pinnedRank = OperatorRank.SFC)
        assertEquals(OperatorRank.MSG, under.pinEligibleRank)
    }

    @Test
    fun aCommissionResetsThePinToTheNewTier() {
        val sgm = snco().copy(pinnedRank = OperatorRank.SGM, xp = OperatorRank.SGM.xpRequired)
        val lt = sgm.promotedTo(OperatorRank.LT2).copy(authorizedTier = RankTier.OFFICER)
        assertEquals(OperatorRank.LT2, lt.rank)
        assertEquals(OperatorRank.LT2, lt.pinnedRank)
        assertTrue(TacticalCalculator.isSfQualified(officer(true)))
    }
}
