package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.catalog.GameCatalog
import com.example.data.local.AegisDatabase
import com.example.data.local.AegisRepository
import com.example.domain.PromotionEndorsementFocus
import com.example.domain.model.AwardedMedal
import com.example.domain.model.CamoPattern
import com.example.domain.model.CombatPatchRecord
import com.example.domain.model.DressUniformStyle
import com.example.domain.model.HeadgearStyle
import com.example.domain.model.MissionRisk
import com.example.domain.model.OperatorRank
import com.example.domain.model.OperatorStatus
import com.example.domain.model.RankTier
import com.example.domain.model.ResourceBundle
import com.example.domain.model.SquadUniformStandard
import com.example.domain.model.TacticalSquad
import com.example.domain.model.UniformMode
import com.example.domain.model.UniformTailorOverrides
import com.example.domain.model.UnitPatchStyle
import com.example.domain.model.WardrobeKit
import com.example.ui.components.OperatorUniformPreview
import com.example.ui.components.RankInsigniaBadge
import com.example.ui.components.ResourceHudBar
import com.example.ui.components.UniformInspectionDialog
import com.example.ui.screens.DressAwardsScreen
import com.example.ui.screens.HqOpsScreen
import com.example.ui.screens.SquadArmoryScreen
import com.example.ui.theme.AegisOpsTheme
import com.example.ui.viewmodel.CommandUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AegisOpsRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var db: AegisDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun `fresh install starts a new save stamped with the current save format`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        val result = repo.loadOrResetSave(nowMillis = 5_000_000L)

        assertEquals(false, result.wasReset)
        assertEquals(AegisRepository.SAVE_FORMAT_VERSION, result.state.saveFormatVersion)
        assertTrue(repo.squads.first().any { it.id == "sq_alpha" })
    }

    @Test
    fun `save with an older save format is replaced with a new command`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        repo.ensureSeeded(nowMillis = 5_000_000L)
        repo.updateCommanderState { it.copy(credits = 99_999.0, saveFormatVersion = 0) }
        repo.deleteSquad("sq_alpha")

        val result = repo.loadOrResetSave(nowMillis = 6_000_000L)

        assertEquals(true, result.wasReset)
        assertEquals(AegisRepository.SAVE_FORMAT_VERSION, result.state.saveFormatVersion)
        assertEquals(320.0, result.state.credits, 0.001)
        assertTrue(repo.squads.first().any { it.id == "sq_alpha" })
    }

    @Test
    fun `save with the current save format loads without a reset`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        repo.ensureSeeded(nowMillis = 5_000_000L)
        repo.updateCommanderState { it.copy(credits = 1_234.0) }

        val result = repo.loadOrResetSave(nowMillis = 6_000_000L)

        assertEquals(false, result.wasReset)
        assertEquals(1_234.0, result.state.credits, 0.001)
    }

    @Test
    fun `repository seeds and persists retirement legacy state`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        val seeded = repo.ensureSeeded(nowMillis = 5_000_000L)
        assertEquals(0, seeded.veteranLegacyBps)
        assertEquals(0, seeded.retiredVeteransCount)

        val operators = repo.operators.first()
        val vesper = operators.first { it.id == "op_vesper" }
        repo.saveOperator(
            vesper.copy(
                status = OperatorStatus.RETIRED,
                retirementRequested = false
            )
        )
        val updatedCommander = repo.updateCommanderState { state ->
            state.copy(
                veteranLegacyBps = 75,
                retiredVeteransCount = 1,
                lastRetirementAtMillis = 5_001_000L
            )
        }

        assertEquals(75, updatedCommander.veteranLegacyBps)
        assertEquals(1, updatedCommander.retiredVeteransCount)
        val persistedVesper = repo.operators.first().first { it.id == "op_vesper" }
        assertEquals(OperatorStatus.RETIRED, persistedVesper.status)
    }

    @Test
    fun `dress awards screen shows empty state and navigates to squad when no active personnel exist`() {
        var navigatedToSquad = false
        val retiredOnly = GameCatalog.initialOperators.take(2).map {
            it.copy(unlocked = true, status = OperatorStatus.RETIRED)
        }

        composeTestRule.setContent {
            AegisOpsTheme {
                DressAwardsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = retiredOnly
                    ),
                    onToggleUniformMode = {},
                    onToggleDressDisplayMode = {},
                    onPromoteOperator = { _, _ -> },
                    onCustomizeOperator = { _, _ -> },
                    onAwardMedal = { _, _, _, _ -> },
                    onDismissDeed = {},
                    onNavigateToSquad = { navigatedToSquad = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No Active or Memorial Personnel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dress_empty_go_squad").assertIsDisplayed().performClick()
        assertTrue(navigatedToSquad)
    }

    @Test
    fun `repository seeds expanded armory catalog and armory drawer shows all five slot filters`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        repo.ensureSeeded(nowMillis = 5_000_000L)
        val seededGear = repo.gearItems.first()
        assertEquals(GameCatalog.initialGear.size, seededGear.size)
        assertTrue(seededGear.any { it.id == "wpn_mk20_ssr" && it.requiredSchoolId == "msn_ranger_school" })

        composeTestRule.setContent {
            AegisOpsTheme {
                SquadArmoryScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = GameCatalog.initialOperators,
                        gearItems = seededGear
                    ),
                    onRecruitOperator = {},
                    onToggleUniformMode = {},
                    onCustomizeOperator = { _, _ -> },
                    onEquipGear = { _, _ -> },
                    onUnequipGear = { _, _ -> },
                    onPurchaseOrUpgradeGear = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_armory_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("armory_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("armory_filter_wpn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("armory_filter_opt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("armory_filter_arm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("armory_filter_hlm").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("armory_filter_utl").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tailor studio renders collapsible dropdown categories for coats patches cords pins and medals`() {
        composeTestRule.setContent {
            AegisOpsTheme {
                DressAwardsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = GameCatalog.initialOperators.filter { it.unlocked }
                    ),
                    onToggleUniformMode = {},
                    onToggleDressDisplayMode = {},
                    onPromoteOperator = { _, _ -> },
                    onCustomizeOperator = { _, _ -> },
                    onAwardMedal = { _, _, _, _ -> },
                    onDismissDeed = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_tailor_panel").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tailor_cat_coat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tailor_cat_patches").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tailor_cat_cords").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_left_cord").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_left_cord_opt_cord_infantry_blue").assertExists()
        composeTestRule.onNodeWithTag("dropdown_left_cord_opt_cord_general_aiguillette").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dropdown_left_cord_opt_cord_valor_fourragere").assertDoesNotExist()
        // Dismiss exposed menu by selecting already-equipped Infantry cord
        composeTestRule.onNodeWithTag("dropdown_left_cord_opt_cord_infantry_blue").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tailor_cat_pins").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_1").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_1_opt_insig_eib_rifle").assertExists()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_1_opt_insig_staff_crest").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_1_opt_auto").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dropdown_pin_slot_3").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_3_opt_insig_staff_crest").assertExists()
        // Slot 3 is the catch-all, so it lists every chest badge
        composeTestRule.onNodeWithTag("dropdown_pin_slot_3_opt_insig_eib_rifle").assertExists()
        composeTestRule.onNodeWithTag("dropdown_pin_slot_3_opt_auto").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tailor_cat_medals").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tailor_cat_accents").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `officer dress uniforms and badges render all six grades from 2LT through COL`() {
        val baseOfficer = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .copy(authorizedTier = RankTier.OFFICER)
        val officerRanks = OperatorRank.entries.filter { it.tier == RankTier.OFFICER }

        composeTestRule.setContent {
            AegisOpsTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    officerRanks.forEach { targetRank ->
                        val dressOfficer = baseOfficer.copy(
                            xp = targetRank.xpRequired,
                            uniformMode = UniformMode.DRESS_UNIFORM
                        )
                        val fieldOfficer = dressOfficer.copy(uniformMode = UniformMode.FIELD_TACTICAL)
                        Row {
                            RankInsigniaBadge(rank = targetRank)
                            OperatorUniformPreview(
                                operator = dressOfficer,
                                equippedGear = emptyList(),
                                awardedMedals = emptyList(),
                                modifier = Modifier.size(120.dp, 160.dp)
                            )
                            OperatorUniformPreview(
                                operator = fieldOfficer,
                                equippedGear = emptyList(),
                                awardedMedals = emptyList(),
                                modifier = Modifier.size(120.dp, 160.dp)
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        officerRanks.forEach { targetRank ->
            composeTestRule.onNodeWithText("${targetRank.code} • ${targetRank.payGrade}")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun `hq ops screen category tabs and collapsible groups filter and expand mission cards`() {
        composeTestRule.setContent {
            AegisOpsTheme {
                HqOpsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = GameCatalog.initialOperators.filter { it.unlocked }
                    ),
                    onReadinessDrill = {},
                    onUpgradeFacility = {},
                    onStartDeployment = { _, _ -> },
                    onClaimDeployment = {},
                    onTreatWounded = {},
                    onNavigateToDress = {},
                    onNavigateToSquad = {},
                    onOpenCommandGuide = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ops_tab_combat").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_card_msn_ridge_patrol").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("mission_card_msn_sf_school").assertDoesNotExist()

        composeTestRule.onNodeWithTag("ops_tab_schools").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_card_msn_basic_qual").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("mission_card_msn_ridge_patrol").assertDoesNotExist()

        composeTestRule.onNodeWithTag("ops_tab_staff").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_card_msn_diplo_summit").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("ops_tab_all").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ops_section_combat").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_card_msn_ridge_patrol").assertDoesNotExist()
    }

    @Test
    fun `squad screen auto gear button triggers distribution callback for deployable field soldiers`() {
        var autoDistributeCalls = 0
        composeTestRule.setContent {
            AegisOpsTheme {
                SquadArmoryScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = GameCatalog.initialOperators.filter { it.unlocked },
                        gearItems = GameCatalog.initialGear
                    ),
                    onRecruitOperator = {},
                    onToggleUniformMode = {},
                    onCustomizeOperator = { _, _ -> },
                    onEquipGear = { _, _ -> },
                    onUnequipGear = { _, _ -> },
                    onAutoDistributeGear = { autoDistributeCalls++ },
                    onPurchaseOrUpgradeGear = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auto_distribute_gear_button")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, autoDistributeCalls)
    }

    @Test
    fun `resource hud prestige chip triggers cheat prestige grant when tapped`() {
        var cheatCalls = 0
        composeTestRule.setContent {
            AegisOpsTheme {
                ResourceHudBar(
                    resources = ResourceBundle(credits = 100.0, supplies = 50.0, intel = 20.0, prestige = 15.0),
                    rates = ResourceBundle(credits = 2.0, supplies = 1.5, intel = 0.5, prestige = 0.0),
                    onPrestigeClick = { cheatCalls++ }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hud_prestige_chip")
            .assertIsDisplayed()
            .performClick()
            .performClick()
        assertEquals(2, cheatCalls)
    }

    @Test
    fun `promotion board dialog shows merit prerequisites board opinions and dispatches selected endorsement`() {
        val candidate = GameCatalog.initialOperators.first { it.id == "op_vesper" }.copy(
            xp = 800,
            authorizedTier = RankTier.NCO,
            missionsCompleted = 6,
            highestRiskSurvived = MissionRisk.MODERATE,
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_nco_school", "msn_snco_school")
        )
        val citations = listOf(
            AwardedMedal("cit_1", candidate.id, "medal_nco_dev", "Graduated NCO & SNCO Academies", awardedAtMillis = 1L)
        )
        var promotedId: String? = null
        var chosenFocus: PromotionEndorsementFocus? = null

        composeTestRule.setContent {
            AegisOpsTheme {
                DressAwardsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        resources = ResourceBundle(credits = 500.0, supplies = 200.0, intel = 100.0, prestige = 50.0),
                        operators = listOf(candidate),
                        citations = citations
                    ),
                    onToggleUniformMode = {},
                    onToggleDressDisplayMode = {},
                    onPromoteOperator = { id, focus ->
                        promotedId = id
                        chosenFocus = focus
                    },
                    onCustomizeOperator = { _, _ -> },
                    onAwardMedal = { _, _, _, _ -> },
                    onDismissDeed = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("promote_operator_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("promotion_board_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("board_merit_school_slc").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("board_opinion_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("board_endorsement_tactical_cunning")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("confirm_board_promotion").performClick()

        assertEquals(candidate.id, promotedId)
        assertEquals(PromotionEndorsementFocus.TACTICAL_CUNNING, chosenFocus)
    }

    @Test
    fun `repository seeds Alpha Squad with leader and persists squad updates and deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
        val repo = AegisRepository(database.aegisDao())

        repo.ensureSeeded(nowMillis = 5_000_000L)
        val initialSquads = repo.squads.first()
        assertEquals(1, initialSquads.size)
        val alpha = initialSquads.first()
        assertEquals("sq_alpha", alpha.id)
        assertEquals("op_kestrel", alpha.leaderId)
        assertEquals(listOf("op_kestrel", "op_vesper", "op_cipher"), alpha.memberIds)

        val updatedAlpha = alpha.copy(
            leaderId = "op_vesper",
            cohesionXp = 420,
            missionsTogether = 6
        )
        repo.saveSquad(updatedAlpha)
        val reloaded = repo.squads.first().first { it.id == "sq_alpha" }
        assertEquals("op_vesper", reloaded.leaderId)
        assertEquals(420, reloaded.cohesionXp)
        assertEquals(6, reloaded.missionsTogether)

        repo.deleteSquad("sq_alpha")
        assertTrue(repo.squads.first().isEmpty())
    }

    @Test
    fun `squad screen manages squad leader designation via squad editor dialog`() {
        val starterOps = GameCatalog.initialOperators.filter { it.unlocked }
        val alphaSquad = TacticalSquad(
            id = "sq_alpha",
            name = "Alpha Squad",
            memberIds = listOf("op_kestrel", "op_vesper", "op_cipher"),
            leaderId = "op_kestrel",
            cohesionXp = 120,
            missionsTogether = 2
        )
        var savedLeaderId: String? = null
        var savedMembers: List<String> = emptyList()

        composeTestRule.setContent {
            AegisOpsTheme {
                SquadArmoryScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        operators = starterOps,
                        gearItems = GameCatalog.initialGear,
                        squads = listOf(alphaSquad)
                    ),
                    onRecruitOperator = {},
                    onToggleUniformMode = {},
                    onCustomizeOperator = { _, _ -> },
                    onEquipGear = { _, _ -> },
                    onUnequipGear = { _, _ -> },
                    onPurchaseOrUpgradeGear = {},
                    onSaveSquad = { _, _, members, leader, _, _, _, _ ->
                        savedMembers = members
                        savedLeaderId = leader
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("squad_card_sq_alpha").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manage_squad_sq_alpha").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("squad_editor_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("set_leader_op_cipher").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("save_squad_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("op_cipher", savedLeaderId)
        assertEquals(listOf("op_kestrel", "op_vesper", "op_cipher"), savedMembers)
    }

    @Test
    fun `hq ops staging sheet allows one tap squad selection and launches with squadId`() {
        val starterOps = GameCatalog.initialOperators.filter { it.unlocked }
        val alphaSquad = TacticalSquad(
            id = "sq_alpha",
            name = "Alpha Squad",
            memberIds = listOf("op_kestrel", "op_vesper", "op_cipher"),
            leaderId = "op_vesper",
            cohesionXp = 300,
            missionsTogether = 4
        )
        var launchedMissionId: String? = null
        var launchedOps: List<String> = emptyList()
        var launchedSquadId: String? = null

        composeTestRule.setContent {
            AegisOpsTheme {
                HqOpsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        resources = ResourceBundle(credits = 500.0, supplies = 200.0, intel = 100.0, prestige = 50.0),
                        operators = starterOps,
                        gearItems = GameCatalog.initialGear,
                        squads = listOf(alphaSquad)
                    ),
                    onReadinessDrill = {},
                    onUpgradeFacility = {},
                    onStartDeployment = { _, _ -> },
                    onStartDeploymentWithSquad = { mission, opIds, sqId ->
                        launchedMissionId = mission.id
                        launchedOps = opIds
                        launchedSquadId = sqId
                    },
                    onClaimDeployment = {},
                    onTreatWounded = {},
                    onNavigateToDress = {},
                    onNavigateToSquad = {},
                    onOpenCommandGuide = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ops_tab_combat").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("assemble_mission_msn_ridge_patrol").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stage_squad_sq_alpha").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("staging_cohesion_bonus_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("launch_mission_msn_ridge_patrol").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("msn_ridge_patrol", launchedMissionId)
        assertEquals("sq_alpha", launchedSquadId)
        // Designated leader op_vesper should be ordered first in deployed squad
        assertEquals(listOf("op_vesper", "op_kestrel", "op_cipher"), launchedOps)
    }

    @Test
    fun `dress awards screen triggers formal inspection and toggles free tailor override`() {
        val op = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        var inspectedId: String? = null
        var lastFreeOverride: Boolean? = null

        composeTestRule.setContent {
            AegisOpsTheme {
                DressAwardsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        resources = ResourceBundle(credits = 500.0, supplies = 200.0, intel = 100.0, prestige = 50.0),
                        operators = listOf(op)
                    ),
                    onToggleUniformMode = {},
                    onToggleDressDisplayMode = {},
                    onPromoteOperator = { _, _ -> },
                    onCustomizeOperator = { _, req ->
                        lastFreeOverride = req.tailorOverrides.freeTailorOverride
                    },
                    onAwardMedal = { _, _, _, _ -> },
                    onDismissDeed = {},
                    onConductInspection = { id -> inspectedId = id }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("conduct_uniform_inspection_button").performScrollTo().performClick()
        assertEquals(op.id, inspectedId)

        composeTestRule.onNodeWithTag("toggle_tailor_panel").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_free_tailor_override").performScrollTo().performClick()
        assertEquals(true, lastFreeOverride)
    }

    @Test
    fun `uniform inspection dialog switches camera zoom views and shows hotspot details`() {
        val op = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            missionsCompleted = 6,
            highestRiskSurvived = MissionRisk.MODERATE,
            combatPatchHistory = listOf(CombatPatchRecord("camp_test", UnitPatchStyle.DIVISION_2, 10L))
        )
        val medal = GameCatalog.medals.first { it.id == "medal_army_commendation" }
        val repeatCitations = listOf(
            AwardedMedal("c1", op.id, medal.id, "First award", awardedAtMillis = 1L),
            AwardedMedal("c2", op.id, medal.id, "Second award", awardedAtMillis = 2L)
        )
        val squad = TacticalSquad(
            id = "sq_alpha",
            name = "Alpha Squad",
            memberIds = listOf(op.id),
            leaderId = op.id
        )

        composeTestRule.setContent {
            AegisOpsTheme {
                UniformInspectionDialog(
                    operator = op,
                    equippedGear = emptyList(),
                    awardedMedals = listOf(medal, medal),
                    allCitations = repeatCitations,
                    squads = listOf(squad),
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zoom_view_chest").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("hotspot_combat_patch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hotspot_detail_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("${medal.name} (×2)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `wardrobe kit strip enforces HQ Staff Field lockout and toggles Smart Duty-Dress`() {
        val hqStaffOp = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            status = OperatorStatus.HQ_STAFF,
            activeWardrobeKit = WardrobeKit.ALPHAS
        )
        var smartToggled = false
        var selectedKit: WardrobeKit? = null

        composeTestRule.setContent {
            AegisOpsTheme {
                DressAwardsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        smartWardrobeEnabled = true,
                        operators = listOf(hqStaffOp)
                    ),
                    onToggleUniformMode = {},
                    onToggleDressDisplayMode = {},
                    onPromoteOperator = { _, _ -> },
                    onCustomizeOperator = { _, _ -> },
                    onAwardMedal = { _, _, _, _ -> },
                    onDismissDeed = {},
                    onSelectWardrobeKit = { _, kit -> selectedKit = kit },
                    onToggleSmartWardrobe = { smartToggled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_smart_wardrobe").performScrollTo().performClick()
        assertTrue(smartToggled)

        composeTestRule.onNodeWithTag("wardrobe_tab_field").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("wardrobe_tab_bravos").performScrollTo().assertIsEnabled().performClick()
        assertEquals(WardrobeKit.BRAVOS, selectedKit)

        composeTestRule.onNodeWithTag("wardrobe_tab_mess").performScrollTo().performClick()
        assertEquals(WardrobeKit.MESS, selectedKit)
    }

    @Test
    fun `mission cards show uniform requirement badges and staging equips Mess Dress manually`() {
        val ops = listOf(
            GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
                activeWardrobeKit = WardrobeKit.FIELD
            )
        )
        var switchedOps: List<String> = emptyList()
        var switchedKit: WardrobeKit? = null

        composeTestRule.setContent {
            AegisOpsTheme {
                HqOpsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        smartWardrobeEnabled = false,
                        resources = ResourceBundle(credits = 500.0, supplies = 300.0, intel = 100.0, prestige = 50.0),
                        operators = ops
                    ),
                    onReadinessDrill = {},
                    onUpgradeFacility = {},
                    onStartDeployment = { _, _ -> },
                    onClaimDeployment = {},
                    onTreatWounded = {},
                    onNavigateToDress = {},
                    onNavigateToSquad = {},
                    onSwitchOperatorsToKit = { ids, kit ->
                        switchedOps = ids
                        switchedKit = kit
                    },
                    onOpenCommandGuide = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ops_tab_schools").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_uniform_req_msn_basic_qual").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("ops_tab_staff").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mission_uniform_req_msn_diplo_summit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("assemble_mission_msn_diplo_summit").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("staging_uniform_protocol_msn_diplo_summit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("staging_equip_kit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("op_kestrel"), switchedOps)
        assertEquals(WardrobeKit.MESS, switchedKit)
    }

    @Test
    fun `expanded 4-kit squad standard persists roundtrip and squad kit switcher dispatches`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AegisDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repo = AegisRepository(db.aegisDao())
            repo.ensureSeeded(1000L)

            val squad = TacticalSquad(
                id = "sq_honor",
                name = "Honor Guard",
                memberIds = listOf("op_kestrel", "op_vesper"),
                leaderId = "op_kestrel",
                standardPatch = UnitPatchStyle.DIVISION_3,
                standardCamo = CamoPattern.NIGHT_CHARCOAL,
                standardBravosHeadgear = HeadgearStyle.TAN_BERET,
                standardBravosDressStyle = DressUniformStyle.NCO_PARADE_BLUES,
                standardHeadgear = HeadgearStyle.OFFICER_PEAKED_CAP,
                standardDressStyle = DressUniformStyle.OFFICER_SERVICE_BLUES,
                standardMessDressStyle = DressUniformStyle.HONOR_GUARD_IVORY,
                standardMessHeadgear = HeadgearStyle.OFFICER_PEAKED_CAP,
                standardAlphasGloves = false,
                standardMessGloves = true
            )
            repo.saveSquad(squad)
            val loaded = repo.squads.first().first { it.id == "sq_honor" }
            assertEquals(HeadgearStyle.TAN_BERET, loaded.standardBravosHeadgear)
            assertEquals(DressUniformStyle.NCO_PARADE_BLUES, loaded.standardBravosDressStyle)
            assertEquals(DressUniformStyle.HONOR_GUARD_IVORY, loaded.standardMessDressStyle)
            assertEquals(HeadgearStyle.OFFICER_PEAKED_CAP, loaded.standardMessHeadgear)
            assertEquals(false, loaded.standardAlphasGloves)
            assertEquals(true, loaded.standardMessGloves)
            assertTrue(loaded.hasUniformStandard)

            var switchedSquadId: String? = null
            var switchedKit: WardrobeKit? = null
            var savedStandard: SquadUniformStandard? = null
            var savedApplyNow = false

            val starterOps = GameCatalog.initialOperators.filter { it.unlocked }
            composeTestRule.setContent {
                AegisOpsTheme {
                    SquadArmoryScreen(
                        uiState = CommandUiState(
                            isLoading = false,
                            smartWardrobeEnabled = false,
                            operators = starterOps,
                            gearItems = GameCatalog.initialGear,
                            squads = listOf(loaded)
                        ),
                        onRecruitOperator = {},
                        onToggleUniformMode = {},
                        onCustomizeOperator = { _, _ -> },
                        onEquipGear = { _, _ -> },
                        onUnequipGear = { _, _ -> },
                        onPurchaseOrUpgradeGear = {},
                        onSwitchSquadKit = { sqId, kit ->
                            switchedSquadId = sqId
                            switchedKit = kit
                        },
                        onSaveSquadWithStandard = { _, _, _, _, std, applyNow ->
                            savedStandard = std
                            savedApplyNow = applyNow
                        }
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("squad_kit_switch_sq_honor_mess").performScrollTo().performClick()
            assertEquals("sq_honor", switchedSquadId)
            assertEquals(WardrobeKit.MESS, switchedKit)

            composeTestRule.onNodeWithTag("manage_squad_sq_honor").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("squad_standard_bravos_headgear_dropdown").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("squad_standard_mess_dress_dropdown").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("squad_standard_alphas_gloves_dropdown").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("squad_standard_mess_gloves_dropdown").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("dialog_apply_squad_standard").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            assertTrue(savedApplyNow)
            assertEquals(DressUniformStyle.HONOR_GUARD_IVORY, savedStandard?.messDressStyle)
            assertEquals(HeadgearStyle.TAN_BERET, savedStandard?.bravosHeadgear)
            assertEquals(false, savedStandard?.alphasGloves)
            assertEquals(true, savedStandard?.messGloves)
        } finally {
            db.close()
        }
    }

    @Test
    fun `ops board displays sequential theater campaigns and triggers rotating mission refresh`() {
        val ops = listOf(
            GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        )
        var refreshCalls = 0
        val completedAllState = mutableStateOf(false)

        composeTestRule.setContent {
            AegisOpsTheme {
                val allCampaigns = if (completedAllState.value) {
                    GameCatalog.campaigns.map { it.id }.toSet()
                } else {
                    emptySet()
                }
                val now = 1_000_000L
                HqOpsScreen(
                    uiState = CommandUiState(
                        isLoading = false,
                        resources = ResourceBundle(credits = 500.0, supplies = 400.0, intel = 60.0, prestige = 40.0),
                        operators = ops,
                        nowMillis = now,
                        completedCampaignIds = allCampaigns,
                        allCampaignsCleared = completedAllState.value,
                        rotatingMissionIds = listOf(
                            "msn_harbor_overwatch",
                            "msn_deep_expedition",
                            "msn_diplo_treaty",
                            "msn_sa_timber_cache"
                        ),
                        rotationUpdatedAtMillis = now - 60_000L
                    ),
                    onReadinessDrill = {},
                    onUpgradeFacility = {},
                    onStartDeployment = { _, _ -> },
                    onClaimDeployment = {},
                    onTreatWounded = {},
                    onNavigateToDress = {},
                    onNavigateToSquad = {},
                    onRefreshRotatingMissions = { refreshCalls += 1 },
                    onOpenCommandGuide = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_rotating_ops").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, refreshCalls)

        composeTestRule.onNodeWithTag("ops_tab_campaigns").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("campaign_card_camp_veridian").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("assemble_mission_camp_veridian_1").performScrollTo().assertIsEnabled()

        // Expand Campaign II while locked and verify Stage 1 assemble button is disabled
        composeTestRule.onNodeWithTag("ops_board_list").performScrollToKey("camp_boreal")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("campaign_toggle_camp_boreal").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("assemble_mission_camp_boreal_1").performScrollTo().assertIsNotEnabled()

        // Mark all 4 campaigns complete -> Full Free Replay unlocks later stages too
        completedAllState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ops_board_list").performScrollToKey("camp_kestrel")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("campaign_toggle_camp_kestrel").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("assemble_mission_camp_kestrel_4").performScrollTo().assertIsEnabled()
    }
}


