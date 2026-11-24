package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCohesionRules
import com.example.domain.model.*
import com.example.ui.components.buildOrganizedRosterSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 26: squad order, ODA names, uniform screen groups, Memorial (items 146, 147, 151). */
class Session26Test {
    private fun sq(id: String, name: String, sf: Boolean = false, members: List<String> = emptyList()) =
        TacticalSquad(id, name, members, members.firstOrNull(), isSpecialForces = sf)

    @Test
    fun regularSquadsFirstThenOdas() {
        val order = SquadCohesionRules.displayOrder(listOf(
            sq("a", "ODA 2", true), sq("b", "Bravo Squad"), sq("c", "ODA 1", true), sq("d", "Alpha Squad"), sq("e", "Wolfpack")
        )).map { it.name }
        assertEquals(listOf("Alpha Squad", "Bravo Squad", "Wolfpack", "ODA 1", "ODA 2"), order)
    }

    @Test
    fun sfSquadsDefaultToOdaNumbers() {
        assertEquals("ODA 1", SquadCohesionRules.nextDefaultSquadName(listOf(sq("a", "Alpha Squad")), specialForces = true))
        assertEquals("ODA 2", SquadCohesionRules.nextDefaultSquadName(listOf(sq("a", "ODA 1", true)), specialForces = true))
        assertEquals("Alpha Squad", SquadCohesionRules.nextDefaultSquadName(listOf(sq("a", "ODA 1", true))))
        assertTrue(SquadCohesionRules.isDefaultSquadName("Echo Squad"))
        assertTrue(SquadCohesionRules.isDefaultSquadName("ODA 3"))
        assertTrue(!SquadCohesionRules.isDefaultSquadName("Wolfpack"))
    }

    @Test
    fun uniformScreenGroupsInOrderWithoutTheFallen() {
        val ops = GameCatalog.initialOperators.map { it.copy(unlocked = true) }
        val co = ops.first { it.id == "op_kestrel" }.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        val fallen = ops.first { it.id == "op_warden" }.copy(status = OperatorStatus.FALLEN)
        val pilot = GameCatalog.createRecruit("op_p", OperatorSpecialty.AVIATION, RecruitSource.WOCS_AVIATOR, emptySet(), 1).copy(unlocked = true)
        val roster = ops.map { when (it.id) { co.id -> co; fallen.id -> fallen; else -> it } } + pilot
        val squads = listOf(sq("sf", "ODA 1", true, listOf("op_bastion")), sq("al", "Alpha Squad", false, listOf("op_vesper", "op_cipher")))
        val ids = buildOrganizedRosterSections(roster, squads).map { it.id }
        assertEquals(listOf("sec_hq_staff", "sec_squad_al", "sec_squad_sf", "sec_aviators", "sec_field_unassigned"), ids)
        assertTrue(buildOrganizedRosterSections(roster, squads).flatMap { it.operators }.none { it.id == fallen.id })
    }
}
