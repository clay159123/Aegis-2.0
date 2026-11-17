package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.GameCatalog
import com.example.data.local.AegisRepository
import com.example.data.local.CommanderStateEntity
import com.example.domain.PromotionEndorsementFocus
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

data class CommandUiState(
    val isLoading: Boolean = true,
    val resources: ResourceBundle = ResourceBundle(),
    val ratesPerSec: ResourceBundle = ResourceBundle(),
    val totalMissionsCompleted: Int = 0,
    val facilities: List<FacilityState> = emptyList(),
    val operators: List<Operator> = emptyList(),
    val gearItems: List<GearItem> = emptyList(),
    val deployments: List<ActiveDeployment> = emptyList(),
    val citations: List<AwardedMedal> = emptyList(),
    val pendingDeeds: List<PendingDeed> = emptyList(),
    val squads: List<TacticalSquad> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
    val maxDeploymentSlots: Int = 2,
    val hqStaffCount: Int = 0,
    val maxFieldRosterSlots: Int = 5,
    val maxHqStaffSlots: Int = 3,
    val hqStaffSummary: TacticalCalculator.HqStaffSummary = TacticalCalculator.HqStaffSummary(),
    val veteranLegacyBps: Int = 0,
    val retiredVeteransCount: Int = 0,
    val lastRetirementAtMillis: Long = 0L,
    val lastInspectionAtMillis: Long = 0L,
    val inspectionBuffUntilMillis: Long = 0L,
    val offlineReport: OfflineReport? = null,
    val lastDebrief: DebriefSummary? = null,
    val lastInspectionReport: UniformInspectionReport? = null,
    val statusBanner: String? = null,
    val smartWardrobeEnabled: Boolean = true,
    val completedCampaignStageIds: Set<String> = emptySet(),
    val completedCampaignIds: Set<String> = emptySet(),
    val allCampaignsCleared: Boolean = false,
    val rotatingMissionIds: List<String> = emptyList(),
    val rotationUpdatedAtMillis: Long = 0L
) {
    /** Deployments that use an HQ tasking slot. Schools and training never take a slot. */
    val occupiedDeploymentSlots: Int
        get() = TacticalCalculator.occupiedDeploymentSlots(deployments)
    val veteranLegacyPctText: String get() = TacticalCalculator.formatBpsPercent(veteranLegacyBps)
    val inspectionBuffActive: Boolean get() = inspectionBuffUntilMillis > nowMillis
    val inspectionBuffRemainingSec: Long
        get() = ((inspectionBuffUntilMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
    val rotationRemainingSec: Long
        get() {
            if (rotationUpdatedAtMillis <= 0L) {
                return TacticalCalculator.STANDALONE_ROTATION_INTERVAL_MILLIS / 1000L
            }
            val elapsed = (nowMillis - rotationUpdatedAtMillis).coerceAtLeast(0L)
            val rem = (TacticalCalculator.STANDALONE_ROTATION_INTERVAL_MILLIS - elapsed).coerceAtLeast(0L)
            return (rem + 999L) / 1000L
        }
}

class CommandViewModel(
    private val repository: AegisRepository
) : ViewModel() {

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val _offlineReport = MutableStateFlow<OfflineReport?>(null)
    private val _lastDebrief = MutableStateFlow<DebriefSummary?>(null)
    private val _lastInspectionReport = MutableStateFlow<UniformInspectionReport?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)

    private var isInitialized = false
    private var lastTickMillis = System.currentTimeMillis()
    @Volatile
    private var isResetting = false

    private val dataFlow = combine(
        repository.commanderState,
        repository.facilities,
        repository.operators,
        repository.gearItems,
        repository.activeDeployments,
        repository.citations,
        repository.pendingDeeds,
        repository.squads
    ) { arr ->
        val commander = arr[0] as CommanderStateEntity?
        @Suppress("UNCHECKED_CAST")
        val facilities = arr[1] as List<FacilityState>
        @Suppress("UNCHECKED_CAST")
        val operators = arr[2] as List<Operator>
        @Suppress("UNCHECKED_CAST")
        val gear = arr[3] as List<GearItem>
        @Suppress("UNCHECKED_CAST")
        val deployments = arr[4] as List<ActiveDeployment>
        @Suppress("UNCHECKED_CAST")
        val citations = arr[5] as List<AwardedMedal>
        @Suppress("UNCHECKED_CAST")
        val pendingDeeds = arr[6] as List<PendingDeed>
        @Suppress("UNCHECKED_CAST")
        val squads = arr[7] as List<TacticalSquad>
        DataSnapshot(commander, facilities, operators, gear, deployments, citations, pendingDeeds, squads)
    }

    val uiState: StateFlow<CommandUiState> = combine(
        dataFlow,
        _nowMillis,
        _offlineReport,
        _lastDebrief,
        _lastInspectionReport,
        _statusBanner
    ) { arr ->
        val data = arr[0] as DataSnapshot
        val now = arr[1] as Long
        val offline = arr[2] as OfflineReport?
        val debrief = arr[3] as DebriefSummary?
        val inspection = arr[4] as UniformInspectionReport?
        val banner = arr[5] as String?
        if (data.commander == null) {
            CommandUiState(isLoading = true)
        } else {
            val hqStaffSummary = TacticalCalculator.summarizeHqStaff(data.operators)
            val rates = TacticalCalculator.productionRates(
                facilities = data.facilities,
                hqStaffCount = hqStaffSummary.staffCount,
                hqStaffSummary = hqStaffSummary,
                veteranLegacyBps = data.commander.veteranLegacyBps
            )
            val completedStages = data.commander.completedCampaignStageIdsCsv
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
            val completedCampaigns = data.commander.completedCampaignIdsCsv
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
            val rotatingIds = data.commander.rotatingMissionIdsCsv
                .split(",")
                .filter { id -> id.isNotBlank() && GameCatalog.rotatingStandalonePool.any { it.id == id } }
            CommandUiState(
                isLoading = false,
                resources = ResourceBundle(
                    credits = data.commander.credits,
                    supplies = data.commander.supplies,
                    intel = data.commander.intel,
                    prestige = data.commander.prestige
                ),
                ratesPerSec = rates,
                totalMissionsCompleted = data.commander.totalMissionsCompleted,
                facilities = data.facilities,
                operators = data.operators,
                gearItems = data.gearItems,
                deployments = data.deployments,
                citations = data.citations,
                pendingDeeds = data.pendingDeeds,
                squads = data.squads,
                nowMillis = now,
                maxDeploymentSlots = TacticalCalculator.maxDeploymentSlots(data.facilities),
                hqStaffCount = hqStaffSummary.staffCount,
                maxFieldRosterSlots = TacticalCalculator.maxFieldRosterSlots(data.facilities),
                maxHqStaffSlots = TacticalCalculator.maxHqStaffSlots(data.facilities),
                hqStaffSummary = hqStaffSummary,
                veteranLegacyBps = data.commander.veteranLegacyBps,
                retiredVeteransCount = data.commander.retiredVeteransCount,
                lastRetirementAtMillis = data.commander.lastRetirementAtMillis,
                lastInspectionAtMillis = data.commander.lastInspectionAtMillis,
                inspectionBuffUntilMillis = data.commander.inspectionBuffUntilMillis,
                offlineReport = offline,
                lastDebrief = debrief,
                lastInspectionReport = inspection,
                statusBanner = banner,
                smartWardrobeEnabled = data.commander.smartWardrobeEnabled,
                completedCampaignStageIds = completedStages,
                completedCampaignIds = completedCampaigns,
                allCampaignsCleared = TacticalCalculator.isAllCampaignsCleared(completedCampaigns),
                rotatingMissionIds = rotatingIds,
                rotationUpdatedAtMillis = data.commander.rotationUpdatedAtMillis
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CommandUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val loadResult = repository.loadOrResetSave(now)
            val initial = loadResult.state
            if (loadResult.wasReset) {
                showBanner("This save was from an older version of Aegis Ops. A new command has been started.")
            }
            val facs = repository.facilities.first()
            val ops = repository.operators.first()
            val deps = repository.activeDeployments.first()

            val elapsedMs = (now - initial.lastSimulatedAtMillis).coerceAtLeast(0L)
            val capSec = TacticalCalculator.offlineCapSeconds(facs)
            val elapsedSec = (elapsedMs / 1000L).coerceAtMost(capSec)

            val staffSummary = TacticalCalculator.summarizeHqStaff(ops)
            val rates = TacticalCalculator.productionRates(
                facilities = facs,
                hqStaffCount = staffSummary.staffCount,
                hqStaffSummary = staffSummary,
                veteranLegacyBps = initial.veteranLegacyBps
            )
            val earnedCredits = rates.credits * elapsedSec
            val earnedSupplies = rates.supplies * elapsedSec
            val earnedIntel = rates.intel * elapsedSec

            if (elapsedSec >= 30L) {
                val finishedOps = deps.count { it.isComplete(now) }
                _offlineReport.value = OfflineReport(
                    elapsedSeconds = elapsedSec,
                    creditsEarned = earnedCredits,
                    suppliesEarned = earnedSupplies,
                    intelEarned = earnedIntel,
                    finishedOpsCount = finishedOps
                )
            }

            val currentRotating = initial.rotatingMissionIdsCsv.split(",").filter { it.isNotBlank() }
            val needsInitRotation = currentRotating.isEmpty() ||
                initial.rotationUpdatedAtMillis <= 0L ||
                (now - initial.rotationUpdatedAtMillis) >= TacticalCalculator.STANDALONE_ROTATION_INTERVAL_MILLIS
            val resolvedRotatingCsv = if (needsInitRotation) {
                TacticalCalculator.pickNextRotatingMissions(currentRotating).joinToString(",")
            } else {
                initial.rotatingMissionIdsCsv
            }
            val resolvedRotationTime = if (needsInitRotation) now else initial.rotationUpdatedAtMillis

            repository.saveCommanderState(
                initial.copy(
                    id = 1,
                    credits = initial.credits + earnedCredits,
                    supplies = initial.supplies + earnedSupplies,
                    intel = initial.intel + earnedIntel,
                    lastSimulatedAtMillis = now,
                    rotatingMissionIdsCsv = resolvedRotatingCsv,
                    rotationUpdatedAtMillis = resolvedRotationTime
                )
            )
            isInitialized = true
            lastTickMillis = now
            runIdleSimulationLoop()
        }
    }

    private suspend fun runIdleSimulationLoop() {
        while (true) {
            delay(1000L)
            val now = System.currentTimeMillis()
            _nowMillis.value = now

            val state = uiState.value
            if (!state.isLoading && isInitialized && !isResetting) {
                val rawElapsedMs = (now - lastTickMillis).coerceAtLeast(1000L)
                lastTickMillis = now
                val capSec = TacticalCalculator.offlineCapSeconds(state.facilities)
                val elapsedSec = (rawElapsedMs / 1000L).coerceIn(1L, capSec)

                val rates = state.ratesPerSec
                val earnedCredits = rates.credits * elapsedSec
                val earnedSupplies = rates.supplies * elapsedSec
                val earnedIntel = rates.intel * elapsedSec

                if (elapsedSec >= 30L) {
                    val finishedOps = state.deployments.count { it.isComplete(now) }
                    val existingReport = _offlineReport.value
                    _offlineReport.value = if (existingReport != null) {
                        existingReport.copy(
                            elapsedSeconds = existingReport.elapsedSeconds + elapsedSec,
                            creditsEarned = existingReport.creditsEarned + earnedCredits,
                            suppliesEarned = existingReport.suppliesEarned + earnedSupplies,
                            intelEarned = existingReport.intelEarned + earnedIntel,
                            finishedOpsCount = finishedOps
                        )
                    } else {
                        OfflineReport(
                            elapsedSeconds = elapsedSec,
                            creditsEarned = earnedCredits,
                            suppliesEarned = earnedSupplies,
                            intelEarned = earnedIntel,
                            finishedOpsCount = finishedOps
                        )
                    }
                }

                repository.updateCommanderState { cur ->
                    val existingRotating = cur.rotatingMissionIdsCsv.split(",").filter { it.isNotBlank() }
                    val rotationDue = existingRotating.isEmpty() ||
                        cur.rotationUpdatedAtMillis <= 0L ||
                        (now - cur.rotationUpdatedAtMillis) >= TacticalCalculator.STANDALONE_ROTATION_INTERVAL_MILLIS
                    cur.copy(
                        credits = cur.credits + earnedCredits,
                        supplies = cur.supplies + earnedSupplies,
                        intel = cur.intel + earnedIntel,
                        lastSimulatedAtMillis = now,
                        rotatingMissionIdsCsv = if (rotationDue) {
                            TacticalCalculator.pickNextRotatingMissions(existingRotating).joinToString(",")
                        } else {
                            cur.rotatingMissionIdsCsv
                        },
                        rotationUpdatedAtMillis = if (rotationDue) now else cur.rotationUpdatedAtMillis
                    )
                }

                val recoveredOps = state.operators.filter {
                    it.status == OperatorStatus.RECOVERING && now >= it.recoveryUntilMillis
                }
                if (recoveredOps.isNotEmpty()) {
                    repository.saveOperators(
                        recoveredOps.map {
                            it.copy(
                                status = if (it.hqRole != null) OperatorStatus.HQ_STAFF else OperatorStatus.READY,
                                recoveryUntilMillis = 0L
                            )
                        }
                    )
                    showBanner("${recoveredOps.joinToString { it.callsign }} discharged from Medbay and ready for duty.")
                }
            } else {
                lastTickMillis = now
            }
        }
    }

    fun refreshRotatingMissions() {
        val state = uiState.value
        if (state.isLoading) return
        val intelCost = TacticalCalculator.ROTATION_REFRESH_INTEL_COST
        if (state.resources.intel < intelCost) {
            showBanner("Need ${intelCost.toInt()} INT to rotate standalone contracts.")
            return
        }
        val nextIds = TacticalCalculator.pickNextRotatingMissions(previousIds = state.rotatingMissionIds)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    intel = (cur.intel - intelCost).coerceAtLeast(0.0),
                    rotatingMissionIdsCsv = nextIds.joinToString(","),
                    rotationUpdatedAtMillis = now,
                    lastSimulatedAtMillis = now
                )
            }
            showBanner("Intel desk cycled 4 fresh standalone contracts (4m timer reset).")
        }
    }

    fun conductReadinessDrill() {
        val state = uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val bonusCredits = 16.0 + TacticalCalculator.facilityLevel(state.facilities, FacilityType.COMMAND_HQ) * 3.0
            val bonusSupplies = 8.0 + TacticalCalculator.facilityLevel(state.facilities, FacilityType.LOGISTICS_DEPOT) * 2.0
            val bonusIntel = 2.0
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = cur.credits + bonusCredits,
                    supplies = cur.supplies + bonusSupplies,
                    intel = cur.intel + bonusIntel,
                    lastSimulatedAtMillis = now
                )
            }
            showBanner("HQ Readiness Drill: +${bonusCredits.roundToInt()} CR, +${bonusSupplies.roundToInt()} SUP, +${bonusIntel.roundToInt()} INT")
        }
    }

    fun grantCheatPrestige() {
        val state = uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    prestige = cur.prestige + 100.0,
                    lastSimulatedAtMillis = now
                )
            }
            showBanner("+100 PRS Directorate Contingency Grant.")
        }
    }

    fun upgradeFacility(type: FacilityType) {
        val state = uiState.value
        val current = state.facilities.find { it.type == type } ?: return
        val creditCost = type.upgradeCreditCost(current.level)
        val supplyCost = type.upgradeSupplyCost(current.level)
        if (state.resources.credits < creditCost || state.resources.supplies < supplyCost) {
            showBanner("Insufficient CR or SUP to upgrade ${type.title}.")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - creditCost).coerceAtLeast(0.0),
                    supplies = (cur.supplies - supplyCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveFacility(FacilityState(type = type, level = current.level + 1))
            showBanner("${type.title} upgraded to Lv.${current.level + 1}")
        }
    }

    fun promoteOperator(
        operatorId: String,
        endorsementFocus: PromotionEndorsementFocus? = null
    ) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked && !it.status.isOutOfActiveService } ?: return
        val boardEval = TacticalCalculator.evaluatePromotionBoard(
            operator = op,
            allOperators = state.operators,
            citations = state.citations,
            gearItems = state.gearItems,
            facilities = state.facilities
        )
        val nextRank = boardEval.nextRank ?: run {
            showBanner("${op.callsign} already holds the highest Army rank (GEN).")
            return
        }
        if (boardEval.isCrossTierGate && !boardEval.allMeritsMet) {
            val blocker = boardEval.meritChecks.firstOrNull { !it.satisfied }
            showBanner(
                if (blocker != null) {
                    "${op.callsign} Board Blocked — ${blocker.label}: ${blocker.statusText}"
                } else {
                    "${op.callsign} has not met Promotion Board merit prerequisites."
                }
            )
            return
        }
        val crCost = boardEval.creditCost
        val prsCost = boardEval.prestigeCost
        if (state.resources.credits < crCost || state.resources.prestige < prsCost) {
            showBanner("Need ${crCost.toInt()} CR and $prsCost PRS to promote ${op.callsign} to ${nextRank.code}.")
            return
        }

        val tierChanged = boardEval.isCrossTierGate
        val nextAuthorizedTier = if (nextRank.tier.order > op.authorizedTier.order) {
            nextRank.tier
        } else {
            op.authorizedTier
        }
        val targetXp = maxOf(op.xp, nextRank.xpRequired)
        val bonus = if (tierChanged) endorsementFocus?.statBonus else null
        val progressedOp = applyTierProgressionOnRankUp(
            op = op.copy(
                xp = targetXp,
                authorizedTier = nextAuthorizedTier,
                baseFirepower = op.baseFirepower + (bonus?.firepower ?: 0),
                baseStealth = op.baseStealth + (bonus?.stealth ?: 0),
                baseTech = op.baseTech + (bonus?.tech ?: 0),
                baseResilience = op.baseResilience + (bonus?.resilience ?: 0)
            ),
            prevRank = op.rank,
            newRank = nextRank
        )

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - crCost).coerceAtLeast(0.0),
                    prestige = (cur.prestige - prsCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveOperator(progressedOp)
            val msg = if (tierChanged) {
                val focusNote = endorsementFocus?.let { " • Endorsed: ${it.title}" } ?: ""
                "${op.callsign} pinned ${nextRank.title} (${nextRank.payGrade}) — ${nextRank.tier.shortLabel} Dress unlocked$focusNote!"
            } else {
                "${op.callsign} promoted to ${nextRank.title} (${nextRank.code} / ${nextRank.payGrade})."
            }
            showBanner(msg)
        }
    }

    private fun applyTierProgressionOnRankUp(
        op: Operator,
        prevRank: OperatorRank,
        newRank: OperatorRank
    ): Operator {
        if (newRank.tier == prevRank.tier || newRank.tier.order < prevRank.tier.order) return op
        val updatedAlphasDress = when (newRank.tier) {
            RankTier.SOLDIER -> DressUniformStyle.ARMY_SERVICE_GREEN
            RankTier.NCO, RankTier.SNCO -> DressUniformStyle.NCO_PARADE_BLUES
            RankTier.OFFICER -> DressUniformStyle.OFFICER_SERVICE_BLUES
            RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
        }
        val updatedMessDress = when (newRank.tier) {
            RankTier.SOLDIER, RankTier.NCO, RankTier.SNCO -> DressUniformStyle.ENLISTED_MESS_BLUES
            RankTier.OFFICER -> DressUniformStyle.OFFICER_MESS_BLUES
            RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_MESS_FORMAL
        }
        val updatedDress = if (op.dressStyle.isCutawayMess) updatedMessDress else updatedAlphasDress
        val updatedServiceCap = when (newRank.tier) {
            RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
            RankTier.NCO, RankTier.SNCO -> HeadgearStyle.NCO_SERVICE_CAP
            RankTier.OFFICER -> HeadgearStyle.OFFICER_PEAKED_CAP
            RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
        }
        val updatedHeadgear = if (op.headgearStyle.kind == HeadgearKind.BERET) {
            op.headgearStyle
        } else {
            updatedServiceCap
        }
        val nextAlphas = op.wardrobe.alphas.copy(
            dressStyle = updatedAlphasDress,
            headgearStyle = op.wardrobe.alphas.headgearStyle?.takeIf {
                it.kind != HeadgearKind.BERET && it.requiredTier.order >= newRank.tier.order
            } ?: updatedServiceCap
        )
        val nextMess = op.wardrobe.mess.copy(
            dressStyle = op.wardrobe.mess.dressStyle?.takeIf {
                it.isCutawayMess && it.requiredTier.order >= newRank.tier.order
            } ?: updatedMessDress,
            headgearStyle = op.wardrobe.mess.headgearStyle?.takeIf {
                it.kind != HeadgearKind.BERET && it.requiredTier.order >= newRank.tier.order
            } ?: updatedServiceCap
        )
        val nextBravos = op.wardrobe.bravos.copy(
            dressStyle = updatedAlphasDress
        )
        return op.copy(
            dressStyle = updatedDress,
            headgearStyle = updatedHeadgear,
            wardrobe = op.wardrobe.copy(
                bravos = nextBravos,
                alphas = nextAlphas,
                mess = nextMess
            )
        )
    }

    fun startDeployment(
        mission: MissionTemplate,
        selectedOperatorIds: List<String>,
        squadId: String? = null
    ) {
        val state = uiState.value
        if (mission.scope != MissionScope.TRAINING && state.occupiedDeploymentSlots >= state.maxDeploymentSlots) {
            showBanner("All HQ tasking slots occupied.")
            return
        }
        if (selectedOperatorIds.size !in mission.minOperators..mission.maxOperators) {
            showBanner("Select ${mission.minOperators}–${mission.maxOperators} soldiers for ${mission.title}.")
            return
        }
        val deployedIds = state.deployments.flatMap { it.operatorIds }.toSet()
        val chosenOps = state.operators.filter {
            it.id in selectedOperatorIds &&
                TacticalCalculator.canOperatorDeployOnMission(
                    operator = it,
                    mission = mission,
                    deployedOpIds = deployedIds,
                    gearItems = state.gearItems,
                    citations = state.citations,
                    facilities = state.facilities
                ).eligible
        }
        if (chosenOps.size != selectedOperatorIds.size) {
            val blocked = state.operators.firstOrNull {
                it.id in selectedOperatorIds &&
                    !TacticalCalculator.canOperatorDeployOnMission(
                        operator = it,
                        mission = mission,
                        deployedOpIds = deployedIds,
                        gearItems = state.gearItems,
                        citations = state.citations,
                        facilities = state.facilities
                    ).eligible
            }
            val reason = blocked?.let {
                TacticalCalculator.canOperatorDeployOnMission(
                    operator = it,
                    mission = mission,
                    deployedOpIds = deployedIds,
                    gearItems = state.gearItems,
                    citations = state.citations,
                    facilities = state.facilities
                ).reasonText
            }?.takeIf { it.isNotBlank() }
            showBanner(reason ?: "One or more selected personnel are unavailable.")
            return
        }
        if (mission.campaignId != null &&
            !TacticalCalculator.isCampaignStageUnlocked(
                mission = mission,
                completedStageIds = state.completedCampaignStageIds,
                completedCampaignIds = state.completedCampaignIds
            )
        ) {
            val campaign = GameCatalog.campaigns.find { it.id == mission.campaignId }
            val msg = if (campaign != null && !TacticalCalculator.isCampaignUnlocked(campaign, state.completedCampaignIds)) {
                "Complete Campaign ${campaign.order - 1} first to unlock ${campaign.title}."
            } else {
                val stageOrder = mission.campaignStageOrder ?: 2
                "Complete Stage ${stageOrder - 1} of ${campaign?.title ?: "campaign"} first."
            }
            showBanner(msg)
            return
        }
        val effectiveSupplyCost = (mission.supplyCost * TacticalCalculator.supplyCostMultiplier(
            facilities = state.facilities,
            hqStaffSummary = state.hqStaffSummary,
            completedCampaignIds = state.completedCampaignIds
        )).roundToInt().toDouble()
        if (state.resources.supplies < effectiveSupplyCost) {
            showBanner("Need ${effectiveSupplyCost.toInt()} SUP to deploy.")
            return
        }

        val activeBonus = SquadCohesionRules.evaluateActiveBonus(chosenOps, state.squads)
        val resolvedSquadId = squadId?.takeIf { id -> state.squads.any { it.id == id } }
            ?: activeBonus?.squad?.id

        val durationMs = (mission.durationSeconds * 1000.0 * TacticalCalculator.durationMultiplier(state.facilities, state.hqStaffSummary)).toLong()
        val effPct = TacticalCalculator.missionEfficiencyPct(
            mission = mission,
            selectedOperators = chosenOps,
            gearItems = state.gearItems,
            citations = state.citations,
            facilities = state.facilities,
            hqStaffCount = state.hqStaffCount,
            hqStaffSummary = state.hqStaffSummary,
            squads = state.squads,
            inspectionBuffActive = state.inspectionBuffActive,
            smartWardrobeEnabled = state.smartWardrobeEnabled
        )
        val previewOddsPct = if (mission.risk.isTraining) {
            TacticalCalculator.missionSuccessOddsPct(
                mission = mission,
                selectedOperators = chosenOps,
                gearItems = state.gearItems,
                citations = state.citations,
                facilities = state.facilities,
                hqStaffCount = state.hqStaffCount,
                hqStaffSummary = state.hqStaffSummary,
                squads = state.squads,
                smartWardrobeEnabled = state.smartWardrobeEnabled
            )
        } else {
            TacticalCalculator.squadAvgSurvivalPct(
                mission = mission,
                selectedOperators = chosenOps,
                gearItems = state.gearItems,
                citations = state.citations,
                facilities = state.facilities,
                hqStaffCount = state.hqStaffCount,
                hqStaffSummary = state.hqStaffSummary,
                squads = state.squads
            )
        }
        val now = System.currentTimeMillis()
        val deployment = ActiveDeployment(
            id = "dep_${UUID.randomUUID().toString().take(8)}",
            templateId = mission.id,
            operatorIds = selectedOperatorIds,
            startedAtMillis = now,
            durationMillis = durationMs,
            efficiencyPct = effPct,
            avgSurvivalPct = previewOddsPct,
            squadId = resolvedSquadId
        )

        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(
                    supplies = (cur.supplies - effectiveSupplyCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveDeployment(deployment)
            val label = if (mission.risk.isTraining) "est. pass" else "est. survival"
            val squadTag = resolvedSquadId?.let { id -> state.squads.find { it.id == id }?.name }
            val prefix = if (squadTag != null) "Dispatched $squadTag: " else "Dispatched: "
            showBanner("$prefix${mission.title} (${previewOddsPct}% $label)")
        }
    }

    fun claimDeployment(deploymentId: String) {
        val state = uiState.value
        val deployment = state.deployments.find { it.id == deploymentId } ?: return
        val mission = GameCatalog.missions.find { it.id == deployment.templateId } ?: return
        val now = System.currentTimeMillis()
        if (!deployment.isComplete(now)) return

        val targetOps = state.operators.filter { it.id in deployment.operatorIds }
        val kitBonus = TacticalCalculator.evaluateMissionKitBonus(
            mission = mission,
            selectedOperators = targetOps,
            smartWardrobeEnabled = state.smartWardrobeEnabled
        )
        val mult = deployment.efficiencyPct / 100.0
        val campaignXpPrestigeMult = TacticalCalculator.campaignPrestigeAndXpMultiplier(state.completedCampaignIds)
        val xpEach = (mission.rewardXp * mult * state.hqStaffSummary.xpMultiplier * kitBonus.xpMult * campaignXpPrestigeMult).roundToInt()

        val updatedOperators = mutableListOf<Operator>()
        val updatedGear = mutableListOf<GearItem>()
        val newDeeds = mutableListOf<PendingDeed>()
        val outcomes = mutableListOf<OperatorDebriefOutcome>()

        val missionSucceeded: Boolean
        if (mission.risk.isTraining) {
            var anyPassed = false
            for (op in targetOps) {
                val opGear = state.gearItems.filter { it.equippedByOperatorId == op.id }
                val passOdds = TacticalCalculator.schoolPassOddsPct(
                    mission = mission,
                    operator = op,
                    citations = state.citations,
                    hqStaffSummary = state.hqStaffSummary
                )
                val passed = Random.nextInt(100) < passOdds
                if (passed) anyPassed = true

                val nextAttempts = (op.schoolAttempts[mission.id] ?: 0) + 1
                val updatedAttempts = op.schoolAttempts + (mission.id to nextAttempts)
                val outcome = if (passed) OperatorOutcomeType.TRAINING_PASSED else OperatorOutcomeType.TRAINING_FAILED
                val opXp = if (passed) xpEach else (xpEach * 0.35).roundToInt().coerceAtLeast(10)
                val prevRank = op.rank
                val newXp = OperatorRank.clampXpToAuthorizedTier(op.xp + opXp, op.authorizedTier)
                val newRank = OperatorRank.forXp(newXp, op.authorizedTier)
                // Training never counts toward combat or career operation totals for valor/awards
                val updatedCompletedIds = if (passed) op.completedMissionIds + mission.id else op.completedMissionIds
                val nextStatus = if (op.status == OperatorStatus.HQ_STAFF) OperatorStatus.HQ_STAFF else OperatorStatus.READY

                val gradBonus = if (passed) mission.schoolRequirements?.graduationStatBonus else null
                val statGainNote = if (gradBonus != null && gradBonus.totalPower > 0) {
                    val parts = buildList {
                        if (gradBonus.firepower > 0) add("+${gradBonus.firepower} FP")
                        if (gradBonus.stealth > 0) add("+${gradBonus.stealth} ST")
                        if (gradBonus.tech > 0) add("+${gradBonus.tech} TC")
                        if (gradBonus.resilience > 0) add("+${gradBonus.resilience} RS")
                    }
                    " Base stats raised (${parts.joinToString(", ")})."
                } else {
                    ""
                }

                val updatedOp = op.copy(
                    xp = newXp,
                    baseFirepower = op.baseFirepower + (gradBonus?.firepower ?: 0),
                    baseStealth = op.baseStealth + (gradBonus?.stealth ?: 0),
                    baseTech = op.baseTech + (gradBonus?.tech ?: 0),
                    baseResilience = op.baseResilience + (gradBonus?.resilience ?: 0),
                    completedMissionIds = updatedCompletedIds,
                    schoolAttempts = updatedAttempts,
                    status = nextStatus
                )
                updatedOperators.add(updatedOp)

                val existingAndQueuedMedals = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet() +
                    state.pendingDeeds.filter { it.operatorId == op.id }.map { it.recommendedMedalId }.toSet()
                val (recMedalId, baseDeedText) = TacticalCalculator.recommendedMedalForMissionDeed(
                    mission = mission,
                    operator = updatedOp,
                    outcome = outcome,
                    equippedGear = opGear,
                    existingCitationMedalIds = existingAndQueuedMedals,
                    missionSucceeded = passed,
                    rollSeedPct = Random.nextInt(100)
                )
                val maxAttempts = mission.schoolRequirements?.maxAttempts
                val attemptNote = if (!passed && maxAttempts != null) {
                    val left = (maxAttempts - nextAttempts).coerceAtLeast(0)
                    if (left == 0) " Washed out (0 attempts left)." else " Attempts used: $nextAttempts/$maxAttempts."
                } else {
                    ""
                }
                val deedText = baseDeedText + statGainNote + attemptNote

                if (recMedalId != null) {
                    newDeeds.add(
                        PendingDeed(
                            id = "deed_${UUID.randomUUID().toString().take(8)}",
                            operatorId = op.id,
                            missionTitle = mission.title,
                            deedSummary = deedText,
                            recommendedMedalId = recMedalId,
                            createdAtMillis = now
                        )
                    )
                }

                outcomes.add(
                    OperatorDebriefOutcome(
                        operatorId = op.id,
                        callsign = op.callsign,
                        rankCode = newRank.code,
                        outcome = outcome,
                        deedText = deedText,
                        recommendedMedalId = recMedalId,
                        xpEarned = opXp,
                        promotedToRankCode = if (newRank != prevRank) newRank.code else null,
                        readyForCategoryRankCode = OperatorRank.nextRank(newRank)
                            ?.takeIf { it.tier != newRank.tier && newXp >= it.xpRequired }
                            ?.code
                    )
                )
            }
            missionSucceeded = anyPassed
        } else {
            val successOdds = TacticalCalculator.missionSuccessOddsPct(
                mission = mission,
                selectedOperators = targetOps,
                gearItems = state.gearItems,
                citations = state.citations,
                facilities = state.facilities,
                hqStaffCount = state.hqStaffCount,
                hqStaffSummary = state.hqStaffSummary,
                squads = state.squads,
                smartWardrobeEnabled = state.smartWardrobeEnabled
            )
            missionSucceeded = Random.nextInt(100) < successOdds
            val activeCohesionBonus = SquadCohesionRules.evaluateActiveBonus(targetOps, state.squads)
            val cohesionSurvBonus = activeCohesionBonus?.survivalBonusPct ?: 0

            var petitionAlreadyActive = state.operators.any {
                it.unlocked && !it.status.isOutOfActiveService && (it.transferRequested || it.retirementRequested)
            }

            for (op in targetOps) {
                val opGear = state.gearItems.filter { it.equippedByOperatorId == op.id }
                val baseSurvivalPct = (TacticalCalculator.operatorSurvivalPct(
                    mission = mission,
                    operator = op,
                    gearItems = state.gearItems,
                    citations = state.citations,
                    facilities = state.facilities,
                    hqStaffCount = state.hqStaffCount,
                    hqStaffSummary = state.hqStaffSummary
                ) + cohesionSurvBonus).coerceIn(42, 98)
                val survivalPct = if (missionSucceeded) baseSurvivalPct else (baseSurvivalPct - 8).coerceAtLeast(38)
                val roll = Random.nextInt(100)
                val outcome = when {
                    roll < survivalPct -> {
                        if (mission.risk.baseDangerPenalty >= 14 && roll > survivalPct - 10) {
                            OperatorOutcomeType.WOUNDED
                        } else {
                            OperatorOutcomeType.SAFE
                        }
                    }
                    roll < survivalPct + 12 -> OperatorOutcomeType.WOUNDED
                    else -> OperatorOutcomeType.FALLEN
                }

                val wasHqStaff = op.status == OperatorStatus.HQ_STAFF
                val nextStatus = when (outcome) {
                    OperatorOutcomeType.SAFE,
                    OperatorOutcomeType.STAFF_SUPPORT,
                    OperatorOutcomeType.TRAINING_PASSED,
                    OperatorOutcomeType.TRAINING_FAILED ->
                        if (wasHqStaff) OperatorStatus.HQ_STAFF else OperatorStatus.READY
                    OperatorOutcomeType.WOUNDED -> OperatorStatus.RECOVERING
                    OperatorOutcomeType.FALLEN -> OperatorStatus.FALLEN
                }
                val recoveryAt = if (outcome == OperatorOutcomeType.WOUNDED) now + 45_000L else 0L

                if (outcome == OperatorOutcomeType.FALLEN) {
                    opGear.forEach { gear -> updatedGear.add(gear.copy(equippedByOperatorId = null)) }
                }

                val opXp = if (missionSucceeded) xpEach else (xpEach * 0.45).roundToInt().coerceAtLeast(15)
                val prevRank = op.rank
                val newXp = OperatorRank.clampXpToAuthorizedTier(op.xp + opXp, op.authorizedTier)
                val newRank = OperatorRank.forXp(newXp, op.authorizedTier)
                val isCombatOp = mission.scope == MissionScope.FIELD_COMBAT
                // Failed deployments still count toward career deployment totals, while combatMissionsCompleted tracks FIELD_COMBAT only
                val newOpsCount = if (outcome != OperatorOutcomeType.FALLEN) op.missionsCompleted + 1 else op.missionsCompleted
                val newCombatOpsCount = if (isCombatOp && outcome != OperatorOutcomeType.FALLEN) {
                    op.combatMissionsCompleted + 1
                } else {
                    op.combatMissionsCompleted
                }
                val totalCareerOps = newOpsCount + op.staffOpsSupported
                val cooldownCleared = totalCareerOps >= op.petitionCooldownUntilOps
                val updatedCompletedIds = if (missionSucceeded && outcome != OperatorOutcomeType.FALLEN) {
                    op.completedMissionIds + mission.id
                } else {
                    op.completedMissionIds
                }
                val updatedHighestRisk = if (isCombatOp && missionSucceeded && outcome != OperatorOutcomeType.FALLEN) {
                    val prev = op.highestRiskSurvived
                    if (prev == null || mission.risk.baseDangerPenalty > prev.baseDangerPenalty) mission.risk else prev
                } else {
                    op.highestRiskSurvived
                }
                val updatedWoundedCount = if (outcome == OperatorOutcomeType.WOUNDED) op.timesWounded + 1 else op.timesWounded
                // Campaign missions record the squad patch worn; the newest campaign becomes the combat patch
                val updatedCombatPatchHistory = mission.campaignId?.let { campaignId ->
                    val wornPatch = UniformRegaliaRules.resolveUnitPatch(op, state.squads)
                    if (wornPatch == UnitPatchStyle.NONE) {
                        op.combatPatchHistory
                    } else {
                        op.combatPatchHistory.recordCampaignPatch(campaignId, wornPatch, System.currentTimeMillis())
                    }
                } ?: op.combatPatchHistory

                val askTransfer = if (
                    !wasHqStaff &&
                    !petitionAlreadyActive &&
                    cooldownCleared &&
                    missionSucceeded &&
                    outcome == OperatorOutcomeType.SAFE &&
                    newRank.isCareerEligibleForStaffOrTransfer &&
                    newOpsCount >= TacticalCalculator.MIN_TRANSFER_OPS &&
                    !op.transferRequested &&
                    !op.retirementRequested
                ) {
                    (Random.nextInt(100) < 6).also { triggered ->
                        if (triggered) petitionAlreadyActive = true
                    }
                } else {
                    op.transferRequested
                }

                val postMissionCandidate = op.copy(
                    xp = newXp,
                    missionsCompleted = newOpsCount,
                    combatMissionsCompleted = newCombatOpsCount,
                    status = nextStatus
                )
                val askRetirement = if (
                    !wasHqStaff &&
                    !petitionAlreadyActive &&
                    cooldownCleared &&
                    missionSucceeded &&
                    outcome == OperatorOutcomeType.SAFE &&
                    !askTransfer &&
                    !op.transferRequested &&
                    !op.retirementRequested &&
                    TacticalCalculator.evaluateRetirementEligibility(postMissionCandidate).eligible
                ) {
                    (Random.nextInt(100) < 7).also { triggered ->
                        if (triggered) petitionAlreadyActive = true
                    }
                } else {
                    op.retirementRequested
                }

                val updatedOp = op.copy(
                    xp = newXp,
                    missionsCompleted = newOpsCount,
                    combatMissionsCompleted = newCombatOpsCount,
                    completedMissionIds = updatedCompletedIds,
                    highestRiskSurvived = updatedHighestRisk,
                    timesWounded = updatedWoundedCount,
                    combatPatchHistory = updatedCombatPatchHistory,
                    status = nextStatus,
                    hqRole = if (outcome == OperatorOutcomeType.FALLEN) null else op.hqRole,
                    recoveryUntilMillis = recoveryAt,
                    transferRequested = askTransfer,
                    retirementRequested = if (outcome == OperatorOutcomeType.FALLEN) false else askRetirement
                )
                updatedOperators.add(updatedOp)

                val existingAndQueuedMedals = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet() +
                    state.pendingDeeds.filter { it.operatorId == op.id }.map { it.recommendedMedalId }.toSet()
                val effectiveCampaignCitations = state.citations + newDeeds.mapNotNull { deed ->
                    if (TacticalCalculator.campaignCapForAward(deed.recommendedMedalId) != null) {
                        AwardedMedal(
                            id = deed.id,
                            operatorId = deed.operatorId,
                            medalId = deed.recommendedMedalId,
                            citationNote = deed.deedSummary,
                            missionTitle = deed.missionTitle,
                            awardedAtMillis = deed.createdAtMillis
                        )
                    } else null
                }
                val (recMedalId, deedText) = TacticalCalculator.recommendedMedalForMissionDeed(
                    mission = mission,
                    operator = updatedOp,
                    outcome = outcome,
                    equippedGear = opGear,
                    existingCitationMedalIds = existingAndQueuedMedals,
                    missionSucceeded = missionSucceeded,
                    rollSeedPct = Random.nextInt(100),
                    allCampaignCitations = effectiveCampaignCitations
                )

                if (recMedalId != null) {
                    newDeeds.add(
                        PendingDeed(
                            id = "deed_${UUID.randomUUID().toString().take(8)}",
                            operatorId = op.id,
                            missionTitle = mission.title,
                            deedSummary = deedText,
                            recommendedMedalId = recMedalId,
                            createdAtMillis = now
                        )
                    )
                }

                outcomes.add(
                    OperatorDebriefOutcome(
                        operatorId = op.id,
                        callsign = op.callsign,
                        rankCode = newRank.code,
                        outcome = outcome,
                        deedText = deedText,
                        recommendedMedalId = recMedalId,
                        xpEarned = opXp,
                        promotedToRankCode = if (outcome != OperatorOutcomeType.FALLEN && newRank != prevRank) newRank.code else null,
                        readyForCategoryRankCode = if (outcome != OperatorOutcomeType.FALLEN) {
                            OperatorRank.nextRank(newRank)
                                ?.takeIf { it.tier != newRank.tier && newXp >= it.xpRequired }
                                ?.code
                        } else {
                            null
                        }
                    )
                )
            }
        }

        val campaignCreditMult = TacticalCalculator.campaignCreditYieldMultiplier(state.completedCampaignIds)
        val campaignIntelMult = TacticalCalculator.campaignIntelYieldMultiplier(state.completedCampaignIds)
        val campaignPrestigeMult = TacticalCalculator.campaignPrestigeAndXpMultiplier(state.completedCampaignIds)
        val creditsEarned = if (missionSucceeded) {
            (mission.rewardCredits * mult * kitBonus.creditMult * campaignCreditMult).roundToInt().toDouble()
        } else {
            (mission.rewardCredits * mult * 0.25).roundToInt().toDouble()
        }
        val suppliesEarned = if (missionSucceeded) {
            (mission.rewardSupplies * mult).roundToInt().toDouble()
        } else if (mission.risk.isTraining) {
            0.0
        } else {
            (mission.rewardSupplies * mult * 0.25).roundToInt().toDouble()
        }
        val intelEarned = if (missionSucceeded) {
            (mission.rewardIntel * mult * campaignIntelMult).roundToInt().toDouble()
        } else if (mission.risk.isTraining) {
            0.0
        } else {
            (mission.rewardIntel * mult * 0.25).roundToInt().toDouble()
        }
        val inspectionPrestigeMult = if (state.inspectionBuffActive) 1.10 else 1.0
        val prestigeEarned = if (missionSucceeded) {
            (mission.rewardPrestige * mult * state.hqStaffSummary.prestigeMultiplier * inspectionPrestigeMult * kitBonus.prestigeMult * campaignPrestigeMult).roundToInt().toDouble()
        } else {
            0.0
        }

        // HQ Staff remaining at HQ continue earning XP & ops supported; citations only on successful non-training ops
        val staffOps = state.operators.filter {
            it.unlocked && it.status == OperatorStatus.HQ_STAFF && it.id !in deployment.operatorIds
        }
        for (staff in staffOps) {
            val staffXp = (xpEach * 0.30).roundToInt().coerceAtLeast(12)
            val nextSupportCount = staff.staffOpsSupported + 1
            val prevStaffRank = staff.rank
            val nextStaffXp = OperatorRank.clampXpToAuthorizedTier(staff.xp + staffXp, staff.authorizedTier)
            val nextStaffRank = OperatorRank.forXp(nextStaffXp, staff.authorizedTier)
            val readyGateCode = OperatorRank.nextRank(nextStaffRank)
                ?.takeIf { it.tier != nextStaffRank.tier && nextStaffXp >= it.xpRequired }
                ?.code
            val candidateStaff = staff.copy(
                xp = nextStaffXp,
                staffOpsSupported = nextSupportCount
            )
            var staffPetitionBlocked = state.operators.any {
                it.unlocked && !it.status.isOutOfActiveService && (it.transferRequested || it.retirementRequested)
            } || updatedOperators.any {
                it.unlocked && !it.status.isOutOfActiveService && (it.transferRequested || it.retirementRequested)
            }
            val staffTotalOps = staff.missionsCompleted + nextSupportCount
            val staffAskRetirement = if (
                !staffPetitionBlocked &&
                staffTotalOps >= staff.petitionCooldownUntilOps &&
                missionSucceeded &&
                !mission.risk.isTraining &&
                !staff.retirementRequested &&
                !staff.transferRequested &&
                nextSupportCount >= TacticalCalculator.MIN_HQ_STAFF_RETIREMENT_SUPPORT_OPS &&
                staffTotalOps >= TacticalCalculator.MIN_HQ_STAFF_RETIREMENT_TOTAL_OPS &&
                TacticalCalculator.evaluateRetirementEligibility(candidateStaff).eligible
            ) {
                (Random.nextInt(100) < 5).also { triggered ->
                    if (triggered) staffPetitionBlocked = true
                }
            } else {
                staff.retirementRequested
            }
            val updatedStaff = candidateStaff.copy(retirementRequested = staffAskRetirement)
            updatedOperators.add(updatedStaff)
            val earnsStaffAward = missionSucceeded &&
                !mission.risk.isTraining &&
                (mission.risk.baseDangerPenalty >= 14 || nextSupportCount % 2 == 0)
            if (earnsStaffAward) {
                val existingAndQueuedMedals = state.citations.filter { it.operatorId == staff.id }.map { it.medalId }.toSet() +
                    state.pendingDeeds.filter { it.operatorId == staff.id }.map { it.recommendedMedalId }.toSet()
                val effectiveCampaignCitations = state.citations + newDeeds.mapNotNull { deed ->
                    if (TacticalCalculator.campaignCapForAward(deed.recommendedMedalId) != null) {
                        AwardedMedal(
                            id = deed.id,
                            operatorId = deed.operatorId,
                            medalId = deed.recommendedMedalId,
                            citationNote = deed.deedSummary,
                            missionTitle = deed.missionTitle,
                            awardedAtMillis = deed.createdAtMillis
                        )
                    } else null
                }
                val (staffMedalId, staffDeedText) = TacticalCalculator.recommendedMedalForHqStaffSupport(
                    operator = updatedStaff,
                    mission = mission,
                    existingCitationMedalIds = existingAndQueuedMedals,
                    allCampaignCitations = effectiveCampaignCitations
                )
                if (staffMedalId != null) {
                    newDeeds.add(
                        PendingDeed(
                            id = "deed_${UUID.randomUUID().toString().take(8)}",
                            operatorId = staff.id,
                            missionTitle = "HQ Support: ${mission.title}",
                            deedSummary = staffDeedText,
                            recommendedMedalId = staffMedalId,
                            createdAtMillis = now
                        )
                    )
                    outcomes.add(
                        OperatorDebriefOutcome(
                            operatorId = staff.id,
                            callsign = staff.callsign,
                            rankCode = nextStaffRank.code,
                            outcome = OperatorOutcomeType.STAFF_SUPPORT,
                            deedText = staffDeedText,
                            recommendedMedalId = staffMedalId,
                            xpEarned = staffXp,
                            promotedToRankCode = if (nextStaffRank != prevStaffRank) nextStaffRank.code else null,
                            readyForCategoryRankCode = readyGateCode
                        )
                    )
                } else if (nextStaffRank != prevStaffRank || readyGateCode != null) {
                    outcomes.add(
                        OperatorDebriefOutcome(
                            operatorId = staff.id,
                            callsign = staff.callsign,
                            rankCode = nextStaffRank.code,
                            outcome = OperatorOutcomeType.STAFF_SUPPORT,
                            deedText = if (nextStaffRank != prevStaffRank) {
                                "Staff ops support logged and advanced to ${nextStaffRank.title} (${nextStaffRank.code})."
                            } else {
                                "Staff ops support logged — eligible for player promotion to $readyGateCode."
                            },
                            recommendedMedalId = null,
                            xpEarned = staffXp,
                            promotedToRankCode = if (nextStaffRank != prevStaffRank) nextStaffRank.code else null,
                            readyForCategoryRankCode = readyGateCode
                        )
                    )
                }
            } else if (nextStaffRank != prevStaffRank || readyGateCode != null) {
                outcomes.add(
                    OperatorDebriefOutcome(
                        operatorId = staff.id,
                        callsign = staff.callsign,
                        rankCode = nextStaffRank.code,
                        outcome = OperatorOutcomeType.STAFF_SUPPORT,
                        deedText = if (nextStaffRank != prevStaffRank) {
                            "Staff ops support logged and advanced to ${nextStaffRank.title} (${nextStaffRank.code})."
                        } else {
                            "Staff ops support logged — eligible for player promotion to $readyGateCode."
                        },
                        recommendedMedalId = null,
                        xpEarned = staffXp,
                        promotedToRankCode = if (nextStaffRank != prevStaffRank) nextStaffRank.code else null,
                        readyForCategoryRankCode = readyGateCode
                    )
                )
            }
        }

        val updatedOpsById = updatedOperators.associateBy { it.id }
        val effectiveRoster = state.operators.map { updatedOpsById[it.id] ?: it }
        val participatingSquads = state.squads.filter { squad ->
            targetOps.count { it.id in squad.memberIds } >= 2
        }
        val cohesionResults = participatingSquads.mapNotNull { squad ->
            val postMissionDeployed = targetOps.map { updatedOpsById[it.id] ?: it }
            SquadCohesionRules.calculateCohesionGain(
                squad = squad,
                deployedOperators = postMissionDeployed,
                mission = mission,
                missionSucceeded = missionSucceeded
            )
        }
        val updatedSquadMap = cohesionResults.associate { it.updatedSquad.id to it.updatedSquad }
        val sanitizedSquadsToSave = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads.map { updatedSquadMap[it.id] ?: it },
            operators = effectiveRoster
        ).filter { updated ->
            state.squads.find { it.id == updated.id } != updated
        }
        val kitDebriefNote = kitBonus.summaryLabel?.takeIf { kitBonus.allMatched }
        val cohesionSummaryNote = listOfNotNull(
            cohesionResults.maxByOrNull { it.gainedXp }?.summaryNote,
            kitDebriefNote
        ).joinToString(" • ").ifBlank { null }

        var bonusCredits = 0.0
        var bonusSupplies = 0.0
        var bonusIntel = 0.0
        var bonusPrestige = 0.0
        var campaignProgressNote: String? = null
        var nextCompletedStageIds = state.completedCampaignStageIds
        var nextCompletedCampaignIds = state.completedCampaignIds

        if (missionSucceeded && mission.campaignId != null) {
            val campaign = GameCatalog.campaigns.find { it.id == mission.campaignId }
            val isNewStageClear = mission.id !in state.completedCampaignStageIds
            nextCompletedStageIds = state.completedCampaignStageIds + mission.id
            if (campaign != null) {
                val campaignFinishedNow = campaign.stageMissionIds.all { it in nextCompletedStageIds }
                if (campaignFinishedNow && campaign.id !in state.completedCampaignIds) {
                    nextCompletedCampaignIds = state.completedCampaignIds + campaign.id
                    bonusCredits = campaign.completionBonusCredits
                    bonusSupplies = campaign.completionBonusSupplies
                    bonusIntel = campaign.completionBonusIntel
                    bonusPrestige = campaign.completionBonusPrestige
                    val allDone = TacticalCalculator.isAllCampaignsCleared(nextCompletedCampaignIds)
                    val nextCamp = GameCatalog.campaigns.find { it.order == campaign.order + 1 }
                    val bonusStr = "+${bonusCredits.toInt()} CR, +${bonusSupplies.toInt()} SUP, +${bonusIntel.toInt()} INT, +${bonusPrestige.toInt()} PRS • Perk: ${campaign.theaterBuffLabel}"
                    campaignProgressNote = if (allDone) {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr) — ALL 4 CAMPAIGNS CLEARED! FULL REPLAY UNLOCKED!"
                    } else if (nextCamp != null) {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr) — Unlocked ${nextCamp.title}!"
                    } else {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr)"
                    }
                } else if (isNewStageClear && !state.allCampaignsCleared) {
                    val stageNum = mission.campaignStageOrder ?: 1
                    val totalStages = campaign.stageMissionIds.size
                    if (stageNum < totalStages) {
                        campaignProgressNote = "${campaign.title}: Stage $stageNum/$totalStages Cleared — Stage ${stageNum + 1} Unlocked!"
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.removeDeployment(deployment.id)
            repository.saveOperators(updatedOperators)
            if (sanitizedSquadsToSave.isNotEmpty()) {
                repository.saveSquads(sanitizedSquadsToSave)
            }
            if (updatedGear.isNotEmpty()) {
                repository.saveGearList(updatedGear)
            }
            if (newDeeds.isNotEmpty()) {
                repository.addPendingDeeds(newDeeds)
            }
            repository.updateCommanderState { cur ->
                val curStages = cur.completedCampaignStageIdsCsv.split(",").filter { it.isNotBlank() }.toSet()
                val curCamps = cur.completedCampaignIdsCsv.split(",").filter { it.isNotBlank() }.toSet()
                val mergedStages = curStages + nextCompletedStageIds
                val mergedCamps = curCamps + nextCompletedCampaignIds
                cur.copy(
                    credits = cur.credits + creditsEarned + bonusCredits,
                    supplies = cur.supplies + suppliesEarned + bonusSupplies,
                    intel = cur.intel + intelEarned + bonusIntel,
                    prestige = cur.prestige + prestigeEarned + bonusPrestige,
                    totalMissionsCompleted = cur.totalMissionsCompleted + if (missionSucceeded) 1 else 0,
                    completedCampaignStageIdsCsv = mergedStages.sorted().joinToString(","),
                    completedCampaignIdsCsv = mergedCamps.sorted().joinToString(","),
                    lastSimulatedAtMillis = now
                )
            }
            _lastDebrief.value = DebriefSummary(
                missionTitle = mission.title,
                theaterName = mission.theater.label,
                missionSucceeded = missionSucceeded,
                isTrainingMission = mission.risk.isTraining,
                efficiencyPct = deployment.efficiencyPct,
                creditsEarned = creditsEarned + bonusCredits,
                suppliesEarned = suppliesEarned + bonusSupplies,
                intelEarned = intelEarned + bonusIntel,
                prestigeEarned = prestigeEarned + bonusPrestige,
                operatorOutcomes = outcomes,
                squadCohesionNote = cohesionSummaryNote,
                campaignProgressNote = campaignProgressNote
            )
        }
    }

    fun treatWoundedOperator(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.status == OperatorStatus.RECOVERING } ?: return
        val supplyCost = 20.0
        if (state.resources.supplies < supplyCost) {
            showBanner("Need 20 SUP for expedited Medbay care.")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    supplies = (cur.supplies - supplyCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveOperator(
                op.copy(
                    status = if (op.hqRole != null) OperatorStatus.HQ_STAFF else OperatorStatus.READY,
                    recoveryUntilMillis = 0L
                )
            )
            showBanner("${op.callsign} cleared for duty.")
        }
    }

    fun recruitOperator(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && !it.unlocked } ?: return
        val activeFieldCount = state.operators.count {
            it.unlocked && it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService
        }
        if (activeFieldCount >= state.maxFieldRosterSlots) {
            showBanner("Field roster full (${state.maxFieldRosterSlots} max). Transfer/assign SGT+ to HQ Staff or upgrade Command HQ.")
            return
        }
        if (state.resources.credits < op.recruitCreditCost || state.resources.intel < op.recruitIntelCost) {
            showBanner("Insufficient CR or INT to recruit ${op.callsign}.")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - op.recruitCreditCost).coerceAtLeast(0.0),
                    intel = (cur.intel - op.recruitIntelCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveOperator(op.copy(unlocked = true))
            showBanner("${op.rank.code} ${op.callsign} (${op.rank.tier.shortLabel}) reported to HQ!")
        }
    }

    fun enlistPostBasicSoldier(specialty: OperatorSpecialty) {
        val state = uiState.value
        val activeFieldCount = state.operators.count {
            it.unlocked && it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService
        }
        if (activeFieldCount >= state.maxFieldRosterSlots) {
            showBanner("Field roster full (${state.maxFieldRosterSlots}/${state.maxFieldRosterSlots}). Transfer out, assign SGT+ to HQ Staff, or upgrade HQ.")
            return
        }
        val enlistCost = 45.0
        if (state.resources.credits < enlistCost) {
            showBanner("Need 45 CR to bring a post-Basic soldier onto HQ orders.")
            return
        }
        val recruit = GameCatalog.createPostBasicRecruit(
            id = "op_rec_${UUID.randomUUID().toString().take(6)}",
            specialty = specialty,
            existingCallsigns = state.operators.map { it.callsign }.toSet(),
            seedIndex = state.operators.size + Random.nextInt(100)
        )
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - enlistCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveOperator(recruit)
            repository.addCitation(
                AwardedMedal(
                    id = "cit_${UUID.randomUUID().toString().take(8)}",
                    operatorId = recruit.id,
                    medalId = "medal_basic_training",
                    citationNote = "Completed Basic Combat Training — arrived at HQ post-Basic with no specialty schools.",
                    missionTitle = "Basic Combat Training Graduation",
                    awardedAtMillis = now
                )
            )
            showBanner("${recruit.rank.code} ${recruit.callsign} (${specialty.roleTag}) arrived post-Basic!")
        }
    }

    fun assignToHqStaff(operatorId: String, role: HqStaffRole? = null) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val deployedOpIds = state.deployments.flatMap { it.operatorIds }.toSet()
        if (op.id in deployedOpIds || op.status != OperatorStatus.READY) {
            showBanner("${op.callsign} must be READY at HQ to join HQ Staff.")
            return
        }
        if (!op.rank.isCareerEligibleForStaffOrTransfer) {
            showBanner("Requires ${OperatorRank.SGT.code} (E-5) or higher for HQ Staff billet.")
            return
        }
        if (state.hqStaffCount >= state.maxHqStaffSlots) {
            showBanner("HQ Staff Wing is full (${state.hqStaffCount}/${state.maxHqStaffSlots}). Upgrade Command HQ or transfer staff.")
            return
        }

        val unlockedRoles = TacticalCalculator.unlockedHqRoles(state.facilities)
        val occupiedRoles = state.operators
            .filter { it.unlocked && it.status == OperatorStatus.HQ_STAFF }
            .mapNotNull { it.hqRole }
            .toSet()

        val targetRole = if (role != null) {
            if (role !in unlockedRoles) {
                showBanner("${role.shortCode} unlocks at Command HQ Lv.${role.requiredHqLevel}.")
                return
            }
            if (role in occupiedRoles) {
                showBanner("${role.shortCode} billet is already occupied.")
                return
            }
            val elig = TacticalCalculator.evaluateHqRoleEligibility(role, op)
            if (!elig.eligible) {
                showBanner("${op.callsign} cannot take ${role.shortCode}: ${elig.reasonText}")
                return
            }
            role
        } else {
            unlockedRoles.firstOrNull { r ->
                r !in occupiedRoles && TacticalCalculator.evaluateHqRoleEligibility(r, op).eligible
            } ?: run {
                showBanner("No open HQ Staff billets fit ${op.callsign}'s rank or op experience.")
                return
            }
        }

        val equippedGear = state.gearItems
            .filter { it.equippedByOperatorId == op.id }
            .map { it.copy(equippedByOperatorId = null) }

        val updatedStaffOp = op.copy(
            status = OperatorStatus.HQ_STAFF,
            hqRole = targetRole,
            transferRequested = false,
            retirementRequested = false,
            uniformMode = UniformMode.DRESS_UNIFORM,
            activeWardrobeKit = if (op.activeWardrobeKit == WardrobeKit.FIELD) WardrobeKit.ALPHAS else op.activeWardrobeKit
        )
        val updatedSquads = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads,
            operators = state.operators.map { if (it.id == op.id) updatedStaffOp else it }
        ).filter { updated -> state.squads.find { it.id == updated.id } != updated }

        viewModelScope.launch {
            if (equippedGear.isNotEmpty()) {
                repository.saveGearList(equippedGear)
            }
            repository.saveOperator(updatedStaffOp)
            if (updatedSquads.isNotEmpty()) {
                repository.saveSquads(updatedSquads)
            }
            showBanner("${op.rank.code} ${op.callsign} assigned as ${targetRole.shortCode} (${targetRole.title})!")
        }
    }

    fun reassignHqStaffRole(operatorId: String, newRole: HqStaffRole) {
        val state = uiState.value
        val op = state.operators.find {
            it.id == operatorId && it.unlocked && it.status == OperatorStatus.HQ_STAFF
        } ?: return
        if (op.hqRole == newRole) return

        val unlockedRoles = TacticalCalculator.unlockedHqRoles(state.facilities)
        if (newRole !in unlockedRoles) {
            showBanner("${newRole.shortCode} unlocks at Command HQ Lv.${newRole.requiredHqLevel}.")
            return
        }
        val occupant = state.operators.find {
            it.id != operatorId && it.unlocked && it.status == OperatorStatus.HQ_STAFF && it.hqRole == newRole
        }
        if (occupant != null) {
            showBanner("${newRole.shortCode} is currently held by ${occupant.callsign}.")
            return
        }
        val elig = TacticalCalculator.evaluateHqRoleEligibility(newRole, op)
        if (!elig.eligible) {
            showBanner("${op.callsign} cannot hold ${newRole.shortCode}: ${elig.reasonText}")
            return
        }

        viewModelScope.launch {
            repository.saveOperator(op.copy(hqRole = newRole))
            showBanner("${op.rank.code} ${op.callsign} reassigned to ${newRole.shortCode} (${newRole.title}).")
        }
    }

    fun approveTransferOut(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val deployedOpIds = state.deployments.flatMap { it.operatorIds }.toSet()
        if (op.id in deployedOpIds || op.status == OperatorStatus.DEPLOYED) {
            showBanner("Cannot transfer ${op.callsign} while on active deployment.")
            return
        }
        if (!op.rank.isCareerEligibleForStaffOrTransfer) {
            showBanner("Unit transfer eligibility opens at ${OperatorRank.SGT.code} (E-5).")
            return
        }
        val returnedGear = state.gearItems
            .filter { it.equippedByOperatorId == op.id }
            .map { it.copy(equippedByOperatorId = null) }

        val updatedSquads = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads,
            operators = state.operators.filter { it.id != op.id }
        ).filter { updated -> state.squads.find { it.id == updated.id } != updated }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (returnedGear.isNotEmpty()) {
                repository.saveGearList(returnedGear)
            }
            if (updatedSquads.isNotEmpty()) {
                repository.saveSquads(updatedSquads)
            }
            repository.updateCommanderState { cur ->
                cur.copy(
                    prestige = cur.prestige + 10.0,
                    lastSimulatedAtMillis = now
                )
            }
            repository.deleteOperatorAndRecords(op.id)
            showBanner("${op.rank.code} ${op.callsign} transferred to external unit (+10 PRS). Roster slot freed.")
        }
    }

    fun declineTransferRequest(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.transferRequested } ?: return
        val cooldownTarget = op.missionsCompleted + op.staffOpsSupported + TacticalCalculator.PETITION_COOLDOWN_OPS
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    transferRequested = false,
                    petitionCooldownUntilOps = cooldownTarget
                )
            )
            showBanner("${op.rank.code} ${op.callsign} retained at Aegis HQ.")
        }
    }

    fun approveRetirement(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val deployedOpIds = state.deployments.flatMap { it.operatorIds }.toSet()
        if (op.id in deployedOpIds || op.status == OperatorStatus.DEPLOYED) {
            showBanner("Cannot retire ${op.callsign} while on active deployment.")
            return
        }
        val elig = TacticalCalculator.evaluateRetirementEligibility(op)
        if (!elig.eligible) {
            showBanner("${op.callsign} not yet eligible: ${elig.reasonText}")
            return
        }
        val returnedGear = state.gearItems
            .filter { it.equippedByOperatorId == op.id }
            .map { it.copy(equippedByOperatorId = null) }
        val now = System.currentTimeMillis()
        val preview = TacticalCalculator.previewRetirementLegacy(
            operator = op,
            currentLegacyBps = state.veteranLegacyBps,
            lastRetirementAtMillis = state.lastRetirementAtMillis,
            nowMillis = now
        )

        val retiredOp = op.copy(
            status = OperatorStatus.RETIRED,
            hqRole = null,
            transferRequested = false,
            retirementRequested = false,
            recoveryUntilMillis = 0L,
            uniformMode = UniformMode.DRESS_UNIFORM
        )
        val updatedSquads = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads,
            operators = state.operators.map { if (it.id == op.id) retiredOp else it }
        ).filter { updated -> state.squads.find { it.id == updated.id } != updated }

        viewModelScope.launch {
            if (returnedGear.isNotEmpty()) {
                repository.saveGearList(returnedGear)
            }
            if (updatedSquads.isNotEmpty()) {
                repository.saveSquads(updatedSquads)
            }
            repository.clearPendingDeedsForOperator(op.id)
            repository.updateCommanderState { cur ->
                val livePreview = TacticalCalculator.previewRetirementLegacy(
                    operator = op,
                    currentLegacyBps = cur.veteranLegacyBps,
                    lastRetirementAtMillis = cur.lastRetirementAtMillis,
                    nowMillis = now
                )
                cur.copy(
                    prestige = cur.prestige + livePreview.prestigeAward,
                    veteranLegacyBps = livePreview.resultingBps,
                    retiredVeteransCount = cur.retiredVeteransCount + 1,
                    lastRetirementAtMillis = now,
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveOperator(retiredOp)
            val msg = if (preview.atCap) {
                "${op.rank.code} ${op.callsign} honorably retired (+${preview.prestigeAward} PRS). Veteran Legacy at ${preview.capPctText} cap."
            } else {
                "${op.rank.code} ${op.callsign} honorably retired (+${preview.prestigeAward} PRS • Veteran Legacy ${preview.gainPctText} → ${preview.resultingPctText})."
            }
            showBanner(msg)
        }
    }

    fun denyRetirementRequest(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.retirementRequested } ?: return
        val cooldownTarget = op.missionsCompleted + op.staffOpsSupported + TacticalCalculator.PETITION_COOLDOWN_OPS
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    retirementRequested = false,
                    petitionCooldownUntilOps = cooldownTarget
                )
            )
            showBanner("Retirement denied — ${op.rank.code} ${op.callsign} remains on active duty.")
        }
    }

    fun customizeOperator(
        operatorId: String,
        request: UniformTailorRequest
    ) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId } ?: return
        val sanitizedCallsign = request.callsign.trim().uppercase().take(12).ifBlank { op.callsign }
        val ownedMedalIds = state.citations.filter { it.operatorId == operatorId }.map { it.medalId }.toSet()

        val targetKit = if (request.wardrobeKit.isAvailableFor(op)) {
            request.wardrobeKit
        } else {
            showBanner("${request.wardrobeKit.label} unavailable for HQ Staff — saved to Service Alphas.")
            WardrobeKit.ALPHAS
        }

        val ov = request.tailorOverrides
        val freeOverride = ov.freeTailorOverride

        val currentKitSettings = op.wardrobe.forKit(targetKit)
        val fallbackDress = currentKitSettings.dressStyle
            ?: TacticalCalculator.defaultDressStyleForKit(
                targetKit,
                op.rank.tier,
                op.dressStyle.takeIf {
                    when (targetKit) {
                        WardrobeKit.MESS -> it.isCutawayMess
                        WardrobeKit.ALPHAS, WardrobeKit.BRAVOS -> !it.isCutawayMess
                        WardrobeKit.FIELD -> true
                    }
                }
            )
        val dressCheck = TacticalCalculator.evaluateDressStyleEligibility(request.dressStyle, op, ownedMedalIds, targetKit)
        val finalDress = if (freeOverride || dressCheck.eligible) {
            request.dressStyle
        } else {
            if (request.dressStyle != fallbackDress && targetKit != WardrobeKit.FIELD) {
                showBanner("${request.dressStyle.shortLabel}: ${dressCheck.reasonText}")
            }
            fallbackDress
        }

        val fallbackHeadgear = currentKitSettings.headgearStyle
            ?: TacticalCalculator.defaultHeadgearForKit(
                targetKit,
                op,
                ownedMedalIds,
                op.headgearStyle.takeIf {
                    when (targetKit) {
                        WardrobeKit.BRAVOS -> it.kind == HeadgearKind.BERET
                        WardrobeKit.ALPHAS, WardrobeKit.MESS -> it.kind != HeadgearKind.BERET
                        WardrobeKit.FIELD -> true
                    }
                }
            )
        val headgearCheck = TacticalCalculator.evaluateHeadgearEligibility(request.headgearStyle, op, ownedMedalIds, targetKit)
        val finalHeadgear = if (freeOverride || headgearCheck.eligible) {
            request.headgearStyle
        } else {
            if (request.headgearStyle != fallbackHeadgear && targetKit != WardrobeKit.FIELD) {
                showBanner("${request.headgearStyle.label}: ${headgearCheck.reasonText}")
            }
            fallbackHeadgear
        }

        val glovesCheck = TacticalCalculator.evaluateWhiteGlovesEligibility(op, ownedMedalIds)
        val finalGloves = when {
            targetKit == WardrobeKit.MESS -> request.hasWhiteGloves
            targetKit == WardrobeKit.BRAVOS && !freeOverride -> false
            !request.hasWhiteGloves || freeOverride || glovesCheck.eligible -> request.hasWhiteGloves
            else -> {
                if (request.hasWhiteGloves != (currentKitSettings.hasWhiteGloves ?: op.hasWhiteGloves)) {
                    showBanner("White Gloves: ${glovesCheck.reasonText}")
                }
                currentKitSettings.hasWhiteGloves ?: op.hasWhiteGloves
            }
        }

        val sashCheck = TacticalCalculator.evaluateDressSashEligibility(op, state.squads)
        val finalSash = when {
            targetKit != WardrobeKit.ALPHAS && !freeOverride -> false
            !request.hasDressSash || freeOverride || sashCheck.eligible -> request.hasDressSash
            else -> {
                if (request.hasDressSash != (currentKitSettings.hasDressSash ?: op.hasDressSash)) {
                    showBanner("Ceremonial Sash: ${sashCheck.reasonText}")
                }
                (currentKitSettings.hasDressSash ?: op.hasDressSash) && sashCheck.eligible
            }
        }

        val medalsById = GameCatalog.medals.associateBy { it.id }
        val finalLeftCord = request.equippedLeftCordId?.takeIf { id ->
            id in ownedMedalIds && medalsById[id]?.let {
                it.category == AwardCategory.SHOULDER_CORD &&
                    (freeOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = false))
            } == true
        }
        val finalRightCord = request.equippedRightCordId?.takeIf { id ->
            id in ownedMedalIds && id != finalLeftCord && medalsById[id]?.let {
                it.category == AwardCategory.SHOULDER_CORD &&
                    (freeOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = true))
            } == true
        }

        fun sanitizeSlot(raw: String, predicate: (MedalDefinition) -> Boolean = { true }): String = when {
            raw == UniformTailorOverrides.SLOT_AUTO || raw == UniformTailorOverrides.SLOT_NONE -> raw
            raw in ownedMedalIds && medalsById[raw]?.let(predicate) == true -> raw
            else -> UniformTailorOverrides.SLOT_AUTO
        }

        val finalOverrides = ov.copy(
            neckSlot = sanitizeSlot(ov.neckSlot) { it.isNeckMedallion },
            tabSlot1 = sanitizeSlot(ov.tabSlot1) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape == InsigniaShape.TAB
            },
            tabSlot2 = sanitizeSlot(ov.tabSlot2) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape == InsigniaShape.TAB
            },
            tabSlot3 = sanitizeSlot(ov.tabSlot3) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape == InsigniaShape.TAB
            },
            pinSlot1 = sanitizeSlot(ov.pinSlot1) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape != InsigniaShape.TAB &&
                    (freeOverride || it.isAllowedInChestSlot(ChestBadgeSlot.ABOVE_RACK))
            },
            pinSlot2 = sanitizeSlot(ov.pinSlot2) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape != InsigniaShape.TAB &&
                    (freeOverride || it.isAllowedInChestSlot(ChestBadgeSlot.LEFT_POCKET))
            },
            // Slot 3 accepts any owned chest badge
            pinSlot3 = sanitizeSlot(ov.pinSlot3) {
                it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape != InsigniaShape.TAB
            },
            medalSlot1 = sanitizeSlot(ov.medalSlot1) {
                it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
            },
            medalSlot2 = sanitizeSlot(ov.medalSlot2) {
                it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
            },
            medalSlot3 = sanitizeSlot(ov.medalSlot3) {
                it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
            },
            medalSlot4 = sanitizeSlot(ov.medalSlot4) {
                it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
            }
        )

        val updatedKitSettings = when (targetKit) {
            WardrobeKit.FIELD -> currentKitSettings.copy(camoPattern = request.camoPattern)
            WardrobeKit.BRAVOS -> currentKitSettings.copy(
                dressStyle = finalDress,
                headgearStyle = finalHeadgear,
                dressDisplayMode = if (request.dressDisplayMode == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.RIBBONS
                },
                hasWhiteGloves = false,
                hasDressSash = false
            )
            WardrobeKit.ALPHAS -> currentKitSettings.copy(
                dressStyle = finalDress,
                headgearStyle = finalHeadgear,
                dressDisplayMode = request.dressDisplayMode,
                hasWhiteGloves = finalGloves,
                hasDressSash = finalSash,
                equippedLeftCordId = finalLeftCord,
                equippedRightCordId = finalRightCord,
                cordCustomized = true
            )
            WardrobeKit.MESS -> currentKitSettings.copy(
                dressStyle = finalDress,
                headgearStyle = finalHeadgear,
                dressDisplayMode = if (request.dressDisplayMode == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.FULL_MEDALS
                },
                hasWhiteGloves = finalGloves,
                hasDressSash = false
            )
        }
        var updatedWardrobe = op.wardrobe.withKit(targetKit, updatedKitSettings)
        if (targetKit != WardrobeKit.ALPHAS &&
            (finalLeftCord != op.equippedLeftCordId || finalRightCord != op.equippedRightCordId)
        ) {
            updatedWardrobe = updatedWardrobe.withKit(
                WardrobeKit.ALPHAS,
                updatedWardrobe.alphas.copy(
                    equippedLeftCordId = finalLeftCord,
                    equippedRightCordId = finalRightCord,
                    cordCustomized = true
                )
            )
        }

        val syncTopLevelDress = targetKit == WardrobeKit.ALPHAS || targetKit == op.activeWardrobeKit
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    callsign = sanitizedCallsign,
                    uniformMode = if (op.activeWardrobeKit == WardrobeKit.FIELD) UniformMode.FIELD_TACTICAL else UniformMode.DRESS_UNIFORM,
                    dressDisplayMode = if (syncTopLevelDress && targetKit != WardrobeKit.FIELD) updatedKitSettings.dressDisplayMode ?: op.dressDisplayMode else op.dressDisplayMode,
                    camoPattern = if (targetKit == WardrobeKit.FIELD) request.camoPattern else op.camoPattern,
                    dressStyle = if (syncTopLevelDress && targetKit != WardrobeKit.FIELD) finalDress else op.dressStyle,
                    headgearStyle = if (syncTopLevelDress && targetKit != WardrobeKit.FIELD) finalHeadgear else op.headgearStyle,
                    equippedLeftCordId = finalLeftCord,
                    equippedRightCordId = finalRightCord,
                    hasWhiteGloves = if (targetKit == WardrobeKit.ALPHAS) finalGloves else op.hasWhiteGloves,
                    hasDressSash = if (targetKit == WardrobeKit.ALPHAS) finalSash else op.hasDressSash,
                    tailorOverrides = finalOverrides,
                    wardrobe = updatedWardrobe
                )
            )
            if (sanitizedCallsign != op.callsign) {
                showBanner("Callsign updated to $sanitizedCallsign")
            }
        }
    }

    fun toggleSmartWardrobe() {
        val state = uiState.value
        val next = !state.smartWardrobeEnabled
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    smartWardrobeEnabled = next,
                    lastSimulatedAtMillis = now
                )
            }
            showBanner(
                if (next) {
                    "Smart Duty-Dress ON — soldiers auto-wear Field / Bravos / Alphas / Mess by duty."
                } else {
                    "Smart Duty-Dress OFF — soldiers wear their manually equipped Wardrobe kit."
                }
            )
        }
    }

    fun selectWardrobeKit(operatorId: String, kit: WardrobeKit) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId } ?: return
        if (!kit.isAvailableFor(op)) {
            showBanner("HQ Staff do not deploy in Field Combat kits.")
            return
        }
        val nextMode = if (kit == WardrobeKit.FIELD) UniformMode.FIELD_TACTICAL else UniformMode.DRESS_UNIFORM
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    activeWardrobeKit = kit,
                    uniformMode = nextMode
                )
            )
        }
    }

    fun switchOperatorsToKit(operatorIds: List<String>, kit: WardrobeKit) {
        val state = uiState.value
        val updated = operatorIds.distinct().mapNotNull { id ->
            val op = state.operators.find { it.id == id } ?: return@mapNotNull null
            if (!kit.isAvailableFor(op)) return@mapNotNull null
            op.copy(
                activeWardrobeKit = kit,
                uniformMode = if (kit == WardrobeKit.FIELD) UniformMode.FIELD_TACTICAL else UniformMode.DRESS_UNIFORM
            )
        }
        if (updated.isEmpty()) return
        viewModelScope.launch {
            repository.saveOperators(updated)
            showBanner("Equipped ${updated.size} personnel in ${kit.label}.")
        }
    }

    fun toggleOperatorUniformMode(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId } ?: return
        val nextMode = if (op.uniformMode == UniformMode.FIELD_TACTICAL) {
            UniformMode.DRESS_UNIFORM
        } else if (WardrobeKit.FIELD.isAvailableFor(op)) {
            UniformMode.FIELD_TACTICAL
        } else {
            UniformMode.DRESS_UNIFORM
        }
        val nextKit = if (nextMode == UniformMode.FIELD_TACTICAL) {
            WardrobeKit.FIELD
        } else if (op.activeWardrobeKit == WardrobeKit.FIELD) {
            WardrobeKit.ALPHAS
        } else {
            op.activeWardrobeKit
        }
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    uniformMode = nextMode,
                    activeWardrobeKit = nextKit
                )
            )
        }
    }

    fun toggleDressDisplayMode(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId } ?: return
        val next = when (op.dressDisplayMode) {
            DressDisplayMode.RIBBONS -> DressDisplayMode.FULL_MEDALS
            DressDisplayMode.FULL_MEDALS -> DressDisplayMode.RIBBONS
            DressDisplayMode.CLEAN -> DressDisplayMode.RIBBONS
        }
        val targetKit = if (op.activeWardrobeKit == WardrobeKit.FIELD) WardrobeKit.ALPHAS else op.activeWardrobeKit
        val nextAlphas = op.wardrobe.alphas.copy(dressDisplayMode = next)
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    activeWardrobeKit = targetKit,
                    dressDisplayMode = next,
                    wardrobe = op.wardrobe.withKit(WardrobeKit.ALPHAS, nextAlphas)
                )
            )
        }
    }

    fun equipGear(operatorId: String, gearId: String) {
        val state = uiState.value
        val operator = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val gear = state.gearItems.find { it.id == gearId && it.unlocked } ?: return
        val currentlyDeployedOps = state.deployments.flatMap { it.operatorIds }.toSet()
        if (operatorId in currentlyDeployedOps) {
            showBanner("Cannot swap gear while soldier is deployed.")
            return
        }
        if (gear.equippedByOperatorId != null && gear.equippedByOperatorId in currentlyDeployedOps) {
            showBanner("Gear is currently in the field with another soldier.")
            return
        }
        val equipElig = TacticalCalculator.evaluateGearEquipEligibility(gear, operator)
        if (!equipElig.eligible) {
            showBanner("${operator.callsign} cannot equip ${gear.name}: ${equipElig.reasonText}")
            return
        }

        val toUpdate = mutableListOf<GearItem>()
        state.gearItems.find { it.equippedByOperatorId == operatorId && it.slot == gear.slot }?.let { prev ->
            if (prev.id != gear.id) {
                toUpdate.add(prev.copy(equippedByOperatorId = null))
            }
        }
        toUpdate.add(gear.copy(equippedByOperatorId = operatorId))

        viewModelScope.launch {
            repository.saveGearList(toUpdate)
            showBanner("Equipped ${gear.name}")
        }
    }

    fun unequipGear(gearId: String) {
        val state = uiState.value
        val gear = state.gearItems.find { it.id == gearId } ?: return
        val deployedOps = state.deployments.flatMap { it.operatorIds }.toSet()
        if (gear.equippedByOperatorId in deployedOps) {
            showBanner("Cannot unequip gear while deployed.")
            return
        }
        viewModelScope.launch {
            repository.saveGear(gear.copy(equippedByOperatorId = null))
        }
    }

    fun autoDistributeFieldGear() {
        val state = uiState.value
        val result = TacticalCalculator.autoDistributeFieldGear(
            operators = state.operators,
            gearItems = state.gearItems,
            citations = state.citations,
            facilities = state.facilities,
            deployments = state.deployments
        )
        if (result.targetOperatorCount == 0) {
            showBanner(
                if (result.skippedDeployedCount > 0) {
                    "All field soldiers are currently deployed — gear locked until RTB."
                } else {
                    "No deployable non-HQ field soldiers available to equip."
                }
            )
            return
        }
        if (result.changedItems.isEmpty()) {
            showBanner("Deployable field soldiers already hold their optimal loadouts.")
            return
        }
        viewModelScope.launch {
            repository.saveGearList(result.changedItems)
            val deployedNote = if (result.skippedDeployedCount > 0) {
                " (${result.skippedDeployedCount} deployed kept kit)"
            } else {
                ""
            }
            showBanner(
                "Auto-equipped ${result.totalEquippedCount} gear items across ${result.outfittedOperatorCount} field soldiers$deployedNote."
            )
        }
    }

    fun purchaseOrUpgradeGear(gearId: String) {
        val state = uiState.value
        val gear = state.gearItems.find { it.id == gearId } ?: return
        if (gear.unlocked && gear.tier >= gear.maxTier) {
            showBanner("${gear.name} is already at max Mk.${gear.maxTier}.")
            return
        }
        val reqElig = TacticalCalculator.evaluateGearRequisitionEligibility(
            gear = gear,
            facilities = state.facilities,
            operators = state.operators
        )
        if (!reqElig.eligible) {
            showBanner("${gear.name} locked: ${reqElig.reasonText}")
            return
        }
        val creditCost = gear.nextActionCreditCost()
        val supplyCost = gear.nextActionSupplyCost()
        if (state.resources.credits < creditCost || state.resources.supplies < supplyCost) {
            showBanner("Insufficient CR or SUP for ${gear.name}.")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - creditCost).coerceAtLeast(0.0),
                    supplies = (cur.supplies - supplyCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            val updated = if (!gear.unlocked) {
                gear.copy(unlocked = true, tier = 1)
            } else {
                gear.copy(tier = gear.tier + 1)
            }
            repository.saveGear(updated)
            showBanner(
                if (!gear.unlocked) "Requisitioned ${gear.name}"
                else "Upgraded ${gear.name} to Mk.${updated.tier}"
            )
        }
    }

    fun awardMedal(
        operatorId: String,
        medalId: String,
        citationNote: String,
        missionTitle: String = "HQ Ceremonial Citation"
    ) {
        val state = uiState.value
        val operator = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val medal = GameCatalog.medals.find { it.id == medalId } ?: return

        val ownedMedalIds = state.citations.filter { it.operatorId == operatorId }.map { it.medalId }.toSet()
        val alreadyAwarded = medalId in ownedMedalIds
        val repeatable = TacticalCalculator.isRepeatableAward(medal)
        if (alreadyAwarded && !repeatable) {
            showBanner("${operator.callsign} already holds ${medal.shortName}.")
            return
        }
        val eligibility = TacticalCalculator.evaluateAwardEligibility(
            medal = medal,
            operator = operator,
            ownedMedalIds = ownedMedalIds,
            allCitations = state.citations
        )
        if (!eligibility.eligible) {
            showBanner("${medal.shortName} locked: ${eligibility.reasonText}")
            return
        }
        if (state.resources.prestige < medal.prestigeCost) {
            showBanner("Need ${medal.prestigeCost} PRS to confer ${medal.name}.")
            return
        }

        val cleanNote = citationNote.trim().ifBlank {
            "Conferred upon ${operator.rank.code} ${operator.callsign} for distinguished Army service."
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    prestige = (cur.prestige - medal.prestigeCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.addCitation(
                AwardedMedal(
                    id = "cit_${UUID.randomUUID().toString().take(8)}",
                    operatorId = operatorId,
                    medalId = medalId,
                    citationNote = cleanNote,
                    missionTitle = missionTitle,
                    awardedAtMillis = now
                )
            )
            // Automatically equip newly awarded Shoulder Cords on their regulation shoulder if empty or higher MOS priority
            if (medal.category == AwardCategory.SHOULDER_CORD) {
                val currentSlotCord = if (medal.isRightShoulder) {
                    operator.equippedRightCordId
                } else {
                    operator.equippedLeftCordId
                }
                val currentMedal = currentSlotCord?.let { id -> GameCatalog.medals.find { it.id == id } }
                val shouldEquip = currentMedal == null ||
                    !UniformRegaliaRules.isCordAllowedOnShoulder(currentMedal, medal.isRightShoulder) ||
                    UniformRegaliaRules.mosCordPriority(operator.specialty, medal.id) <
                    UniformRegaliaRules.mosCordPriority(operator.specialty, currentMedal.id)
                val updatedOp = if (!shouldEquip) {
                    operator.copy(
                        uniformMode = UniformMode.DRESS_UNIFORM,
                        activeWardrobeKit = if (operator.activeWardrobeKit == WardrobeKit.FIELD) WardrobeKit.ALPHAS else operator.activeWardrobeKit
                    )
                } else if (medal.isRightShoulder) {
                    val nextAlphas = operator.wardrobe.alphas.copy(
                        equippedRightCordId = medal.id,
                        cordCustomized = true
                    )
                    operator.copy(
                        uniformMode = UniformMode.DRESS_UNIFORM,
                        activeWardrobeKit = WardrobeKit.ALPHAS,
                        equippedRightCordId = medal.id,
                        wardrobe = operator.wardrobe.withKit(WardrobeKit.ALPHAS, nextAlphas)
                    )
                } else {
                    val nextAlphas = operator.wardrobe.alphas.copy(
                        equippedLeftCordId = medal.id,
                        cordCustomized = true
                    )
                    operator.copy(
                        uniformMode = UniformMode.DRESS_UNIFORM,
                        activeWardrobeKit = WardrobeKit.ALPHAS,
                        equippedLeftCordId = medal.id,
                        wardrobe = operator.wardrobe.withKit(WardrobeKit.ALPHAS, nextAlphas)
                    )
                }
                repository.saveOperator(updatedOp)
            }
            showBanner("Awarded ${medal.name} to ${operator.rank.code} ${operator.callsign}!")
        }
    }

    fun dismissPendingDeed(deedId: String) {
        viewModelScope.launch {
            repository.dismissPendingDeed(deedId)
        }
    }

    fun dismissOfflineReport() {
        _offlineReport.value = null
    }

    fun dismissDebrief() {
        _lastDebrief.value = null
    }

    fun resetCampaign() {
        viewModelScope.launch {
            isResetting = true
            try {
                val now = System.currentTimeMillis()
                _offlineReport.value = null
                _lastDebrief.value = null
                _nowMillis.value = now
                lastTickMillis = now
                repository.resetCampaign(now)
                showBanner("Campaign reset to initial Directorate orders.")
            } finally {
                isResetting = false
            }
        }
    }

    fun saveSquadConfiguration(
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        standardPatch: UnitPatchStyle? = null,
        standardCamo: CamoPattern? = null,
        standardHeadgear: HeadgearStyle? = null,
        standardDressStyle: DressUniformStyle? = null
    ) {
        val existing = squadId?.let { id -> uiState.value.squads.find { it.id == id } }
        val standard = SquadUniformStandard(
            patch = standardPatch ?: existing?.standardPatch
                ?: UnitPatchStyle.firstUnusedDivision(uiState.value.squads),
            camo = standardCamo,
            bravosHeadgear = existing?.standardBravosHeadgear,
            bravosDressStyle = existing?.standardBravosDressStyle,
            alphasHeadgear = standardHeadgear,
            alphasDressStyle = standardDressStyle,
            messDressStyle = existing?.standardMessDressStyle,
            messHeadgear = existing?.standardMessHeadgear,
            alphasGloves = existing?.standardAlphasGloves,
            messGloves = existing?.standardMessGloves
        )
        saveSquadWithStandard(
            squadId = squadId,
            name = name,
            memberIds = memberIds,
            leaderId = leaderId,
            standard = standard,
            applyImmediately = false
        )
    }

    fun saveSquadWithStandard(
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        standard: SquadUniformStandard,
        applyImmediately: Boolean = false
    ) {
        val state = uiState.value
        val validIds = memberIds
            .distinct()
            .filter { id ->
                state.operators.find { it.id == id }?.let { SquadCohesionRules.isOperatorEligibleForFieldSquad(it) } == true
            }
            .take(MAX_SQUAD_SIZE)

        val resolvedLeaderId = leaderId?.takeIf { it in validIds }
            ?: SquadCohesionRules.pickDefaultLeaderId(validIds, state.operators)

        val existing = squadId?.let { id -> state.squads.find { it.id == id } }
        val resolvedName = name.trim().take(24).ifBlank {
            existing?.name ?: SquadCohesionRules.nextDefaultSquadName(state.squads)
        }
        val now = System.currentTimeMillis()
        // Squads always carry a division patch; a missing or non-division pick gets the first free one
        val resolvedPatch = standard.patch.takeIf { it.isDivision }
            ?: existing?.standardPatch?.takeIf { it.isDivision }
            ?: UnitPatchStyle.firstUnusedDivision(state.squads, excludingSquadId = existing?.id)
        val targetSquad = if (existing != null) {
            existing.copy(
                name = resolvedName,
                memberIds = validIds,
                leaderId = resolvedLeaderId,
                standardPatch = resolvedPatch,
                standardCamo = standard.camo,
                standardHeadgear = standard.alphasHeadgear,
                standardDressStyle = standard.alphasDressStyle,
                standardBravosHeadgear = standard.bravosHeadgear,
                standardBravosDressStyle = standard.bravosDressStyle,
                standardMessDressStyle = standard.messDressStyle,
                standardMessHeadgear = standard.messHeadgear,
                standardAlphasGloves = standard.alphasGloves,
                standardMessGloves = standard.messGloves
            )
        } else {
            TacticalSquad(
                id = "sq_${UUID.randomUUID().toString().take(8)}",
                name = resolvedName,
                memberIds = validIds,
                leaderId = resolvedLeaderId,
                cohesionXp = 0,
                missionsTogether = 0,
                createdAtMillis = now,
                standardPatch = resolvedPatch,
                standardCamo = standard.camo,
                standardHeadgear = standard.alphasHeadgear,
                standardDressStyle = standard.alphasDressStyle,
                standardBravosHeadgear = standard.bravosHeadgear,
                standardBravosDressStyle = standard.bravosDressStyle,
                standardMessDressStyle = standard.messDressStyle,
                standardMessHeadgear = standard.messHeadgear,
                standardAlphasGloves = standard.alphasGloves,
                standardMessGloves = standard.messGloves
            )
        }

        // Remove assigned members from any other squad so each soldier belongs to at most 1 squad
        val otherUpdatedSquads = state.squads
            .filter { it.id != targetSquad.id && it.memberIds.any { m -> m in validIds } }
            .map { other ->
                val remaining = other.memberIds.filter { it !in validIds }
                val nextLeader = other.leaderId?.takeIf { it in remaining }
                    ?: SquadCohesionRules.pickDefaultLeaderId(remaining, state.operators)
                other.copy(memberIds = remaining, leaderId = nextLeader)
            }

        val updatedMembers = if (applyImmediately) {
            applyStandardToMembers(targetSquad, state)
        } else {
            emptyList()
        }

        viewModelScope.launch {
            repository.saveSquads(otherUpdatedSquads + targetSquad)
            if (updatedMembers.isNotEmpty()) {
                repository.saveOperators(updatedMembers)
                showBanner("Saved & dressed ${updatedMembers.size} soldiers in ${targetSquad.name} Uniform Standard.")
            } else {
                val leaderCallsign = resolvedLeaderId?.let { id -> state.operators.find { it.id == id }?.callsign }
                val leaderSuffix = if (leaderCallsign != null) " • Leader: $leaderCallsign" else ""
                showBanner("Saved ${targetSquad.name} (${validIds.size}/$MAX_SQUAD_SIZE$leaderSuffix)")
            }
        }
    }

    fun applySquadUniformStandard(squadId: String) {
        val state = uiState.value
        val squad = state.squads.find { it.id == squadId } ?: return
        val updatedMembers = applyStandardToMembers(squad, state)
        if (updatedMembers.isEmpty()) {
            showBanner("${squad.name} has no members to outfit.")
            return
        }
        viewModelScope.launch {
            repository.saveOperators(updatedMembers)
            showBanner("Dressed ${updatedMembers.size} soldiers in ${squad.name} Uniform Standard.")
        }
    }

    fun switchSquadWardrobeKit(squadId: String, kit: WardrobeKit) {
        val state = uiState.value
        val squad = state.squads.find { it.id == squadId } ?: return
        val members = state.operators.filter { it.id in squad.memberIds && kit.isAvailableFor(it) }
        if (members.isEmpty()) {
            showBanner("No eligible members in ${squad.name} for ${kit.label}.")
            return
        }
        val updated = members.map { op ->
            val nextMode = if (kit == WardrobeKit.FIELD) UniformMode.FIELD_TACTICAL else UniformMode.DRESS_UNIFORM
            op.copy(
                activeWardrobeKit = kit,
                uniformMode = nextMode
            )
        }
        viewModelScope.launch {
            repository.saveOperators(updated)
            val suffix = if (state.smartWardrobeEnabled) {
                " (Smart Duty-Dress ON — turn OFF on Uniforms to lock manual wear)"
            } else {
                ""
            }
            showBanner("${squad.name} switched to ${kit.label}$suffix.")
        }
    }

    private fun applyStandardToMembers(
        squad: TacticalSquad,
        state: CommandUiState
    ): List<Operator> {
        val members = state.operators.filter { it.id in squad.memberIds }
        return members.map { op ->
            val ownedMedalIds = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet()
            val allowFree = op.tailorOverrides.freeTailorOverride
            val nextCamo = squad.standardCamo ?: op.wardrobe.field.camoPattern ?: op.camoPattern

            val bravosBeret = squad.standardBravosHeadgear?.takeIf { hg ->
                allowFree || TacticalCalculator.evaluateHeadgearEligibility(hg, op, ownedMedalIds, WardrobeKit.BRAVOS).eligible
            } ?: op.wardrobe.bravos.headgearStyle
            val bravosDress = squad.standardBravosDressStyle?.takeIf { ds ->
                allowFree || TacticalCalculator.evaluateDressStyleEligibility(ds, op, ownedMedalIds, WardrobeKit.BRAVOS).eligible
            } ?: op.wardrobe.bravos.dressStyle

            val alphasCap = squad.standardHeadgear?.takeIf { hg ->
                allowFree || TacticalCalculator.evaluateHeadgearEligibility(hg, op, ownedMedalIds, WardrobeKit.ALPHAS).eligible
            } ?: op.wardrobe.alphas.headgearStyle
            val alphasDress = squad.standardDressStyle?.takeIf { ds ->
                allowFree || TacticalCalculator.evaluateDressStyleEligibility(ds, op, ownedMedalIds, WardrobeKit.ALPHAS).eligible
            } ?: op.wardrobe.alphas.dressStyle

            val messCap = squad.standardMessHeadgear?.takeIf { hg ->
                allowFree || TacticalCalculator.evaluateHeadgearEligibility(hg, op, ownedMedalIds, WardrobeKit.MESS).eligible
            } ?: op.wardrobe.mess.headgearStyle
            val messDress = squad.standardMessDressStyle?.takeIf { ds ->
                allowFree || TacticalCalculator.evaluateDressStyleEligibility(ds, op, ownedMedalIds, WardrobeKit.MESS).eligible
            } ?: op.wardrobe.mess.dressStyle

            val nextAlphasGloves = squad.standardAlphasGloves ?: op.wardrobe.alphas.hasWhiteGloves ?: op.hasWhiteGloves
            val nextMessGloves = squad.standardMessGloves ?: op.wardrobe.mess.hasWhiteGloves ?: true

            val nextWardrobe = op.wardrobe
                .withKit(WardrobeKit.FIELD, op.wardrobe.field.copy(camoPattern = nextCamo))
                .withKit(
                    WardrobeKit.BRAVOS,
                    op.wardrobe.bravos.copy(
                        dressStyle = bravosDress,
                        headgearStyle = bravosBeret,
                        hasWhiteGloves = false,
                        hasDressSash = false
                    )
                )
                .withKit(
                    WardrobeKit.ALPHAS,
                    op.wardrobe.alphas.copy(
                        dressStyle = alphasDress,
                        headgearStyle = alphasCap,
                        hasWhiteGloves = nextAlphasGloves
                    )
                )
                .withKit(
                    WardrobeKit.MESS,
                    op.wardrobe.mess.copy(
                        dressStyle = messDress,
                        headgearStyle = messCap,
                        hasWhiteGloves = nextMessGloves,
                        hasDressSash = false
                    )
                )

            op.copy(
                camoPattern = nextCamo,
                headgearStyle = alphasCap ?: op.headgearStyle,
                dressStyle = alphasDress ?: op.dressStyle,
                hasWhiteGloves = nextAlphasGloves,
                // The squad patch is resolved from membership at render time, never copied onto the soldier
                wardrobe = nextWardrobe
            )
        }
    }

    fun conductUniformInspection(
        operatorId: String? = null,
        squadId: String? = null,
        inspectedKit: WardrobeKit? = null
    ) {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val cooldownMs = 60_000L
        val elapsedSince = now - state.lastInspectionAtMillis
        if (state.lastInspectionAtMillis > 0L && elapsedSince in 0L until cooldownMs) {
            val waitSec = ((cooldownMs - elapsedSince) + 999L) / 1000L
            showBanner("Inspection team resetting — next formal inspection in ${waitSec}s.")
            return
        }
        val squad = squadId?.let { id -> state.squads.find { it.id == id } }
        val subjects = when {
            squad != null -> state.operators.filter { it.id in squad.memberIds }
            operatorId != null -> listOfNotNull(state.operators.find { it.id == operatorId })
            else -> emptyList()
        }
        if (subjects.isEmpty()) {
            showBanner("Select a soldier or squad to inspect.")
            return
        }
        val report = UniformRegaliaRules.evaluateUniformInspection(
            operators = subjects,
            citations = state.citations,
            gearItems = state.gearItems,
            squads = state.squads,
            subjectSquad = squad,
            inspectedKit = inspectedKit
        )
        val buffUntil = now + report.buffDurationSec * 1000L
        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(
                    prestige = cur.prestige + report.prestigeEarned,
                    lastInspectionAtMillis = now,
                    inspectionBuffUntilMillis = buffUntil,
                    lastSimulatedAtMillis = now
                )
            }
            _lastInspectionReport.value = report
            showBanner("Inspection ${report.gradeLetter}: +${report.prestigeEarned} PRS • +8% Eff / +10% PRS for 3m.")
        }
    }

    fun dismissInspectionReport() {
        _lastInspectionReport.value = null
    }

    fun designateSquadLeader(squadId: String, operatorId: String) {
        val state = uiState.value
        val squad = state.squads.find { it.id == squadId } ?: return
        val op = state.operators.find { it.id == operatorId } ?: return
        if (operatorId !in squad.memberIds) return
        viewModelScope.launch {
            repository.saveSquad(squad.copy(leaderId = operatorId))
            showBanner("${op.rank.code} ${op.callsign} designated Squad Leader of ${squad.name}.")
        }
    }

    fun disbandSquad(squadId: String) {
        val state = uiState.value
        val squad = state.squads.find { it.id == squadId } ?: return
        viewModelScope.launch {
            repository.deleteSquad(squadId)
            showBanner("Disbanded ${squad.name}.")
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun showBanner(message: String) {
        _statusBanner.value = message
    }

    private data class DataSnapshot(
        val commander: CommanderStateEntity?,
        val facilities: List<FacilityState>,
        val operators: List<Operator>,
        val gearItems: List<GearItem>,
        val deployments: List<ActiveDeployment>,
        val citations: List<AwardedMedal>,
        val pendingDeeds: List<PendingDeed>,
        val squads: List<TacticalSquad>
    )

    companion object {
        fun factory(repository: AegisRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CommandViewModel(repository) as T
                }
            }
    }
}
