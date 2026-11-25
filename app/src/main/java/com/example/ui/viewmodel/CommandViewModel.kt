package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.GameCatalog
import com.example.data.local.AegisRepository
import com.example.data.local.CommanderStateEntity
import com.example.domain.PromotionEndorsementFocus
import com.example.domain.SquadCohesionRules
import com.example.domain.MosEffects
import com.example.domain.MoraleRules
import com.example.domain.GameTiming
import com.example.domain.GameCalendar
import com.example.domain.MissionNameGenerator
import com.example.domain.MissionNames
import com.example.domain.displayTitle
import com.example.domain.TacticalCalculator
import com.example.domain.CommandEventEffects
import com.example.domain.PendingSoldierEvent
import com.example.domain.PetitionAction
import com.example.domain.SoldierEventContext
import com.example.domain.SoldierEvents
import com.example.domain.PowRules
import com.example.domain.ClandestineRules
import com.example.domain.CompoundRules
import com.example.domain.AwardRules
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong
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
    val maxHqStaffSlots: Int = 4,
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
    val rotationUpdatedAtMillis: Long = 0L,
    /** Item 69: the soldier event waiting for an answer, if any. */
    val pendingEvent: PendingSoldierEvent? = null,
    val lastEventCheckAtMillis: Long = 0L,
    val eventEffects: CommandEventEffects = CommandEventEffects(),
    /** Session 38: Parade Ground activity times. */
    val lastCookoutAtMillis: Long = 0L,
    val lastParadeActivityAtMillis: Long = 0L,
    /** Session 41 (item 73): the unit calendar in game days. */
    val calendarDays: Double = 0.0
) {
    fun eventContext(): SoldierEventContext = SoldierEventContext(operators, squads, deployments, facilities, resources, nowMillis)

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
    private val _rifleQualQueue = MutableStateFlow<List<TacticalCalculator.RifleQualResult>>(emptyList())
    /** Rifle qualification results waiting to be shown, one pop-up at a time. */
    val rifleQualQueue: StateFlow<List<TacticalCalculator.RifleQualResult>> = _rifleQualQueue
    private val _awardQueue = MutableStateFlow<List<PendingDeed>>(emptyList())
    /** Newly earned awards waiting for an Award or Skip decision, one pop-up at a time. */
    val awardQueue: StateFlow<List<PendingDeed>> = _awardQueue
    private val _awardQueueTotal = MutableStateFlow(0)
    /** Size of the current award batch, for the "n of N" counter. Resets once the queue empties. */
    val awardQueueTotal: StateFlow<Int> = _awardQueueTotal
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
            TacticalCalculator.sfSquadEstablished = TacticalCalculator.sfMissionsUnlocked(data.squads)
            TacticalCalculator.clearedCampaignIds = completedCampaigns
            ClandestineRules.clearedStageIds = completedStages
            HqChain.seatedStaff = data.operators.filter { it.holdsHqSeat }
            HqChain.commanderRank = data.operators.firstOrNull {
                it.holdsHqSeat && it.hqRole == HqStaffRole.CO_COMMANDING_OFFICER
            }?.earnedRank
            CompoundRules.compoundLevel = TacticalCalculator.facilityLevel(data.facilities, FacilityType.SF_COMPOUND)
            TacticalCalculator.tdyOperatorIds = TacticalCalculator.tdyOperatorIdsFor(data.deployments)
            val eventEffects = CommandEventEffects(
                heloGroundedUntilMillis = data.commander.heloGroundedUntilMillis,
                recruitDiscountPct = data.commander.recruitDiscountPct,
                sfRehearsalBonusPct = data.commander.sfRehearsalBonusPct
            )
            SoldierEvents.effects = eventEffects
            GameCalendar.today = data.commander.calendarDays
            TacticalCalculator.airfieldLevel = TacticalCalculator.facilityLevel(data.facilities, FacilityType.AIRFIELD)
            // Session 38: Main Gate recruit discount and the CSM's morale cushion
            TacticalCalculator.mainGateLevel = TacticalCalculator.facilityLevel(data.facilities, FacilityType.MAIN_GATE)
            MoraleRules.csmSeated = data.operators.any { it.status == OperatorStatus.HQ_STAFF && it.hqRole == HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR }
            // Item 50: generated names for the current board, from the rotation time
            MissionNames.current = MissionNameGenerator.namesFor(
                rotatingIds.mapNotNull { id -> GameCatalog.rotatingStandalonePool.find { it.id == id } },
                data.commander.rotationUpdatedAtMillis
            )
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
                maxHqStaffSlots = TacticalCalculator.hqSeatCount(data.facilities),
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
                rotationUpdatedAtMillis = data.commander.rotationUpdatedAtMillis,
                pendingEvent = PendingSoldierEvent.fromCsv(data.commander.pendingEventCsv),
                lastEventCheckAtMillis = data.commander.lastEventCheckAtMillis,
                eventEffects = eventEffects,
                lastCookoutAtMillis = data.commander.lastCookoutAtMillis,
                lastParadeActivityAtMillis = data.commander.lastParadeActivityAtMillis,
                calendarDays = data.commander.calendarDays
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CommandUiState(isLoading = true)
    )

    /** Item 75: squads that no longer have a single active member disband on their own. */
    private val autoDisbanding = mutableSetOf<String>()

    private fun disbandEmptySquads(state: CommandUiState) {
        if (state.isLoading) return
        val opsById = state.operators.associateBy { it.id }
        state.squads.filter { squad ->
            squad.id !in autoDisbanding &&
                squad.memberIds.none { id -> opsById[id]?.let { SquadCohesionRules.isOperatorEligibleForFieldSquad(it) } == true }
        }.forEach { squad ->
            autoDisbanding += squad.id
            val keepers = state.operators.mapNotNull { op ->
                val earned = squad.unitCitations.filter { op.id in it.memberIds }.map { it.type }
                if (earned.isNotEmpty()) op.copy(retainedUnitAwards = op.retainedUnitAwards + earned) else null
            }
            viewModelScope.launch {
                if (keepers.isNotEmpty()) repository.saveOperators(keepers)
                repository.deleteSquad(squad.id)
                showBanner("${squad.name} had no soldiers left and was disbanded.")
            }
        }
    }

    init {
        viewModelScope.launch { uiState.collect { disbandEmptySquads(it) } }
        // Item 99: drop award cards made unnecessary (e.g. a cord that came free with its badge)
        viewModelScope.launch { uiState.collect { pruneAwardQueue(it) } }
        // Item 107: the S-3 is enlisted only; an officer or warrant seated there returns to field duty
        viewModelScope.launch { uiState.collect { enforceEnlistedBillets(it) } }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val loadResult = repository.loadOrResetSave(now)
            val initial = loadResult.state
            if (loadResult.wasReset) {
                showBanner("This save was from an older version of Aegis Ops. A new command has been started.")
            }
            // Builds keep running while the app is closed, with no offline cap
            finishCompletedUpgrades(repository.facilities.first(), now)
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

            // Session 41: the calendar runs offline for as long as anything was in progress
            val squadsAtLoad = repository.squads.first()
            val clockFrom = if (initial.lastClockAtMillis > 0L) initial.lastClockAtMillis else now
            val calendarAtLoad = GameCalendar.advance(
                initial.calendarDays, GameCalendar.activeIntervals(ops, deps, squadsAtLoad), clockFrom, now
            )
            GameCalendar.today = calendarAtLoad
            repository.saveCommanderState(
                initial.copy(
                    id = 1,
                    calendarDays = calendarAtLoad,
                    lastClockAtMillis = now,
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

                val tickIntervals = GameCalendar.activeIntervals(state.operators, state.deployments, state.squads)
                repository.updateCommanderState { cur ->
                    val existingRotating = cur.rotatingMissionIdsCsv.split(",").filter { it.isNotBlank() }
                    val rotationDue = existingRotating.isEmpty() ||
                        cur.rotationUpdatedAtMillis <= 0L ||
                        (now - cur.rotationUpdatedAtMillis) >= TacticalCalculator.STANDALONE_ROTATION_INTERVAL_MILLIS
                    // Session 41: the calendar moves only while something is in progress
                    val clockFrom = if (cur.lastClockAtMillis > 0L) cur.lastClockAtMillis else now
                    val days = GameCalendar.advance(cur.calendarDays, tickIntervals, clockFrom, now)
                    GameCalendar.today = days
                    cur.copy(
                        calendarDays = days,
                        lastClockAtMillis = now,
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

                finishCompletedUpgrades(state.facilities, now)
                driftMorale(state, now)

                // Item 69: soldiers back from leave or an event duty
                val backFromLeave = state.operators.filter { it.status == OperatorStatus.ON_LEAVE && now >= it.recoveryUntilMillis }
                if (backFromLeave.isNotEmpty()) {
                    repository.saveOperators(backFromLeave.map {
                        it.copy(status = if (it.hqRole != null) OperatorStatus.HQ_STAFF else OperatorStatus.READY, recoveryUntilMillis = 0L)
                    })
                    showBanner("${backFromLeave.joinToString { it.callsign }} back on duty.")
                }
                tickSoldierEvents(state, now)
                stampServiceRecords(state)
                enforceMandatoryRetirement(state, now)
                repairPowRecords(state, now)
                repairOcsCommissions(state)
                repairStarterSchools(state)
                repairCommandSeniority(state)
                repairSfSquadNames(state)
                repairSfTeamPatches(state)
                resolveCaptivity(state, now)
                // Item 62: covers that blow now; the clock drops and the team must choose
                state.deployments.filter { ClandestineRules.coverBlowsNow(it, now) }.forEach { d ->
                    repository.saveDeployment(ClandestineRules.blowCover(d, now))
                    showBanner("Cover blown on ${d.missionTitle ?: "a clandestine mission"}!")
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

    /**
     * Item 69: drop a waiting event that has expired or no longer fits; otherwise, at most once per interval,
     * roll for a new one. Only one event waits at a time.
     */
    private suspend fun tickSoldierEvents(state: CommandUiState, now: Long) {
        val ctx = state.eventContext().copy(now = now)
        val pending = state.pendingEvent
        if (pending != null) {
            if (!SoldierEvents.stillValid(pending, ctx)) {
                repository.updateCommanderState { it.copy(pendingEventCsv = "") }
            }
            return
        }
        // The first check waits a full interval after a fresh start
        if (state.lastEventCheckAtMillis == 0L) {
            repository.updateCommanderState { it.copy(lastEventCheckAtMillis = now) }
            return
        }
        if (now - state.lastEventCheckAtMillis < SoldierEvents.eventIntervalMillis()) return
        val rolled = SoldierEvents.roll(ctx)
        repository.updateCommanderState { it.copy(lastEventCheckAtMillis = now, pendingEventCsv = rolled?.toCsv() ?: "") }
    }

    /** Fix: saves from before the capture-record fix; see [PowRules.repairedRecord]. */
    private suspend fun repairPowRecords(state: CommandUiState, now: Long) {
        val powDeedIds = state.pendingDeeds.filter { it.recommendedMedalId == PowRules.POW_MEDAL_ID }.map { it.operatorId }.toSet()
        val fixed = state.operators.mapNotNull { op -> PowRules.repairedRecord(op, op.id in powDeedIds, now) }
        if (fixed.isNotEmpty()) repository.saveOperators(fixed)
    }

    /** Item 63: held soldiers whose wait has run out are released or die in captivity. */
    private suspend fun resolveCaptivity(state: CommandUiState, now: Long) {
        val due = state.operators.filter {
            it.status == OperatorStatus.CAPTURED && it.captivityResolveAtMillis in 1..now &&
                state.deployments.none { d -> d.targetOperatorId == it.id }
        }
        if (due.isEmpty()) return
        val deeds = mutableListOf<PendingDeed>()
        val updated = due.map { op ->
            if (Random.nextInt(100) < PowRules.releaseSurvivalPct(op)) {
                deeds += PendingDeed(
                    id = "deed_${UUID.randomUUID().toString().take(8)}",
                    operatorId = op.id,
                    missionTitle = "Released from Captivity",
                    deedSummary = "Held as a prisoner of war and released by the captors.",
                    recommendedMedalId = PowRules.POW_MEDAL_ID,
                    createdAtMillis = now
                )
                PowRules.released(op, now, TacticalCalculator.aidRecoveryMultiplier(state.facilities))
            } else PowRules.diedInCaptivity(op)
        }
        // Session 38: homecomings give squads their morale back; a death in captivity hits like a loss in action
        val ops = LinkedHashMap<String, Operator>()
        updated.forEach { ops[it.id] = it }
        var moraleSquads = state.squads
        val moraleChanged = mutableSetOf<String>()
        for (u in updated) {
            if (u.status == OperatorStatus.FALLEN) {
                moraleSquads = moraleSquads.map { sq ->
                    if (u.id !in sq.memberIds && u.id !in sq.shakenForIds && u.id !in sq.ralliedForIds) return@map sq
                    sq.memberIds.filter { it != u.id }.forEach { mid ->
                        (ops[mid] ?: state.operators.find { it.id == mid })?.let { ops[mid] = MoraleRules.adjust(it, MoraleRules.SQUADMATE_KILLED) }
                    }
                    moraleChanged += sq.id
                    sq.copy(shakenForIds = sq.shakenForIds - u.id, ralliedForIds = sq.ralliedForIds - u.id)
                }
            } else {
                val (sqs, changed) = homecoming(moraleSquads, u.id, ops, state)
                moraleSquads = sqs
                moraleChanged += changed
            }
        }
        repository.saveOperators(ops.values.toList())
        val effective = state.operators.map { o -> ops[o.id] ?: o }
        val pruned = SquadCohesionRules.pruneUnavailableMembers(moraleSquads, effective)
        val squads = pruned.filter { sq -> sq.id in moraleChanged || state.squads.find { it.id == sq.id } != sq }
        if (squads.isNotEmpty()) repository.saveSquads(squads)
        if (deeds.isNotEmpty()) {
            repository.addPendingDeeds(deeds)
            enqueueAwardPrompts(deeds)
        }
        showBanner(updated.joinToString(" ") {
            if (it.status == OperatorStatus.FALLEN) "${it.callsign} died in captivity." else "${it.callsign} was released and is coming home."
        })
    }

    /** Item 62: the region a Language school class will study, picked in staging. */
    var languageRegionChoice: TheaterRegion? = null
        private set

    fun selectLanguageRegion(region: TheaterRegion) {
        languageRegionChoice = region
    }

    /** Item 62: push on after a blown cover; the mission continues at a disadvantage. */
    fun pushOnAfterBlownCover(deploymentId: String) {
        val d = uiState.value.deployments.find { it.id == deploymentId } ?: return
        viewModelScope.launch { repository.saveDeployment(d.copy(coverPushOn = true)) }
        showBanner("Pushing on. The team fights its way to the objective.")
    }

    /** Item 62: abort and exfil after a blown cover: no rewards, halved casualty odds. */
    fun exfilAfterBlownCover(deploymentId: String) = cancelDeployment(deploymentId, ClandestineRules.EXFIL_CASUALTY_FACTOR)

    /** Item 63: the POW a rescue or negotiation is being staged for. */
    var powOperationTargetId: String? = null
        private set

    fun selectPowTarget(operatorId: String) {
        powOperationTargetId = operatorId
        PowRules.activeTalksFailures = uiState.value.operators.find { it.id == operatorId }?.powTalksFailures ?: 0
    }

    /** Item 69: answer the waiting soldier event. */
    fun answerSoldierEvent(choiceId: String) {
        val state = uiState.value
        val event = state.pendingEvent ?: return
        val ctx = state.eventContext().copy(now = System.currentTimeMillis())
        if (!SoldierEvents.stillValid(event, ctx)) {
            viewModelScope.launch { repository.updateCommanderState { it.copy(pendingEventCsv = "") } }
            showBanner("That request has lapsed.")
            return
        }
        val outcome = SoldierEvents.apply(event, choiceId, ctx)
        when (outcome.petitionAction) {
            PetitionAction.APPROVE_TRANSFER -> approveTransferOut(event.operatorId)
            PetitionAction.DECLINE_TRANSFER -> declineTransferRequest(event.operatorId)
            PetitionAction.APPROVE_RETIREMENT -> approveRetirement(event.operatorId)
            PetitionAction.DENY_RETIREMENT -> denyRetirementRequest(event.operatorId)
            null -> Unit
        }
        if (outcome.deeds.isNotEmpty()) enqueueAwardPrompts(outcome.deeds)
        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                val eff = outcome.effects
                cur.copy(
                    credits = (cur.credits + outcome.creditsDelta).coerceAtLeast(0.0),
                    supplies = (cur.supplies + outcome.suppliesDelta).coerceAtLeast(0.0),
                    intel = (cur.intel + outcome.intelDelta).coerceAtLeast(0.0),
                    prestige = (cur.prestige + outcome.prestigeDelta).coerceAtLeast(0.0),
                    pendingEventCsv = "",
                    heloGroundedUntilMillis = eff?.heloGroundedUntilMillis ?: cur.heloGroundedUntilMillis,
                    recruitDiscountPct = eff?.recruitDiscountPct ?: cur.recruitDiscountPct,
                    sfRehearsalBonusPct = eff?.sfRehearsalBonusPct ?: cur.sfRehearsalBonusPct
                )
            }
            if (outcome.operatorUpdates.isNotEmpty()) repository.saveOperators(outcome.operatorUpdates)
            if (outcome.squadUpdates.isNotEmpty()) repository.saveSquads(outcome.squadUpdates)
            if (outcome.deeds.isNotEmpty()) repository.addPendingDeeds(outcome.deeds)
        }
        if (outcome.banner.isNotBlank()) showBanner(outcome.banner)
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

    /** Testing build only: tapping CR, SUP or INT on the resource bar adds a stock of it. */
    fun grantCheatResource(type: ResourceType) {
        val state = uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                when (type) {
                    ResourceType.CREDITS -> cur.copy(credits = cur.credits + CHEAT_CREDITS, lastSimulatedAtMillis = now)
                    ResourceType.SUPPLIES -> cur.copy(supplies = cur.supplies + CHEAT_SUPPLIES, lastSimulatedAtMillis = now)
                    ResourceType.INTEL -> cur.copy(intel = cur.intel + CHEAT_INTEL, lastSimulatedAtMillis = now)
                    ResourceType.PRESTIGE -> cur.copy(prestige = cur.prestige + 100.0, lastSimulatedAtMillis = now)
                }
            }
            showBanner(when (type) {
                ResourceType.CREDITS -> "+${CHEAT_CREDITS.toInt()} CR (testing grant)."
                ResourceType.SUPPLIES -> "+${CHEAT_SUPPLIES.toInt()} SUP (testing grant)."
                ResourceType.INTEL -> "+${CHEAT_INTEL.toInt()} INT (testing grant)."
                ResourceType.PRESTIGE -> "+100 PRS Directorate Contingency Grant."
            })
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

    /** Starts construction: the building keeps working at its current level until the timer finishes. */
    fun upgradeFacility(type: FacilityType) {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val current = state.facilities.find { it.type == type } ?: FacilityState(type, type.startLevel)
        TacticalCalculator.facilityUpgradeBlocker(type, state.facilities)?.let { blocker ->
            showBanner("${type.title}: $blocker")
            return
        }
        val creditCost = type.upgradeCreditCost(current.level)
        val supplyCost = type.upgradeSupplyCost(current.level)
        if (state.resources.credits < creditCost || state.resources.supplies < supplyCost) {
            showBanner("Insufficient CR or SUP to build ${type.title}.")
            return
        }
        val buildSeconds = type.buildSeconds(current.level)
        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - creditCost).coerceAtLeast(0.0),
                    supplies = (cur.supplies - supplyCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveFacility(
                current.copy(upgradeStartedAtMillis = now, upgradeEndsAtMillis = now + buildSeconds * 1000L)
            )
            showBanner("Construction started: ${type.title} Lv ${current.level + 1} (${TacticalCalculator.formatBuildTime(buildSeconds)})")
        }
    }

    /** Finishes a running build now for Prestige scaled by the time left. */
    fun rushFacility(type: FacilityType) {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val current = state.facilities.find { it.type == type && it.isUpgrading } ?: return
        val cost = TacticalCalculator.rushPrestigeCost(current, now)
        if (state.resources.prestige < cost) {
            showBanner("Need $cost PRS to rush ${type.title}.")
            return
        }
        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(prestige = (cur.prestige - cost).coerceAtLeast(0.0), lastSimulatedAtMillis = now)
            }
            val done = current.copy(level = (current.level + 1).coerceAtMost(MAX_FACILITY_LEVEL), upgradeEndsAtMillis = 0L, upgradeStartedAtMillis = 0L)
            repository.saveFacility(done)
            showBanner("${type.title} rushed to Lv ${done.level} for $cost PRS")
        }
    }

    // ---------- Session 38: Parade Ground activities and squad leave ----------

    private fun onBase(op: Operator) = op.unlocked && (op.status == OperatorStatus.READY || op.status == OperatorStatus.HQ_STAFF)

    /** Unit run: one squad, a few supplies, +3 morale for members on base. */
    fun unitRun(squadId: String) {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val squad = state.squads.find { it.id == squadId } ?: return
        val left = squad.lastRunAtMillis + GameTiming.millis(MoraleRules.UNIT_RUN_COOLDOWN_MILLIS) - now
        if (squad.lastRunAtMillis > 0 && left > 0) { showBanner("${squad.name} ran recently: again in ${TacticalCalculator.formatBuildTime(left / 1000)}."); return }
        if (state.resources.supplies < MoraleRules.UNIT_RUN_SUPPLIES) { showBanner("Need ${MoraleRules.UNIT_RUN_SUPPLIES.toInt()} SUP for a unit run."); return }
        val runners = state.operators.filter { it.id in squad.memberIds && onBase(it) }
        if (runners.isEmpty()) { showBanner("${squad.name} has nobody on base to run."); return }
        viewModelScope.launch {
            repository.updateCommanderState { it.copy(supplies = (it.supplies - MoraleRules.UNIT_RUN_SUPPLIES).coerceAtLeast(0.0), lastParadeActivityAtMillis = now, lastSimulatedAtMillis = now) }
            repository.saveOperators(runners.map { MoraleRules.adjust(it, MoraleRules.UNIT_RUN_MORALE) })
            repository.saveSquads(listOf(squad.copy(lastRunAtMillis = now)))
            showBanner("${squad.name} ran the post: +${MoraleRules.UNIT_RUN_MORALE} morale for ${runners.size}.")
        }
    }

    /** Cookout: the whole base, supplies plus credits by headcount, +5 morale for everyone on base. */
    fun cookout() {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val left = state.lastCookoutAtMillis + GameTiming.millis(MoraleRules.COOKOUT_COOLDOWN_MILLIS) - now
        if (state.lastCookoutAtMillis > 0 && left > 0) { showBanner("Next cookout in ${TacticalCalculator.formatBuildTime(left / 1000)}."); return }
        val guests = state.operators.filter { onBase(it) }
        val (sup, cr) = MoraleRules.cookoutCost(guests.size)
        if (state.resources.supplies < sup || state.resources.credits < cr) { showBanner("Need ${sup.toInt()} SUP and ${cr.toInt()} CR for a cookout."); return }
        viewModelScope.launch {
            repository.updateCommanderState {
                it.copy(supplies = (it.supplies - sup).coerceAtLeast(0.0), credits = (it.credits - cr).coerceAtLeast(0.0),
                    lastCookoutAtMillis = now, lastParadeActivityAtMillis = now, lastSimulatedAtMillis = now)
            }
            repository.saveOperators(guests.map { MoraleRules.adjust(it, MoraleRules.COOKOUT_MORALE) })
            showBanner("Cookout on the Parade Ground: +${MoraleRules.COOKOUT_MORALE} morale for ${guests.size} soldiers.")
        }
    }

    /** Award formation: every pending award at once (same prestige as one by one), +2 morale each, squads +cohesion. */
    fun awardFormation() {
        val state = uiState.value
        if (state.pendingDeeds.isEmpty()) { showBanner("No pending awards to present."); return }
        val awarded = awardAll(state.pendingDeeds, formation = true)
        if (awarded.isEmpty()) return
        val now = System.currentTimeMillis()
        val honored = state.pendingDeeds.filter { it.id in awarded }.map { it.operatorId }.toSet()
        val squads = state.squads.filter { sq -> sq.memberIds.any { it in honored } }
            .map { it.copy(cohesionXp = (it.cohesionXp + MoraleRules.FORMATION_COHESION_XP).coerceAtMost(MAX_SQUAD_COHESION_XP)) }
        viewModelScope.launch {
            repository.updateCommanderState { it.copy(lastParadeActivityAtMillis = now) }
            if (squads.isNotEmpty()) repository.saveSquads(squads)
            awarded.forEach { repository.dismissPendingDeed(it) }
        }
    }

    /** Squad leave: the whole squad away for a while, +25 morale each; then a cooldown before the next. */
    fun sendSquadOnLeave(squadId: String) {
        val state = uiState.value
        val now = System.currentTimeMillis()
        val squad = state.squads.find { it.id == squadId } ?: return
        val members = state.operators.filter { it.id in squad.memberIds }
        if (members.isEmpty()) return
        if (squad.leaveReadyAtMillis > now) {
            showBanner("${squad.name} can take leave again in ${TacticalCalculator.formatBuildTime((squad.leaveReadyAtMillis - now) / 1000)}."); return
        }
        val busy = members.filter { it.status != OperatorStatus.READY }
        if (busy.isNotEmpty()) { showBanner("${squad.name} isn't all home: ${busy.joinToString { "${it.callsign} (${it.status.label})" }}."); return }
        val cost = MoraleRules.SQUAD_LEAVE_CREDITS_PER_SOLDIER * members.size
        if (state.resources.credits < cost) { showBanner("Need ${cost.toInt()} CR to send ${squad.name} on leave."); return }
        val until = now + GameCalendar.daysToMillis(GameCalendar.SQUAD_LEAVE_DAYS)
        viewModelScope.launch {
            repository.updateCommanderState { it.copy(credits = (it.credits - cost).coerceAtLeast(0.0), lastSimulatedAtMillis = now) }
            repository.saveOperators(members.map {
                MoraleRules.adjust(it, MoraleRules.SQUAD_LEAVE_MORALE).copy(status = OperatorStatus.ON_LEAVE, recoveryUntilMillis = until, deployStreak = 0)
            })
            repository.saveSquads(listOf(squad.copy(leaveUntilMillis = until, leaveReadyAtMillis = until + GameTiming.millis(MoraleRules.SQUAD_LEAVE_COOLDOWN_MILLIS))))
            showBanner("${squad.name} is on leave: +${MoraleRules.SQUAD_LEAVE_MORALE} morale; back in ${TacticalCalculator.formatBuildTime((until - now) / 1000)}.")
        }
    }

    /** Session 38: morale drifts one point per step back toward the base's baseline. */
    private var lastMoraleDriftAt = 0L

    private suspend fun driftMorale(state: CommandUiState, now: Long) {
        if (lastMoraleDriftAt == 0L) { lastMoraleDriftAt = now; return }
        val steps = ((now - lastMoraleDriftAt) / MoraleRules.DRIFT_STEP_MILLIS).toInt()
        if (steps < 1) return
        lastMoraleDriftAt += steps * MoraleRules.DRIFT_STEP_MILLIS
        val baseline = MoraleRules.baseline(state.facilities, state.lastParadeActivityAtMillis, now)
        if (state.operators.any { it.unlocked && !it.status.isOutOfActiveService && it.morale != baseline }) {
            repository.driftMorale(baseline, steps)
        }
    }

    private suspend fun finishCompletedUpgrades(facilities: List<FacilityState>, now: Long) {
        val done = TacticalCalculator.completedUpgrades(facilities, now)
        if (done.isEmpty()) return
        done.forEach { repository.saveFacility(it) }
        showBanner(done.joinToString { "${it.type.title} Lv ${it.level}" } + " construction complete")
    }

    /**
     * Session 33 (Option B): pin the next rank for every soldier XP has made promotable (SGT and up, no board).
     * One rank per soldier per press; a soldier with more XP stays promotable for the next press.
     */
    fun pinAllEligiblePromotions() {
        val state = uiState.value
        val eligible = state.operators.filter { it.pinEligibleRank != null }.sortedByDescending { it.rank.seniorityIndex }
        if (eligible.isEmpty()) {
            showBanner("No soldiers are ready to pin a new rank.")
            return
        }
        // Each pin costs the same as the board-free promotion did; pin as many as the budget allows, senior first
        var crLeft = state.resources.credits
        var prsLeft = state.resources.prestige
        val affordable = eligible.filter { op ->
            val (cr, prs) = pinCost(op, state)
            if (cr <= crLeft && prs <= prsLeft) { crLeft -= cr; prsLeft -= prs; true } else false
        }
        if (affordable.isEmpty()) {
            val (cr, prs) = pinCost(eligible.first(), state)
            showBanner("Need ${cr.toInt()} CR and $prs PRS to pin ${eligible.first().callsign}'s next rank.")
            return
        }
        val spentCr = state.resources.credits - crLeft
        val spentPrs = state.resources.prestige - prsLeft
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - spentCr).coerceAtLeast(0.0),
                    prestige = (cur.prestige - spentPrs).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            // Session 38: a new rank lifts morale
            val pinned = affordable.map { op -> op to MoraleRules.adjust(op.promotedTo(op.pinEligibleRank!!), MoraleRules.RANK_PINNED) }
            repository.saveOperators(pinned.map { it.second })
            val deeds = pinned.flatMap { (before, after) ->
                newlyEligibleAwardDeeds(before = before, after = after, state = state, reasonTitle = "Promotion to ${after.rank.code}", now = now)
            }
            if (deeds.isNotEmpty()) {
                repository.addPendingDeeds(deeds)
                enqueueAwardPrompts(deeds)
            }
            val short = eligible.size - affordable.size
            showBanner(
                (if (pinned.size == 1) "${pinned[0].first.callsign} pinned ${pinned[0].second.rank.code}"
                else "${pinned.size} soldiers pinned new ranks: " + pinned.joinToString { "${it.first.callsign} ${it.second.rank.code}" }) +
                    " (${spentCr.toInt()} CR / ${spentPrs.toInt()} PRS)" +
                    if (short > 0) ". $short more waiting on resources." else "."
            )
        }
    }

    /** Session 33: a pin costs what the in-tier promotion costs (credits and prestige by tier, CSM discount applied). */
    private fun pinCost(op: Operator, state: CommandUiState): Pair<Double, Int> {
        val eval = TacticalCalculator.evaluatePromotionBoard(op, state.operators, state.citations, state.gearItems, state.facilities)
        return eval.creditCost to eval.prestigeCost
    }

    /** Session 33 (Option B): pin one soldier's next rank inside their tier. XP has to be there first, and it costs CR and PRS. */
    fun pinPromotion(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId } ?: return
        val next = op.pinEligibleRank
        if (next == null) {
            val target = OperatorRank.nextRank(op.rank)
            showBanner(
                if (target != null && op.xp < target.xpRequired) "${op.callsign} needs ${target.xpRequired - op.xp} more XP for ${target.code}."
                else "${op.callsign} can't pin a new rank right now."
            )
            return
        }
        val (crCost, prsCost) = pinCost(op, state)
        if (state.resources.credits < crCost || state.resources.prestige < prsCost) {
            showBanner("Need ${crCost.toInt()} CR and $prsCost PRS to pin ${op.callsign} as ${next.code}.")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - crCost).coerceAtLeast(0.0),
                    prestige = (cur.prestige - prsCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            val after = MoraleRules.adjust(op.promotedTo(next), MoraleRules.RANK_PINNED)
            repository.saveOperator(after)
            val deeds = newlyEligibleAwardDeeds(before = op, after = after, state = state, reasonTitle = "Promotion to ${next.code}", now = now)
            if (deeds.isNotEmpty()) {
                repository.addPendingDeeds(deeds)
                enqueueAwardPrompts(deeds)
            }
            showBanner("${op.callsign} pinned ${next.title} (${next.code} / ${next.payGrade}).")
        }
    }

    fun promoteOperator(
        operatorId: String,
        endorsementFocus: PromotionEndorsementFocus? = null
    ) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked && !it.status.isOutOfActiveService } ?: return
        // Session 33: from SGT up, a rank inside the tier is pinned (free, XP-gated), not bought
        if (op.pinnedRank != null && op.rank.tier != RankTier.SOLDIER) {
            val next = OperatorRank.nextRank(op.rank)
            if (next != null && !OperatorRank.isBoardGate(op.rank, next)) {
                pinPromotion(operatorId)
                return
            }
        }
        val boardEval = TacticalCalculator.evaluatePromotionBoard(
            operator = op,
            allOperators = state.operators,
            citations = state.citations,
            gearItems = state.gearItems,
            facilities = state.facilities
        )
        val nextRank = boardEval.nextRank ?: run {
            showBanner(boardEval.blockedReason?.let { "${op.callsign}: $it" } ?: "${op.callsign} already holds the highest Army rank (GEN).")
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
        val bonus = if (tierChanged) endorsementFocus?.statBonus else null
        val promoted = op.promotedTo(nextRank)
        val progressedOp = applyTierProgressionOnRankUp(
            op = promoted.copy(
                authorizedTier = if (nextRank.isWarrant) RankTier.WARRANT else nextAuthorizedTier,
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
            repository.saveOperator(MoraleRules.adjust(progressedOp, MoraleRules.RANK_PINNED))
            val promotionDeeds = newlyEligibleAwardDeeds(
                before = op,
                after = progressedOp,
                state = state,
                reasonTitle = "Promotion to ${nextRank.code}",
                now = now
            )
            if (promotionDeeds.isNotEmpty()) {
                repository.addPendingDeeds(promotionDeeds)
                enqueueAwardPrompts(promotionDeeds)
            }
            val msg = if (tierChanged) {
                val focusNote = endorsementFocus?.let { " • Endorsed: ${it.title}" } ?: ""
                "${op.callsign} pinned ${nextRank.title} (${nextRank.payGrade}) — ${nextRank.tier.shortLabel} Dress unlocked$focusNote!"
            } else {
                "${op.callsign} promoted to ${nextRank.title} (${nextRank.code} / ${nextRank.payGrade})."
            }
            showBanner(msg)
        }
    }

    /** WOCS appoints WO1 (keeping MOS); Flight School makes a 153A aviator; Flight Hours builds flight skill. */
    private fun applyCareerSchoolEffects(
        op: Operator,
        missionId: String,
        passed: Boolean,
        flightHours: Int = TacticalCalculator.FLIGHT_HOURS_PER_SESSION
    ): Operator = when {
        // Item 143: a Sniper School graduate becomes a Sniper, who counts as Infantry (11B)
        passed && missionId == TacticalCalculator.SNIPER_SCHOOL_ID -> op.copy(specialty = OperatorSpecialty.ASSAULT)
        // Session 23: an OCS graduate commissions as a 2LT on the spot, the way WOCS makes a WO1
        passed && missionId == TacticalCalculator.OCS_MISSION_ID && op.rank.tier.isEnlisted -> commissionFromOcs(op)
        passed && missionId == TacticalCalculator.WOCS_MISSION_ID -> {
            val prevRank = op.rank
            applyTierProgressionOnRankUp(op.promotedTo(OperatorRank.WO1), prevRank, OperatorRank.WO1)
        }
        passed && missionId == TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID -> op.copy(
            specialty = OperatorSpecialty.AVIATION,
            flightSkill = maxOf(op.flightSkill, TacticalCalculator.FLIGHT_SCHOOL_STARTING_SKILL),
            // Pilots aren't ground troops: their field kit goes back to the Armory (item 81)
            equippedGearIds = emptyMap()
        )
        // Item 95: passed or failed, a Flight Hours session logs hours (a failed one logs about half)
        missionId == TacticalCalculator.FLIGHT_HOURS_MISSION_ID ->
            TacticalCalculator.applyFlightHours(op, flightHours.takeIf { it > 0 } ?: TacticalCalculator.FLIGHT_HOURS_PER_SESSION, passed)
        else -> op
    }

    private fun commissionFromOcs(op: Operator): Operator {
        val prevRank = op.rank
        return applyTierProgressionOnRankUp(op.promotedTo(OperatorRank.LT2), prevRank, OperatorRank.LT2)
    }

    /** Session 23: soldiers who passed OCS before it commissioned directly get their 2LT now. */
    private suspend fun repairOcsCommissions(state: CommandUiState) {
        val fixed = state.operators.filter { TacticalCalculator.awaitingOcsCommission(it) }.map { commissionFromOcs(it) }
        if (fixed.isNotEmpty()) {
            repository.saveOperators(fixed)
            showBanner("${fixed.joinToString { it.callsign }} commissioned as 2LT (OCS graduate).")
        }
    }

    /**
     * Item 121: founding soldiers in older saves get the schools their rank implies (KESTREL's OCS), plus any
     * award those schools now earn. Runs once in effect: after the fix nothing is missing.
     */
    private suspend fun repairStarterSchools(state: CommandUiState) {
        val now = System.currentTimeMillis()
        state.operators.filter { it.id in GameCatalog.starterOperatorIds && !it.status.isOutOfActiveService }.forEach { op ->
            // Only the schools the founding record implies, never ones the soldier should earn in play
            val seed = GameCatalog.initialOperators.find { it.id == op.id } ?: return@forEach
            val missing = seed.completedMissionIds - op.completedMissionIds
            if (missing.isEmpty()) return@forEach
            val fixed = op.copy(completedMissionIds = op.completedMissionIds + missing)
            repository.saveOperator(fixed)
            val held = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet()
            val before = TacticalCalculator.earnedStartingAwards(op, held).toSet()
            TacticalCalculator.earnedStartingAwards(fixed, held).filter { it !in before }.forEachIndexed { i, medalId ->
                repository.addCitation(
                    AwardedMedal(
                        id = "cit_${UUID.randomUUID().toString().take(8)}",
                        operatorId = op.id,
                        medalId = medalId,
                        citationNote = "Earned before reporting to Aegis HQ.",
                        missionTitle = "Prior Service Record",
                        awardedAtMillis = now + i
                    )
                )
            }
        }
    }

    /**
     * Item 150 follow-up: saves from before the seniority rule can hold a Deputy who outranks the CO.
     * Swap them once so the senior officer commands (the Deputy cap would otherwise shrink the general).
     */
    /** Item 120: SF squads saved before team patches get their team's arrowhead once. */
    private suspend fun repairSfTeamPatches(state: CommandUiState) {
        val fixed = mutableListOf<TacticalSquad>()
        state.squads.filter { it.isSpecialForces && !it.standardPatch.isSfTeamPatch }.forEach { sq ->
            val current = state.squads.map { o -> fixed.find { it.id == o.id } ?: o }
            fixed += sq.copy(standardPatch = UnitPatchStyle.sfTeamPatchFor(sq, current))
        }
        if (fixed.isNotEmpty()) repository.saveSquads(fixed)
    }

    /** Item 146: SF squads that still carry an auto-assigned NATO name become ODA 1, 2, 3 once. */
    private suspend fun repairSfSquadNames(state: CommandUiState) {
        val renamed = mutableListOf<TacticalSquad>()
        state.squads.filter { it.isSpecialForces && SquadCohesionRules.isDefaultSquadName(it.name) && !it.name.trim().startsWith("ODA", true) }
            .forEach { sq ->
                val taken = state.squads.filter { it.id != sq.id }.map { o -> renamed.find { it.id == o.id } ?: o }
                renamed += sq.copy(name = SquadCohesionRules.nextDefaultSquadName(taken, specialForces = true))
            }
        if (renamed.isNotEmpty()) repository.saveSquads(renamed)
    }

    private suspend fun repairCommandSeniority(state: CommandUiState) {
        val seated = state.operators.filter { it.holdsHqSeat }
        val co = seated.find { it.hqRole == HqStaffRole.CO_COMMANDING_OFFICER } ?: return
        val dcg = seated.find { it.hqRole == HqStaffRole.DEPUTY_COMMANDER } ?: return
        if (dcg.earnedRank.seniorityIndex <= co.earnedRank.seniorityIndex) return
        repository.saveOperators(listOf(dcg.copy(hqRole = HqStaffRole.CO_COMMANDING_OFFICER), co.copy(hqRole = HqStaffRole.DEPUTY_COMMANDER)))
        showBanner("${dcg.earnedRank.code} ${dcg.callsign} takes command as CO; ${co.callsign} moves to Deputy.")
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
            RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_SERVICE_BLUES
            RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
        }
        val updatedMessDress = when (newRank.tier) {
            RankTier.SOLDIER, RankTier.NCO, RankTier.SNCO -> DressUniformStyle.ENLISTED_MESS_BLUES
            RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_MESS_BLUES
            RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_MESS_FORMAL
        }
        val updatedDress = if (op.dressStyle.isCutawayMess) updatedMessDress else updatedAlphasDress
        val updatedServiceCap = when (newRank.tier) {
            RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
            RankTier.NCO, RankTier.SNCO -> HeadgearStyle.NCO_SERVICE_CAP
            RankTier.OFFICER, RankTier.WARRANT -> HeadgearStyle.OFFICER_PEAKED_CAP
            RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
        }
        val updatedHeadgear = if (op.headgearStyle.kind == HeadgearKind.BERET) {
            op.headgearStyle
        } else {
            updatedServiceCap
        }
        // Item 127: a tier change resets the coat to Auto instead of saving it as a hand pick (a hand pick
        // outranks the squad standard, so promoted soldiers stopped following it). Keep real special picks.
        val nextAlphas = op.wardrobe.alphas.copy(
            dressStyle = op.wardrobe.alphas.dressStyle?.takeIf {
                !TacticalCalculator.isTierDefaultCoat(it) && it.requiredTier.order <= newRank.tier.order
            },
            // Keep a still-valid manual pick; otherwise Auto shows the new rank's cap
            headgearStyle = op.wardrobe.alphas.headgearStyle?.takeIf {
                it.kind != HeadgearKind.BERET && it.requiredTier.order >= newRank.tier.order
            }
        )
        val nextMess = op.wardrobe.mess.copy(
            dressStyle = op.wardrobe.mess.dressStyle?.takeIf {
                it.isCutawayMess && !TacticalCalculator.isTierDefaultCoat(it) && it.requiredTier.order <= newRank.tier.order
            },
            // Keep a still-valid manual pick; otherwise Auto shows the new rank's cap
            headgearStyle = op.wardrobe.mess.headgearStyle?.takeIf {
                it.kind != HeadgearKind.BERET && it.requiredTier.order >= newRank.tier.order
            }
        )
        val nextBravos = op.wardrobe.bravos.copy(
            dressStyle = op.wardrobe.bravos.dressStyle?.takeIf {
                !TacticalCalculator.isTierDefaultCoat(it) && it.requiredTier.order <= newRank.tier.order
            }
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
        squadId: String? = null,
        insertion: InsertionMethod = InsertionMethod.GROUND,
        pilotId: String? = null,
        platoonLeaderId: String? = null,
        flightHours: Int = 0
    ) {
        val state = uiState.value
        // Item 97: the Airfield limits how many helicopter missions fly at once
        if (insertion.isHelicopter) {
            val inAir = state.deployments.count { it.insertion.isHelicopter }
            val maxInAir = TacticalCalculator.maxHelicopterMissions(TacticalCalculator.facilityLevel(state.facilities, FacilityType.AIRFIELD))
            if (inAir >= maxInAir) {
                showBanner("Airfield can fly $maxInAir helicopter mission${if (maxInAir == 1) "" else "s"} at once. Upgrade the Airfield for more.")
                return
            }
        }
        if (mission.scope != MissionScope.TRAINING && state.occupiedDeploymentSlots >= state.maxDeploymentSlots) {
            showBanner("All HQ tasking slots occupied.")
            return
        }
        // Item 62: a Language school class studies one region; nobody in it can already speak it
        val languageRegion = if (mission.id == ClandestineRules.LANGUAGE_SCHOOL_ID) languageRegionChoice else null
        if (mission.id == ClandestineRules.LANGUAGE_SCHOOL_ID) {
            if (languageRegion == null) {
                showBanner("Pick a region for the Language school.")
                return
            }
            state.operators.firstOrNull { it.id in selectedOperatorIds && ClandestineRules.knowsLanguage(it, languageRegion) }?.let {
                showBanner("${it.callsign} already speaks the ${languageRegion.label} language.")
                return
            }
        }
        // Item 63: rescues and release talks need a POW and an open window (talks also cost prestige)
        val powTarget = if (PowRules.isPowOperation(mission)) state.operators.find { it.id == powOperationTargetId } else null
        if (PowRules.isPowOperation(mission)) {
            val team = state.operators.filter { it.id in selectedOperatorIds }
            PowRules.operationBlocker(mission, powTarget, state.deployments, state.resources.prestige, System.currentTimeMillis(), team)?.let {
                showBanner(it)
                return
            }
        }
        if (selectedOperatorIds.size !in mission.minOperators..mission.maxOperators) {
            showBanner("Select ${mission.teamSizeLabel} soldiers for ${mission.title}.")
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
        // Items 35/36: SF team makeup, or two squads with a platoon leader
        TacticalCalculator.teamBlocker(mission, chosenOps, state.squads, platoonLeaderId)?.let {
            showBanner(it)
            return
        }
        val platoonLeader = TacticalCalculator.platoonLeaderFor(mission, chosenOps, platoonLeaderId)
        // Item 33: the insertion must be allowed and the team (and pilot) qualified
        val pilot = if (insertion.isHelicopter) {
            state.operators.find { it.id == pilotId && it.id !in deployedIds && it.status == OperatorStatus.READY }
        } else null
        val insertionBlocker = TacticalCalculator.insertionBlocker(mission, insertion, chosenOps, pilot, state.facilities)
        if (insertionBlocker != null) {
            showBanner("${insertion.shortLabel} insertion: $insertionBlocker")
            return
        }
        val airOps = TacticalCalculator.airOpsBonusPct(state.operators)
        val effectiveSupplyCost = (TacticalCalculator.dispatchSupplyCost(
            mission = mission,
            soldierCount = chosenOps.size,
            multiplier = TacticalCalculator.supplyCostMultiplier(
                facilities = state.facilities,
                hqStaffSummary = state.hqStaffSummary,
                completedCampaignIds = state.completedCampaignIds
            )
        ) * SoldierEvents.schoolDiscountFactor(mission, chosenOps, System.currentTimeMillis()) + TacticalCalculator.insertionSupplyCost(insertion, state.facilities, airOps) +
            TacticalCalculator.schoolRetrySurcharge(
                mission, chosenOps,
                TacticalCalculator.supplyCostMultiplier(state.facilities, state.hqStaffSummary, state.completedCampaignIds)
            )).toDouble()
        if (state.resources.supplies < effectiveSupplyCost) {
            showBanner("Need ${effectiveSupplyCost.toInt()} SUP to deploy.")
            return
        }

        val activeBonus = SquadCohesionRules.evaluateActiveBonus(chosenOps, state.squads, mission.requiredSquads > 1)
        val resolvedSquadId = squadId?.takeIf { id -> state.squads.any { it.id == id } }
            ?: activeBonus?.squad?.id

        val bookedHours = if (mission.id == TacticalCalculator.FLIGHT_HOURS_MISSION_ID) {
            (flightHours.takeIf { it > 0 } ?: TacticalCalculator.FLIGHT_HOURS_PER_SESSION)
                .coerceIn(TacticalCalculator.MIN_FLIGHT_HOURS, TacticalCalculator.MAX_FLIGHT_HOURS)
        } else 0
        val hoursMult = if (bookedHours > 0) TacticalCalculator.flightHoursDurationMultiplier(bookedHours) else 1.0
        val durationMs = (mission.durationSeconds * 1000.0 * TacticalCalculator.durationMultiplier(state.facilities, state.hqStaffSummary) *
            TacticalCalculator.insertionDurationMultiplier(insertion, pilot) * hoursMult *
            // Item 165: Signals on the team shorten the mission
            MosEffects.durationFactor(mission, chosenOps)).toLong()
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
                squads = state.squads,
                smartWardrobeEnabled = state.smartWardrobeEnabled
            )
        }
        val now = System.currentTimeMillis()
        val deployment = ActiveDeployment(
            id = "dep_${UUID.randomUUID().toString().take(8)}",
            templateId = mission.id,
            // The pilot rides along, so they're listed (and marked deployed) with the team
            operatorIds = selectedOperatorIds + listOfNotNull(pilot?.id),
            startedAtMillis = now,
            durationMillis = durationMs,
            efficiencyPct = effPct,
            avgSurvivalPct = (previewOddsPct + if (mission.risk.isTraining) 0 else
                TacticalCalculator.insertionSurvivalDelta(insertion, pilot, airOps, scattered = false)).coerceIn(0, 98),
            squadId = resolvedSquadId,
            insertion = insertion,
            pilotId = pilot?.id,
            platoonLeaderId = platoonLeader?.id,
            missionTitle = when {
                powTarget != null -> "${mission.displayTitle}: ${powTarget.rank.code} ${powTarget.callsign}"
                languageRegion != null -> "${mission.displayTitle} (${languageRegion.label})"
                bookedHours > 0 -> "${mission.displayTitle} (${bookedHours}h)"
                // Item 60: temporary duty away from the unit
                mission.isTdy -> "TDY: ${mission.displayTitle.removePrefix("School: ")}"
                else -> mission.displayTitle
            },
            flightHours = bookedHours,
            targetOperatorId = powTarget?.id,
            languageRegion = languageRegion
        ).let { d ->
            // Item 62: clandestine missions roll now for whether (and when) the cover blows
            if (!mission.isClandestine) d else d.copy(
                coverBlownAtMillis = ClandestineRules.rollCoverBlownAt(
                    ClandestineRules.blownCoverChancePct(mission, state.operators.filter { it.id in selectedOperatorIds }),
                    d.startedAtMillis, d.durationMillis
                )
            )
        }
        val talksCost = if (mission.id == PowRules.NEGOTIATION_MISSION_ID) {
            PowRules.talksPrestigeCost(state.operators.filter { it.id in selectedOperatorIds })
        } else 0.0

        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(
                    supplies = (cur.supplies - effectiveSupplyCost).coerceAtLeast(0.0),
                    prestige = (cur.prestige - talksCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now
                )
            }
            repository.saveDeployment(deployment)
            val label = if (mission.risk.isTraining) "est. pass" else "est. survival"
            val squadTag = resolvedSquadId?.let { id -> state.squads.find { it.id == id }?.name }
            val prefix = if (squadTag != null) "Dispatched $squadTag: " else "Dispatched: "
            showBanner("$prefix${mission.displayTitle} (${previewOddsPct}% $label)")
        }
    }

    fun claimDeployment(deploymentId: String) {
        val state = uiState.value
        val deployment = state.deployments.find { it.id == deploymentId } ?: return
        // The name it launched under, even if the board has rotated since
        val mission = GameCatalog.missions.find { it.id == deployment.templateId }
            ?.let { it.copy(title = deployment.missionTitle ?: it.displayTitle) } ?: return
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

        /**
         * Session 15 (items 52 and 55): advance the soldier's service record and queue the routine awards
         * it earned. [updated] must already be the last entry for this soldier in updatedOperators.
         */
        fun addServiceAwards(updated: Operator, outcome: OperatorOutcomeType, succeeded: Boolean, isLeader: Boolean = false) {
            val pendingIds = state.pendingDeeds.filter { it.operatorId == updated.id }.map { it.recommendedMedalId }.toSet() +
                newDeeds.filter { it.operatorId == updated.id }.map { it.recommendedMedalId }
            val result = AwardRules.serviceAwardsFor(
                op = updated,
                mission = mission,
                outcome = outcome,
                missionSucceeded = succeeded,
                held = AwardRules.heldCounts(updated.id, state.citations),
                pending = pendingIds,
                rng = Random,
                isSquadOrPlatoonLeader = isLeader
            )
            val idx = updatedOperators.indexOfLast { it.id == updated.id }
            if (idx >= 0) updatedOperators[idx] = result.operator
            result.medalIds.forEach { medalId ->
                newDeeds.add(
                    PendingDeed(
                        id = "deed_${UUID.randomUUID().toString().take(8)}",
                        operatorId = updated.id,
                        missionTitle = mission.title,
                        deedSummary = AwardRules.serviceDeedText(medalId, mission),
                        recommendedMedalId = medalId,
                        createdAtMillis = now
                    )
                )
            }
        }
        val outcomes = mutableListOf<OperatorDebriefOutcome>()

        val missionSucceeded: Boolean
        var scatteredDrop = false
        val rifleQualResults = mutableListOf<TacticalCalculator.RifleQualResult>()
        if (mission.risk.isTraining) {
            var anyPassed = false
            for (op in targetOps) {
                val opGear = state.gearItems.equippedBy(op)
                val passOdds = TacticalCalculator.schoolPassOddsPct(
                    mission = mission,
                    operator = op,
                    citations = state.citations,
                    hqStaffSummary = state.hqStaffSummary,
                    facilities = state.facilities
                )
                // Rifle Qualification always completes; the score decides the badge
                val isRifleQual = mission.id == TacticalCalculator.RIFLE_QUAL_MISSION_ID
                val passed = isRifleQual || Random.nextInt(100) < passOdds
                if (passed) anyPassed = true

                val nextAttempts = (op.schoolAttempts[mission.id] ?: 0) + 1
                val updatedAttempts = op.schoolAttempts + (mission.id to nextAttempts)
                val outcome = if (passed) OperatorOutcomeType.TRAINING_PASSED else OperatorOutcomeType.TRAINING_FAILED
                val opXp = if (passed) xpEach else (xpEach * 0.35).roundToInt().coerceAtLeast(10)
                val prevRank = op.rank
                val newXp = OperatorRank.clampXpToAuthorizedTier(op.xp + opXp, op.authorizedTier)
                var newRank = op.copy(xp = newXp).rank
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

                val graduatedOp = op.copy(
                    xp = newXp,
                    baseFirepower = op.baseFirepower + (gradBonus?.firepower ?: 0),
                    baseStealth = op.baseStealth + (gradBonus?.stealth ?: 0),
                    baseTech = op.baseTech + (gradBonus?.tech ?: 0),
                    baseResilience = op.baseResilience + (gradBonus?.resilience ?: 0),
                    completedMissionIds = updatedCompletedIds,
                    schoolAttempts = updatedAttempts,
                    status = nextStatus,
                    eventBuffs = when {
                        isRifleQual -> op.eventBuffs.copy(rifleQualBonusPct = 0)
                        // Item 141: a pass banks the prep; any try at a pipeline school uses it up
                        mission.id == TacticalCalculator.SELECTION_PREP_ID && passed ->
                            op.eventBuffs.copy(selectionPrepPct = TacticalCalculator.SELECTION_PREP_BONUS_PCT)
                        mission.id in TacticalCalculator.SELECTION_PREP_SCHOOLS -> op.eventBuffs.copy(selectionPrepPct = 0)
                        else -> op.eventBuffs
                    },
                    languageRegions = if (passed && deployment.languageRegion != null) op.languageRegions + deployment.languageRegion else op.languageRegions
                ).let { grad -> applyCareerSchoolEffects(grad, mission.id, passed, deployment.flightHours) }
                newRank = graduatedOp.rank
                // Rifle Qualification, and graduating Marksmanship & Tactical Basics, both shoot for a badge
                val shootsForQual = isRifleQual || (passed && mission.id == "msn_basic_qual")
                val (updatedOp, qualNote) = if (shootsForQual) {
                    val score = TacticalCalculator.rifleQualScore(
                        op, state.citations, state.facilities, Random.nextInt(TacticalCalculator.QUAL_ROLL_MAX + 1)
                    )
                    val attemptsLeft = if (isRifleQual) (TacticalCalculator.MAX_REQUAL_ATTEMPTS - nextAttempts).coerceAtLeast(0) else null
                    val (qualifiedRaw, result) = TacticalCalculator.applyRifleQualification(graduatedOp, score, attemptsLeft)
                    // Range time counts as Firepower experience (Rifle Qualification attempts only)
                    val qualified = if (isRifleQual) TacticalCalculator.applyExperience(
                        qualifiedRaw, setOf(StatType.FIREPOWER), TacticalCalculator.EXP_POINTS_RIFLE_QUAL_FIREPOWER
                    ).first else qualifiedRaw
                    rifleQualResults.add(result)
                    val note = if (result.improved) {
                        " Qualified ${result.achieved.shortLabel} (score $score) • award pending."
                    } else {
                        " Score $score: no change (${result.previous?.shortLabel ?: "unqualified"} held)" +
                            (attemptsLeft?.let { " • $it attempts left." } ?: ".")
                    }
                    qualified to note
                } else {
                    graduatedOp to ""
                }
                updatedOperators.add(updatedOp)
                addServiceAwards(updatedOp, outcome, passed)

                val existingAndQueuedMedals = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet() +
                    state.pendingDeeds.filter { it.operatorId == op.id }.map { it.recommendedMedalId }.toSet()
                val (recMedalId, baseDeedText) = TacticalCalculator.recommendedMedalForMissionDeed(
                    mission = mission,
                    operator = updatedOp,
                    outcome = outcome,
                    equippedGear = opGear,
                    existingCitationMedalIds = existingAndQueuedMedals,
                    missionSucceeded = passed,
                    rollSeedPct = Random.nextInt(100),
                    ownedMedalIds = repeatableOwnedIds(state, op.id)
                )
                val maxAttempts = mission.schoolRequirements?.maxAttempts
                val attemptNote = if (!passed && maxAttempts != null) {
                    val left = maxAttempts - nextAttempts
                    if (left <= 0) " Past the attempt limit: the next try costs extra supplies." else " Attempts used: $nextAttempts/$maxAttempts."
                } else {
                    ""
                }
                val flightNote = when {
                    passed && mission.id == TacticalCalculator.WOCS_MISSION_ID -> " Appointed Warrant Officer 1."
                    passed && mission.id == TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID -> " Now a 153A Army Aviator (flight skill ${updatedOp.flightSkill})."
                    mission.id == TacticalCalculator.FLIGHT_HOURS_MISSION_ID -> " Flight skill ${op.flightSkill} → ${updatedOp.flightSkill}."
                    else -> ""
                }
                val deedText = baseDeedText + statGainNote + attemptNote + qualNote + flightNote
                if (passed && mission.id in setOf(
                        TacticalCalculator.FLIGHT_HOURS_MISSION_ID,
                        TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID,
                        TacticalCalculator.WOCS_MISSION_ID
                    )
                ) {
                    // Wings, the Aviation cord and wing upgrades follow from these schools: offer every one now earned
                    newDeeds.addAll(
                        newlyEligibleAwardDeeds(op, updatedOp, state, reasonTitle = "Flight Hours", now = now)
                            .filter { d -> newDeeds.none { it.operatorId == d.operatorId && it.recommendedMedalId == d.recommendedMedalId } }
                    )
                }

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
                        readyForCategoryRankCode = TacticalCalculator.promotionBoardReadyCode(
                            updatedOp, state.operators, state.citations, state.gearItems, state.facilities
                        )
                    )
                )
            }
            missionSucceeded = anyPassed
        } else {
            val pilotId = deployment.pilotId
            val groundOps = targetOps.filter { it.id != pilotId }
            val pilotOp = targetOps.find { it.id == pilotId }
            val airOps = TacticalCalculator.airOpsBonusPct(state.operators)
            // Airborne and HALO drops can scatter: harder mission, more casualties
            scatteredDrop = Random.nextInt(100) < TacticalCalculator.scatterChancePct(deployment.insertion)
            val insertionSurvival = TacticalCalculator.insertionSurvivalDelta(deployment.insertion, pilotOp, airOps, scatteredDrop)
            val platoonSurvBonus = if (TacticalCalculator.platoonLeaderFor(mission, groundOps, deployment.platoonLeaderId) != null) {
                TacticalCalculator.PLATOON_LEADER_SURVIVAL_BONUS
            } else 0
            PowRules.activeTalksFailures = deployment.targetOperatorId?.let { id -> state.operators.find { it.id == id }?.powTalksFailures } ?: 0
            val successOdds = TacticalCalculator.missionSuccessOddsPct(
                insertion = deployment.insertion,
                scatteredDrop = scatteredDrop,
                mission = mission,
                selectedOperators = groundOps,
                gearItems = state.gearItems,
                citations = state.citations,
                facilities = state.facilities,
                hqStaffCount = state.hqStaffCount,
                hqStaffSummary = state.hqStaffSummary,
                squads = state.squads,
                smartWardrobeEnabled = state.smartWardrobeEnabled
            )
            // Item 62: a blown cover makes the fight harder
            val blownPenalty = if (deployment.coverBlown) ClandestineRules.BLOWN_COVER_PASS_PENALTY else 0
            // Session 38: a squad that rallied for this captive fights harder to bring them home
            val rallyBonus = deployment.targetOperatorId?.let { target ->
                if (PowRules.isPowOperation(mission) && state.squads.any { sq -> target in sq.ralliedForIds && groundOps.any { it.id in sq.memberIds } })
                    MoraleRules.RALLY_RESCUE_PASS_PCT else 0
            } ?: 0
            missionSucceeded = Random.nextInt(100) < (successOdds - blownPenalty + rallyBonus).coerceAtLeast(5)
            val activeCohesionBonus = SquadCohesionRules.evaluateActiveBonus(groundOps, state.squads, mission.requiredSquads > 1)
            val cohesionSurvBonus = activeCohesionBonus?.survivalBonusPct ?: 0
            val camoSurvBonus = TacticalCalculator.teamCamoEffect(mission, groundOps, state.squads, state.smartWardrobeEnabled).survivalDelta

            var petitionAlreadyActive = state.operators.any {
                it.unlocked && !it.status.isOutOfActiveService && (it.transferRequested || it.retirementRequested)
            }

            for (op in targetOps) {
                val opGear = state.gearItems.equippedBy(op)
                val baseSurvivalPct = (TacticalCalculator.operatorSurvivalPct(
                    mission = mission,
                    operator = op,
                    gearItems = state.gearItems,
                    citations = state.citations,
                    facilities = state.facilities,
                    hqStaffCount = state.hqStaffCount,
                    hqStaffSummary = state.hqStaffSummary
                ) + (if (op.id == pilotId) 0 else cohesionSurvBonus + platoonSurvBonus + camoSurvBonus) + insertionSurvival).coerceIn(38, 98)
                val survivalPct = if (missionSucceeded) baseSurvivalPct else (baseSurvivalPct - 8).coerceAtLeast(38)
                val roll = Random.nextInt(100)
                val rolledOutcome = when {
                    roll < survivalPct -> {
                        if (mission.risk.baseDangerPenalty >= 14 && roll > survivalPct - 10) {
                            OperatorOutcomeType.WOUNDED
                        } else {
                            OperatorOutcomeType.SAFE
                        }
                    }
                    roll < survivalPct + 12 -> OperatorOutcomeType.WOUNDED
                    // Aid Station can pull a would-be fatality back as wounded
                    // Item 165: Engineers and Medics on the team pull more would-be fatalities back
                    Random.nextInt(100) < TacticalCalculator.aidSaveChancePct(state.facilities) + MosEffects.casualtySavePct(groundOps) -> OperatorOutcomeType.WOUNDED
                    else -> OperatorOutcomeType.FALLEN
                }
                // Item 63: anyone who lives through a field mission can be captured
                val outcome = if (rolledOutcome != OperatorOutcomeType.FALLEN &&
                    Random.nextDouble() * 100.0 < PowRules.captureChancePct(mission, op, missionSucceeded, blownCover = deployment.coverBlown) *
                        MosEffects.captureFactor(groundOps)
                ) OperatorOutcomeType.CAPTURED else rolledOutcome

                val wasHqStaff = op.status == OperatorStatus.HQ_STAFF
                val nextStatus = when (outcome) {
                    OperatorOutcomeType.SAFE,
                    OperatorOutcomeType.STAFF_SUPPORT,
                    OperatorOutcomeType.TRAINING_PASSED,
                    OperatorOutcomeType.TRAINING_FAILED ->
                        if (wasHqStaff) OperatorStatus.HQ_STAFF else OperatorStatus.READY
                    OperatorOutcomeType.WOUNDED -> OperatorStatus.RECOVERING
                    OperatorOutcomeType.FALLEN -> OperatorStatus.FALLEN
                    OperatorOutcomeType.CAPTURED -> OperatorStatus.CAPTURED
                }
                val recoveryAt = if (outcome == OperatorOutcomeType.WOUNDED) {
                    now + GameTiming.millis((TacticalCalculator.BASE_RECOVERY_MILLIS * TacticalCalculator.aidRecoveryMultiplier(state.facilities) *
                        // Item 118: the SF Compound's own medics speed SF soldiers' recovery
                        (if (op.id in CompoundRules.sfSquadMemberIds(state.squads)) CompoundRules.sfRecoveryMultiplier() else 1.0) *
                        MosEffects.recoveryFactor(groundOps)).roundToLong())
                } else {
                    0L
                }

                // Item 69: an answered op request or the lieutenant's own plan adds XP for that soldier
                val eventXpPct = (if (SoldierEvents.opRequestMet(op, mission, deployment.startedAtMillis)) SoldierEvents.OP_REQUEST_XP_BONUS_PCT else 0) +
                    (if (op.eventBuffs.leadsNextMission) SoldierEvents.LT_PLAN_XP_BONUS_PCT else 0)
                val opXp = ((if (missionSucceeded) xpEach else (xpEach * 0.45).roundToInt().coerceAtLeast(15)) * (100 + eventXpPct)) / 100
                val prevRank = op.rank
                val newXp = OperatorRank.clampXpToAuthorizedTier(op.xp + opXp, op.authorizedTier)
                val newRank = op.copy(xp = newXp).rank
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
                val sfWin = missionSucceeded && outcome != OperatorOutcomeType.FALLEN && outcome != OperatorOutcomeType.CAPTURED &&
                    TacticalCalculator.isSfOperation(mission) && TacticalCalculator.isSfQualified(op)
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
                // Campaign missions record the squad patch worn; the newest campaign becomes the combat patch.
                // Item 193: a standalone field op counts toward its theater's campaign
                val patchCampaignKey = TacticalCalculator.deploymentCampaignKey(mission)
                val updatedCombatPatchHistory = patchCampaignKey?.let { campaignId ->
                    // Item 100: aviators' combat patch is always the Aviation Brigade
                    val wornPatch = if (op.specialty == OperatorSpecialty.AVIATION) {
                        if (TacticalCalculator.isSfAviator(op)) UnitPatchStyle.SOA else UnitPatchStyle.AVIATION_BRIGADE
                    }
                        else UniformRegaliaRules.resolveUnitPatch(op, state.squads)
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
                    (Random.nextInt(100) < (6 * TacticalCalculator.dfacPetitionFactor(state.facilities) * MoraleRules.petitionFactor(op)).roundToInt()).also { triggered ->
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
                    (Random.nextInt(100) < (7 * TacticalCalculator.dfacPetitionFactor(state.facilities) * MoraleRules.petitionFactor(op)).roundToInt()).also { triggered ->
                        if (triggered) petitionAlreadyActive = true
                    }
                } else {
                    op.retirementRequested
                }

                val jumped = isCombatOp && op.id != pilotId && outcome != OperatorOutcomeType.FALLEN &&
                    (deployment.insertion == InsertionMethod.AIRBORNE || deployment.insertion == InsertionMethod.HALO)
                val (updatedOp, expNote) = TacticalCalculator.applyExperience(op.copy(
                    combatJumps = op.combatJumps + if (jumped) 1 else 0,
                    xp = newXp,
                    missionsCompleted = newOpsCount,
                    combatMissionsCompleted = newCombatOpsCount,
                    completedMissionIds = updatedCompletedIds,
                    // Session 30: successful SF missions count toward SF2 and SF3
                    sfMissionsSucceeded = op.sfMissionsSucceeded + if (sfWin) 1 else 0,
                    sfHighMissionsSucceeded = op.sfHighMissionsSucceeded +
                        if (sfWin && (mission.risk == MissionRisk.HIGH || mission.risk == MissionRisk.EXTREME)) 1 else 0,
                    highestRiskSurvived = updatedHighestRisk,
                    timesWounded = updatedWoundedCount,
                    combatPatchHistory = updatedCombatPatchHistory,
                    // Session 41: dwell time at home after a field op (1:1 with the time actually deployed)
                    dwellUntilMillis = if (mission.scope == MissionScope.FIELD_COMBAT && outcome != OperatorOutcomeType.FALLEN) {
                        val ran = (minOf(System.currentTimeMillis(), deployment.startedAtMillis + deployment.durationMillis) - deployment.startedAtMillis).coerceAtLeast(0L)
                        System.currentTimeMillis() + (ran * GameCalendar.DWELL_RATIO).toLong()
                    } else op.dwellUntilMillis,
                    status = nextStatus,
                    fallenNote = if (outcome == OperatorOutcomeType.FALLEN) "Killed in action: ${mission.displayTitle}" else op.fallenNote,
                    hqRole = if (outcome == OperatorOutcomeType.FALLEN) null else op.hqRole,
                    recoveryUntilMillis = recoveryAt,
                    transferRequested = askTransfer,
                    retirementRequested = if (outcome == OperatorOutcomeType.FALLEN) false else askRetirement,
                    // The pilot logs a combat sortie (Air Medal progress)
                    combatSortiesFlown = op.combatSortiesFlown + if (op.id == pilotId && isCombatOp) 1 else 0,
                    eventBuffs = op.eventBuffs.copy(
                        leadsNextMission = false,
                        opRequestType = op.eventBuffs.opRequestType.takeUnless { SoldierEvents.opRequestMet(op, mission, deployment.startedAtMillis) }
                    ),
                    flightHoursLogged = op.flightHoursLogged + if (op.id == pilotId) TacticalCalculator.FLIGHT_HOURS_PER_SORTIE else 0
                ),
                    // Item 40: the ground team trains the mission's stats; pilots log sorties instead
                    stats = if (op.id == pilotId) emptySet() else mission.trainsStats,
                    points = TacticalCalculator.missionExperiencePoints(mission, outcome, missionSucceeded)
                )
                // Item 63: record the capture first, so the service-award step builds on it instead of erasing it
                val recordedOp = if (outcome == OperatorOutcomeType.CAPTURED) PowRules.capture(updatedOp, mission, now) else updatedOp
                updatedOperators.add(recordedOp)
                val leadsTeam = op.id == TacticalCalculator.platoonLeaderFor(mission, groundOps, deployment.platoonLeaderId)?.id ||
                    state.squads.any { it.leaderId == op.id && groundOps.count { g -> g.id in it.memberIds } >= 2 }
                if (op.id != pilotId) addServiceAwards(recordedOp, outcome, missionSucceeded, leadsTeam)
                if (op.id == pilotId && isCombatOp && outcome != OperatorOutcomeType.FALLEN) {
                    val airMedal = GameCatalog.medals.first { it.id == TacticalCalculator.AIR_MEDAL_ID }
                    val owned = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet()
                    val alreadyPending = state.pendingDeeds.any { it.operatorId == op.id && it.recommendedMedalId == airMedal.id }
                    if (!alreadyPending &&
                        TacticalCalculator.evaluateAwardEligibility(airMedal, updatedOp, owned, state.citations).eligible
                    ) {
                        newDeeds.add(
                            PendingDeed(
                                id = "deed_${UUID.randomUUID().toString().take(8)}",
                                operatorId = op.id,
                                missionTitle = mission.title,
                                deedSummary = "Flew ${updatedOp.combatSortiesFlown} combat sorties as pilot in command, most recently ${mission.title}.",
                                recommendedMedalId = airMedal.id,
                                createdAtMillis = now
                            )
                        )
                    }
                }

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
                    allCampaignCitations = effectiveCampaignCitations,
                    ownedMedalIds = repeatableOwnedIds(state, op.id)
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
                // Item 166: combat jumps earn Senior, then Master Parachutist wings
                if (jumped) {
                    val ownedOrQueued = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet() +
                        state.pendingDeeds.filter { it.operatorId == op.id }.map { it.recommendedMedalId } +
                        newDeeds.filter { it.operatorId == op.id }.map { it.recommendedMedalId }
                    TacticalCalculator.parachutistMilestone(updatedOp, ownedOrQueued)?.let { wingsId ->
                        val wings = GameCatalog.medals.first { it.id == wingsId }
                        newDeeds.add(
                            PendingDeed(
                                id = "deed_${UUID.randomUUID().toString().take(8)}",
                                operatorId = op.id,
                                missionTitle = mission.title,
                                deedSummary = "Combat jump #${updatedOp.combatJumps} earned the ${wings.name}.",
                                recommendedMedalId = wingsId,
                                createdAtMillis = now
                            )
                        )
                    }
                }

                outcomes.add(
                    OperatorDebriefOutcome(
                        operatorId = op.id,
                        callsign = op.callsign,
                        rankCode = newRank.code,
                        outcome = outcome,
                        deedText = deedText + expNote + if (jumped) " Combat jump #${updatedOp.combatJumps}." else "",
                        recommendedMedalId = recMedalId,
                        xpEarned = opXp,
                        promotedToRankCode = if (outcome != OperatorOutcomeType.FALLEN && newRank != prevRank) newRank.code else null,
                        readyForCategoryRankCode = if (outcome != OperatorOutcomeType.FALLEN) {
                            TacticalCalculator.promotionBoardReadyCode(
                                updatedOp, state.operators, state.citations, state.gearItems, state.facilities
                            )
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
            (mission.rewardSupplies * mult * TacticalCalculator.campaignSupplyYieldMultiplier(state.completedCampaignIds)).roundToInt().toDouble()
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
            // Item 126: staff work earns real experience (was 30%, which left seated staff looking frozen)
            val staffXp = (xpEach * TacticalCalculator.HQ_STAFF_XP_SHARE).roundToInt().coerceAtLeast(TacticalCalculator.HQ_STAFF_MIN_XP)
            val nextSupportCount = staff.staffOpsSupported + 1
            val prevStaffRank = staff.rank
            val nextStaffXp = OperatorRank.clampXpToAuthorizedTier(staff.xp + staffXp, staff.authorizedTier)
            val nextStaffRank = staff.copy(xp = nextStaffXp).rank
            val candidateStaff = staff.copy(
                xp = nextStaffXp,
                staffOpsSupported = nextSupportCount
            )
            val readyGateCode = TacticalCalculator.promotionBoardReadyCode(
                candidateStaff, state.operators, state.citations, state.gearItems, state.facilities
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
                TacticalCalculator.evaluateRetirementEligibility(candidateStaff).eligible
            ) {
                (Random.nextInt(100) < (5 * TacticalCalculator.dfacPetitionFactor(state.facilities) * MoraleRules.petitionFactor(staff)).roundToInt()).also { triggered ->
                    if (triggered) staffPetitionBlocked = true
                }
            } else {
                staff.retirementRequested
            }
            val updatedStaff = TacticalCalculator.applyExperience(
                candidateStaff.copy(retirementRequested = staffAskRetirement),
                setOf(StatType.TECH),
                TacticalCalculator.EXP_POINTS_HQ_STAFF_TECH
            ).first
            updatedOperators.add(updatedStaff)
            if (missionSucceeded && !mission.risk.isTraining) {
                addServiceAwards(updatedStaff, OperatorOutcomeType.STAFF_SUPPORT, missionSucceeded)
            }
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
                missionSucceeded = missionSucceeded,
                gainMultiplier = TacticalCalculator.dfacCohesionMultiplier(state.facilities) *
                    (1.0 + state.hqStaffSummary.cohesionGainBonusPct / 100.0) *
                    // Item 165: a Command soldier on the team speeds cohesion
                    MosEffects.cohesionFactor(targetOps) *
                    // Session 38: so does a high-morale team
                    MoraleRules.cohesionFactor(targetOps)
            )
        }
        val postMissionDeployed = targetOps.map { updatedOpsById[it.id] ?: it }
        val citedSquads = participatingSquads.mapNotNull { squad ->
            SquadCohesionRules.earnedUnitCitation(squad, postMissionDeployed, mission, missionSucceeded, now)?.let { squad.id to it }
        }.toMap()
        val updatedSquadMap = (cohesionResults.associate { it.updatedSquad.id to it.updatedSquad } + participatingSquads
            .filter { it.id in citedSquads && cohesionResults.none { r -> r.updatedSquad.id == it.id } }
            .associateBy { it.id })
            .mapValues { (id, squad) ->
                citedSquads[id]?.let { squad.copy(unitCitations = squad.unitCitations + it) } ?: squad
            }
        val unitCitationNote = citedSquads.mapNotNull { (id, cit) -> state.squads.find { it.id == id }?.let { "${it.name} earned the ${cit.type.label}" } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
        val sanitizedSquadsToSave = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads.map { updatedSquadMap[it.id] ?: it },
            operators = effectiveRoster
        ).filter { updated ->
            state.squads.find { it.id == updated.id } != updated
        }
        val kitDebriefNote = kitBonus.summaryLabel?.takeIf { kitBonus.allMatched }
        val cohesionSummaryNote = listOfNotNull(
            cohesionResults.maxByOrNull { it.gainedXp }?.summaryNote,
            unitCitationNote,
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
                    val nextCamp = GameCatalog.campaigns.find { it.order == campaign.order + 1 && !it.isSpecialForces }
                    val bonusStr = "+${bonusCredits.toInt()} CR, +${bonusSupplies.toInt()} SUP, +${bonusIntel.toInt()} INT, +${bonusPrestige.toInt()} PRS • Perk: ${campaign.theaterBuffLabel}"
                    campaignProgressNote = if (allDone) {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr). ALL 10 CAMPAIGNS CLEARED! FULL REPLAY UNLOCKED!"
                    } else if (nextCamp != null) {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr). Unlocked ${nextCamp.title}!"
                    } else {
                        "CAMPAIGN CLEARED: ${campaign.title} ($bonusStr)"
                    }
                } else if (isNewStageClear && !state.allCampaignsCleared) {
                    val stageNum = mission.campaignStageOrder ?: 1
                    val totalStages = campaign.stageMissionIds.size
                    if (stageNum < totalStages) {
                        campaignProgressNote = "${campaign.title}: Stage $stageNum/$totalStages Cleared. Stage ${stageNum + 1} Unlocked!"
                    }
                }
            }
        }

        if (rifleQualResults.isNotEmpty()) {
            _rifleQualQueue.value = _rifleQualQueue.value + rifleQualResults
        }

        // Sovereign Star heroism roll: each soldier on an eligible mission rolls once; a hit replaces
        // that soldier's citation from this mission
        val anyCasualty = outcomes.any { it.outcome == OperatorOutcomeType.WOUNDED || it.outcome == OperatorOutcomeType.FALLEN }
        for (i in outcomes.indices) {
            val o = outcomes[i]
            val odds = TacticalCalculator.heroismOdds(mission, o.outcome, missionSucceeded, anyCasualty)
            if (odds <= 0.0 || Random.nextDouble() >= odds) continue
            val alreadyHas = state.citations.any { it.operatorId == o.operatorId && it.medalId == TacticalCalculator.SOVEREIGN_STAR_ID } ||
                state.pendingDeeds.any { it.operatorId == o.operatorId && it.recommendedMedalId == TacticalCalculator.SOVEREIGN_STAR_ID }
            if (alreadyHas) continue
            val text = TacticalCalculator.heroismDeedText(mission, o.outcome)
            newDeeds.removeAll { it.operatorId == o.operatorId && it.missionTitle == mission.title }
            newDeeds.add(
                PendingDeed(
                    id = "deed_${UUID.randomUUID().toString().take(8)}",
                    operatorId = o.operatorId,
                    missionTitle = mission.title,
                    deedSummary = text,
                    recommendedMedalId = TacticalCalculator.SOVEREIGN_STAR_ID,
                    createdAtMillis = now
                )
            )
            outcomes[i] = o.copy(deedText = text, recommendedMedalId = TacticalCalculator.SOVEREIGN_STAR_ID)
        }
        // Item 89: valor awards by chance on High and Extreme field ops; DFC for the pilot; Soldier's Medal
        // for heroism away from combat. A soldier who just earned the Sovereign Star doesn't roll again.
        for (i in outcomes.indices) {
            val o = outcomes[i]
            if (o.recommendedMedalId == TacticalCalculator.SOVEREIGN_STAR_ID) continue
            val isPilot = o.operatorId == deployment.pilotId
            val medalId = when {
                isPilot -> AwardRules.DFC_ID.takeIf {
                    o.outcome != OperatorOutcomeType.FALLEN && Random.nextDouble() < AwardRules.dfcOdds(mission, anyCasualty)
                }
                else -> AwardRules.valorAwardFor(mission, o.outcome, missionSucceeded, Random.nextDouble())
                    ?: AwardRules.SOLDIERS_MEDAL_ID.takeIf { Random.nextDouble() < AwardRules.soldiersMedalOdds(mission, o.outcome) }
            } ?: continue
            if (state.pendingDeeds.any { it.operatorId == o.operatorId && it.recommendedMedalId == medalId }) continue
            if (TacticalCalculator.isAtAwardLimit(o.operatorId, medalId, state.citations)) continue
            val text = AwardRules.heroismText(medalId, mission, o.outcome)
            newDeeds.add(
                PendingDeed(
                    id = "deed_${UUID.randomUUID().toString().take(8)}",
                    operatorId = o.operatorId,
                    missionTitle = mission.title,
                    deedSummary = text,
                    recommendedMedalId = medalId,
                    createdAtMillis = now
                )
            )
            if (o.recommendedMedalId == null) outcomes[i] = o.copy(deedText = text, recommendedMedalId = medalId)
        }
        // Item 98: The Prison Camp frees every Aegis soldier held as a POW
        if (mission.id == ClandestineRules.PRISON_CAMP_STAGE_ID && missionSucceeded) {
            state.operators.filter { it.status == OperatorStatus.CAPTURED && it.id !in deployment.operatorIds }.forEach { pow ->
                updatedOperators.add(PowRules.released(pow, now, TacticalCalculator.aidRecoveryMultiplier(state.facilities)))
                newDeeds.add(
                    PendingDeed(
                        id = "deed_${UUID.randomUUID().toString().take(8)}",
                        operatorId = pow.id,
                        missionTitle = mission.title,
                        deedSummary = "Held as a prisoner of war and freed in the raid on the prison camp.",
                        recommendedMedalId = PowRules.POW_MEDAL_ID,
                        createdAtMillis = now
                    )
                )
            }
        }
        // Item 63: a successful rescue or negotiation brings the POW home; failed talks have consequences
        val freedPow = deployment.targetOperatorId?.let { id -> state.operators.find { it.id == id && it.status == OperatorStatus.CAPTURED } }
        if (mission.id == PowRules.NEGOTIATION_MISSION_ID && !missionSucceeded && freedPow != null) {
            val (afterTalks, note) = PowRules.failedTalks(freedPow, now)
            updatedOperators.add(afterTalks)
            showBanner(note)
        }
        if (PowRules.isPowOperation(mission) && missionSucceeded && freedPow != null) {
            updatedOperators.add(PowRules.released(freedPow, now, TacticalCalculator.aidRecoveryMultiplier(state.facilities)))
            newDeeds.add(
                PendingDeed(
                    id = "deed_${UUID.randomUUID().toString().take(8)}",
                    operatorId = freedPow.id,
                    missionTitle = mission.title,
                    deedSummary = "Held as a prisoner of war and brought home by ${if (mission.id == PowRules.RESCUE_MISSION_ID) "a rescue raid" else "negotiated release"}.",
                    recommendedMedalId = PowRules.POW_MEDAL_ID,
                    createdAtMillis = now
                )
            )
            showBanner("${freedPow.rank.code} ${freedPow.callsign} is coming home.")
        }
        // Item 76: one pending award per soldier per award; skip anything already waiting
        val alreadyPending = state.pendingDeeds.map { it.operatorId to it.recommendedMedalId }.toSet()
        val uniqueDeeds = newDeeds.distinctBy { it.operatorId to it.recommendedMedalId }
            .filter { (it.operatorId to it.recommendedMedalId) !in alreadyPending }
        newDeeds.clear()
        newDeeds.addAll(uniqueDeeds)
        enqueueAwardPrompts(newDeeds)
        // Session 38: morale from this mission (results, wounds, losses, captures, homecomings, streaks)
        val (moraleOps, moraleSquads, moraleNote) = applyMissionMorale(
            state, mission, deployment, missionSucceeded, outcomes, updatedOperators, sanitizedSquadsToSave, now
        )
        moraleNote?.let { showBanner(it) }
        viewModelScope.launch {
            repository.removeDeployment(deployment.id)
            repository.saveOperators(moraleOps)
            if (moraleSquads.isNotEmpty()) {
                repository.saveSquads(moraleSquads)
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
                    lastSimulatedAtMillis = now,
                    // Item 69: the rehearsal bonus is spent on the next SF mission
                    sfRehearsalBonusPct = if (TacticalCalculator.isSfOperation(mission)) 0 else cur.sfRehearsalBonusPct
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
                squadCohesionNote = listOfNotNull(
                    when {
                        deployment.insertion == InsertionMethod.GROUND || mission.risk.isTraining -> null
                        scatteredDrop -> "${deployment.insertion.shortLabel} insertion: scattered drop (-${TacticalCalculator.SCATTER_PASS_PENALTY}% pass, higher casualties)."
                        else -> "${deployment.insertion.shortLabel} insertion went in clean."
                    },
                    cohesionSummaryNote
                ).joinToString(" ").ifBlank { null },
                campaignProgressNote = campaignProgressNote
            )
        }
    }

    /**
     * Session 38 (item 70): morale after a mission. Each soldier back from the field gains or loses morale for the
     * result, a wound, and a long run of deployments without rest. Each squad loses morale for a member killed;
     * for a member captured it rolls: shaken (-5, given back when they come home) or rallied (+5 and a rescue
     * bonus). Soldiers freed on this mission bring their squads' morale back. School graduates gain a little.
     */
    private fun applyMissionMorale(
        state: CommandUiState,
        mission: MissionTemplate,
        deployment: ActiveDeployment,
        missionSucceeded: Boolean,
        outcomes: List<OperatorDebriefOutcome>,
        updatedOperators: List<Operator>,
        squadsToSave: List<TacticalSquad>,
        now: Long
    ): Triple<List<Operator>, List<TacticalSquad>, String?> {
        val ops = LinkedHashMap<String, Operator>()
        updatedOperators.forEach { ops[it.id] = it }
        fun current(id: String): Operator? = ops[id] ?: state.operators.find { it.id == id }
        val outcomeById = outcomes.associateBy { it.operatorId }
        val notes = mutableListOf<String>()

        for (id in deployment.operatorIds) {
            val op = ops[id] ?: continue
            val o = outcomeById[id]?.outcome ?: continue
            if (o == OperatorOutcomeType.FALLEN || o == OperatorOutcomeType.CAPTURED) continue
            var next = op
            if (mission.scope == MissionScope.TRAINING) {
                if (o == OperatorOutcomeType.TRAINING_PASSED) next = MoraleRules.adjust(next, MoraleRules.SCHOOL_PASSED)
            } else {
                next = MoraleRules.adjust(next, if (missionSucceeded) MoraleRules.MISSION_SUCCESS else MoraleRules.MISSION_FAILURE)
                if (o == OperatorOutcomeType.WOUNDED) next = MoraleRules.adjust(next, MoraleRules.WOUNDED)
                if (mission.scope == MissionScope.FIELD_COMBAT) {
                    val streak = MoraleRules.nextStreak(op, deployment.startedAtMillis)
                    next = MoraleRules.adjust(next.copy(deployStreak = streak), MoraleRules.streakPenalty(streak))
                }
            }
            ops[id] = next.copy(lastReturnAtMillis = now)
        }

        // Squad-wide effects
        val squads = LinkedHashMap<String, TacticalSquad>()
        state.squads.forEach { sq -> squads[sq.id] = squadsToSave.find { it.id == sq.id } ?: sq }
        val changedSquadIds = squadsToSave.map { it.id }.toMutableSet()
        val fallenIds = outcomes.filter { it.outcome == OperatorOutcomeType.FALLEN }.map { it.operatorId }
        val capturedIds = outcomes.filter { it.outcome == OperatorOutcomeType.CAPTURED }.map { it.operatorId }
        for (sq in state.squads) {
            val lost = fallenIds.filter { it in sq.memberIds }
            if (lost.isNotEmpty()) {
                sq.memberIds.filter { it !in fallenIds && it !in capturedIds }.forEach { mid ->
                    current(mid)?.let { ops[mid] = MoraleRules.adjust(it, MoraleRules.SQUADMATE_KILLED * lost.size) }
                }
                notes += "${sq.name} mourns ${lost.size} lost."
            }
            for (cap in capturedIds.filter { it in sq.memberIds }) {
                val rallied = Random.nextBoolean()
                val delta = if (rallied) MoraleRules.RALLIED else MoraleRules.SQUADMATE_CAPTURED
                sq.memberIds.filter { it != cap && it !in fallenIds }.forEach { mid ->
                    current(mid)?.let { ops[mid] = MoraleRules.adjust(it, delta) }
                }
                val s = squads[sq.id]!!
                squads[sq.id] = if (rallied) s.copy(ralliedForIds = s.ralliedForIds + cap) else s.copy(shakenForIds = s.shakenForIds + cap)
                changedSquadIds += sq.id
                val name = current(cap)?.callsign ?: "a squadmate"
                notes += if (rallied) "${sq.name} rallied to bring $name home." else "${sq.name} is shaken by $name's capture."
            }
        }
        // Homecomings: anyone freed on this mission
        val freedIds = updatedOperators.filter { it.id !in deployment.operatorIds && state.operators.find { s -> s.id == it.id }?.status == OperatorStatus.CAPTURED && it.status != OperatorStatus.CAPTURED }.map { it.id }
        for (freed in freedIds) {
            val (sqs, changed) = homecoming(squads.values.toList(), freed, ops, state)
            sqs.forEach { squads[it.id] = it }
            changedSquadIds += changed
        }
        val squadList = squads.values.filter { it.id in changedSquadIds }
        return Triple(ops.values.toList(), squadList, notes.takeIf { it.isNotEmpty() }?.joinToString(" "))
    }

    /**
     * Session 38: a captured soldier is home. Squads shaken by the capture get their morale back; the rally ends.
     * Returns the updated squads and which changed; morale changes are written into [ops].
     */
    private fun homecoming(
        squads: List<TacticalSquad>,
        freedId: String,
        ops: MutableMap<String, Operator>,
        state: CommandUiState
    ): Pair<List<TacticalSquad>, Set<String>> {
        val changed = mutableSetOf<String>()
        val out = squads.map { sq ->
            if (freedId !in sq.shakenForIds && freedId !in sq.ralliedForIds) return@map sq
            changed += sq.id
            if (freedId in sq.shakenForIds) {
                sq.memberIds.filter { it != freedId }.forEach { mid ->
                    (ops[mid] ?: state.operators.find { it.id == mid })?.let { ops[mid] = MoraleRules.adjust(it, -MoraleRules.SQUADMATE_CAPTURED) }
                }
            }
            sq.copy(shakenForIds = sq.shakenForIds - freedId, ralliedForIds = sq.ralliedForIds - freedId)
        }
        return out to changed
    }

    /**
     * Item 103: cancel a school or abort a mission before it returns. Schools send everyone home READY and
     * count the attempt with no refund. Missions pay nothing, and each soldier can be wounded or killed
     * pulling out. An abort isn't a failure for awards, but it resets the Good Conduct streak.
     */
    fun cancelDeployment(deploymentId: String, casualtyFactor: Double = 1.0) {
        val state = uiState.value
        val deployment = state.deployments.find { it.id == deploymentId } ?: return
        val mission = GameCatalog.missions.find { it.id == deployment.templateId } ?: return
        val now = System.currentTimeMillis()
        if (deployment.isComplete(now)) return
        val title = deployment.missionTitle ?: mission.displayTitle
        val targetOps = state.operators.filter { it.id in deployment.operatorIds }
        fun homeStatus(op: Operator) = if (op.status == OperatorStatus.HQ_STAFF) OperatorStatus.HQ_STAFF else OperatorStatus.READY

        if (mission.risk.isTraining) {
            val updated = targetOps.map { op ->
                op.copy(
                    schoolAttempts = op.schoolAttempts + (mission.id to ((op.schoolAttempts[mission.id] ?: 0) + 1)),
                    status = homeStatus(op)
                )
            }
            viewModelScope.launch {
                repository.removeDeployment(deployment.id)
                repository.saveOperators(updated)
            }
            showBanner("$title cancelled. The attempt counts and the cost is not refunded.")
            return
        }

        val (rawWound, rawDeath) = TacticalCalculator.abortCasualtyOddsPct(
            mission.risk, deployment.progressFraction(now), deployment.insertion
        )
        val woundPct = rawWound * casualtyFactor
        val deathPct = rawDeath * casualtyFactor
        val updated = mutableListOf<Operator>()
        val newDeeds = mutableListOf<PendingDeed>()
        val outcomes = mutableListOf<OperatorDebriefOutcome>()
        for (op in targetOps) {
            val roll = Random.nextDouble() * 100.0
            val outcome = when {
                roll < deathPct -> OperatorOutcomeType.FALLEN
                roll < deathPct + woundPct -> OperatorOutcomeType.WOUNDED
                else -> OperatorOutcomeType.SAFE
            }
            val updatedOp = op.copy(
                status = when (outcome) {
                    OperatorOutcomeType.FALLEN -> OperatorStatus.FALLEN
                    OperatorOutcomeType.WOUNDED -> OperatorStatus.RECOVERING
                    else -> homeStatus(op)
                },
                fallenNote = if (outcome == OperatorOutcomeType.FALLEN) "Killed during the withdrawal from ${mission.displayTitle}" else op.fallenNote,
                recoveryUntilMillis = if (outcome == OperatorOutcomeType.WOUNDED) {
                    now + GameTiming.millis((TacticalCalculator.BASE_RECOVERY_MILLIS * TacticalCalculator.aidRecoveryMultiplier(state.facilities) *
                        // Item 118: the SF Compound's own medics speed SF soldiers' recovery
                        (if (op.id in CompoundRules.sfSquadMemberIds(state.squads)) CompoundRules.sfRecoveryMultiplier() else 1.0)).roundToLong())
                } else {
                    op.recoveryUntilMillis
                },
                timesWounded = op.timesWounded + if (outcome == OperatorOutcomeType.WOUNDED) 1 else 0,
                hqRole = if (outcome == OperatorOutcomeType.FALLEN) null else op.hqRole,
                goodConductStreak = 0
            )
            updated.add(updatedOp)
            val casualty = outcome == OperatorOutcomeType.WOUNDED || outcome == OperatorOutcomeType.FALLEN
            val deedText = when (outcome) {
                OperatorOutcomeType.FALLEN -> "Killed in action during the withdrawal from $title."
                OperatorOutcomeType.WOUNDED -> "Wounded during the withdrawal from $title."
                else -> "Withdrew safely when $title was aborted."
            }
            val purple = "medal_purple_shield"
            val queuePurple = casualty &&
                state.pendingDeeds.none { it.operatorId == op.id && it.recommendedMedalId == purple } &&
                !TacticalCalculator.isAtAwardLimit(op.id, purple, state.citations)
            if (queuePurple) {
                newDeeds.add(
                    PendingDeed(
                        id = "deed_${UUID.randomUUID().toString().take(8)}",
                        operatorId = op.id,
                        missionTitle = title,
                        deedSummary = deedText,
                        recommendedMedalId = purple,
                        createdAtMillis = now
                    )
                )
            }
            outcomes.add(
                OperatorDebriefOutcome(
                    operatorId = op.id,
                    callsign = op.callsign,
                    rankCode = op.rank.code,
                    outcome = outcome,
                    deedText = deedText,
                    recommendedMedalId = if (queuePurple) purple else null,
                    xpEarned = 0,
                    promotedToRankCode = null,
                    readyForCategoryRankCode = null
                )
            )
        }
        val effectiveRoster = state.operators.map { o -> updated.find { it.id == o.id } ?: o }
        val prunedSquads = SquadCohesionRules.pruneUnavailableMembers(state.squads, effectiveRoster)
            .filter { sq -> state.squads.find { it.id == sq.id } != sq }
        enqueueAwardPrompts(newDeeds)
        viewModelScope.launch {
            repository.removeDeployment(deployment.id)
            repository.saveOperators(updated)
            if (prunedSquads.isNotEmpty()) repository.saveSquads(prunedSquads)
            if (newDeeds.isNotEmpty()) repository.addPendingDeeds(newDeeds)
            _lastDebrief.value = DebriefSummary(
                missionTitle = "$title (Aborted)",
                theaterName = mission.theater.label,
                missionSucceeded = false,
                isTrainingMission = false,
                efficiencyPct = deployment.efficiencyPct,
                creditsEarned = 0.0,
                suppliesEarned = 0.0,
                intelEarned = 0.0,
                prestigeEarned = 0.0,
                operatorOutcomes = outcomes,
                squadCohesionNote = "Mission aborted at ${(deployment.progressFraction(now) * 100).roundToInt()}%. No rewards; the team pulled out."
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
        val activeFieldCount = CompoundRules.fieldRosterUsed(state.operators, state.squads, state.facilities)
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
            repository.saveOperator(op.copy(unlocked = true, nativeRegion = op.nativeRegion ?: ClandestineRules.rollNativeRegion()))
            // Item 43: cadre transfers arrive holding everything their record earns
            val held = state.citations.filter { it.operatorId == op.id }.map { it.medalId }.toSet()
            TacticalCalculator.earnedStartingAwards(op.copy(unlocked = true), held).forEachIndexed { i, medalId ->
                repository.addCitation(
                    AwardedMedal(
                        id = "cit_${UUID.randomUUID().toString().take(8)}",
                        operatorId = op.id,
                        medalId = medalId,
                        citationNote = "Earned before transferring to Aegis HQ.",
                        missionTitle = "Prior Service Record",
                        awardedAtMillis = now + i
                    )
                )
            }
            showBanner("${op.rank.code} ${op.callsign} (${op.rank.tier.shortLabel}) reported to HQ!")
        }
    }

    fun enlistPostBasicSoldier(specialty: OperatorSpecialty) = enlistRecruit(specialty, RecruitSource.RECEPTION)

    /** Item 31: recruit from Reception Station or a leadership school at that school's rank and record. */
    fun enlistRecruit(specialty: OperatorSpecialty, source: RecruitSource) {
        val state = uiState.value
        val activeFieldCount = CompoundRules.fieldRosterUsed(state.operators, state.squads, state.facilities)
        if (activeFieldCount >= state.maxFieldRosterSlots) {
            showBanner("Field roster full (${state.maxFieldRosterSlots}/${state.maxFieldRosterSlots}). Transfer out, assign SGT+ to HQ Staff, or upgrade HQ.")
            return
        }
        val enlistCost = SoldierEvents.discountedRecruitCost(source.creditCost)
        if (state.resources.credits < enlistCost) {
            showBanner("Need ${enlistCost.toInt()} CR to recruit a ${source.arrivalRank.code} from ${source.label}.")
            return
        }
        val raw = GameCatalog.createRecruit(
            id = "op_rec_${UUID.randomUUID().toString().take(6)}",
            requestedSpecialty = specialty,
            source = source,
            existingCallsigns = state.operators.map { it.callsign }.toSet(),
            usedNames = state.operators.map { it.name }.toSet(),
            seedIndex = state.operators.size + Random.nextInt(100)
        )
        // Officers and warrant officers arrive in their tier's uniforms; item 133: some speak a region's language already
        val recruit = applyTierProgressionOnRankUp(raw, OperatorRank.PV2, raw.rank).copy(nativeRegion = ClandestineRules.rollNativeRegion())
        // Item 128: the package, then everything else the recruit's record already earns (badges, tabs, cords),
        // so nothing waits in the awards queue to be handed out by hand
        val awardIds = TacticalCalculator.recruitArrivalAwards(recruit, source)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(
                    credits = (cur.credits - enlistCost).coerceAtLeast(0.0),
                    lastSimulatedAtMillis = now,
                    recruitDiscountPct = 0
                )
            }
            repository.saveOperator(recruit)
            // One of each award at most (the per-soldier caps from item 5 hold for every source)
            awardIds.forEachIndexed { i, medalId ->
                repository.addCitation(
                    AwardedMedal(
                        id = "cit_${UUID.randomUUID().toString().take(8)}",
                        operatorId = recruit.id,
                        medalId = medalId,
                        citationNote = if (source == RecruitSource.RECEPTION) {
                            "Completed Basic Combat Training — arrived at HQ post-Basic with no specialty schools."
                        } else {
                            "Prior service record on arrival from ${source.label}."
                        },
                        missionTitle = if (medalId == "medal_basic_training") "Basic Combat Training Graduation" else "Prior Service Record",
                        awardedAtMillis = now + i
                    )
                )
            }
            showBanner(
                if (source == RecruitSource.RECEPTION) "${recruit.rank.code} ${recruit.callsign} (${recruit.mosTag}) arrived post-Basic!"
                else "${recruit.rank.code} ${recruit.callsign} (${recruit.mosTag}) reported from ${source.label}!"
            )
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
            .filter { it.holdsHqSeat }
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

        val updatedStaffOp = op.copy(
            // HQ staff carry no field kit
            equippedGearIds = emptyMap(),
            status = OperatorStatus.HQ_STAFF,
            hqRole = targetRole,
            transferRequested = false,
            retirementRequested = false,
            uniformMode = UniformMode.DRESS_UNIFORM,
            // Item 91: HQ staff default to Alphas
            activeWardrobeKit = WardrobeKit.ALPHAS
        )
        val updatedSquads = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads,
            operators = state.operators.map { if (it.id == op.id) updatedStaffOp else it }
        ).filter { updated -> state.squads.find { it.id == updated.id } != updated }

        // Item 90: the gold command aiguillette comes with an HQ billet
        val hasAiguillette = state.citations.any { it.operatorId == op.id && it.medalId == AwardRules.AIGUILLETTE_ID }
        viewModelScope.launch {
            repository.saveOperator(updatedStaffOp)
            if (updatedSquads.isNotEmpty()) {
                repository.saveSquads(updatedSquads)
            }
            if (!hasAiguillette) {
                repository.addCitation(
                    AwardedMedal(
                        id = "cit_${UUID.randomUUID().toString().take(8)}",
                        operatorId = op.id,
                        medalId = AwardRules.AIGUILLETTE_ID,
                        citationNote = "Authorized on joining the HQ staff as ${targetRole.title}.",
                        missionTitle = "HQ Staff: ${targetRole.shortCode}",
                        awardedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            showBanner("${op.rank.code} ${op.callsign} assigned as ${targetRole.shortCode} (${targetRole.title})!" +
                if (!hasAiguillette) " Gold aiguillette authorized." else "")
        }
    }

    /** Item 86: release an HQ staffer back to field duty. Frees the billet; needs an open field roster slot. */
    fun returnHqStaffToField(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find {
            it.id == operatorId && it.unlocked && it.status == OperatorStatus.HQ_STAFF
        } ?: return
        val activeFieldCount = CompoundRules.fieldRosterUsed(state.operators, state.squads, state.facilities)
        if (activeFieldCount >= state.maxFieldRosterSlots) {
            showBanner("Field roster full (${state.maxFieldRosterSlots}/${state.maxFieldRosterSlots}). Free a field slot before releasing ${op.callsign}.")
            return
        }
        val oldRole = op.hqRole
        viewModelScope.launch {
            repository.saveOperator(
                op.copy(
                    status = OperatorStatus.READY,
                    hqRole = null,
                    uniformMode = UniformMode.FIELD_TACTICAL,
                    activeWardrobeKit = if (op.isAviator) op.activeWardrobeKit else WardrobeKit.FIELD
                )
            )
            showBanner(
                "${op.rank.code} ${op.callsign} released from ${oldRole?.shortCode ?: "HQ Staff"} and back on field duty."
            )
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
            it.id != operatorId && it.holdsHqSeat && it.hqRole == newRole
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

    /**
     * Item 46: transfer a soldier to another command. Works for any rank at HQ. Soldiers who asked for the
     * transfer earn +10 PRS; a player-initiated transfer earns nothing. The soldier stays on file as
     * TRANSFERRED so their name and callsign aren't reused.
     */
    fun approveTransferOut(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked && !it.status.isOutOfActiveService } ?: return
        val deployedOpIds = state.deployments.flatMap { it.operatorIds }.toSet()
        if (op.id in deployedOpIds || op.status == OperatorStatus.DEPLOYED) {
            showBanner("Cannot transfer ${op.callsign} while on active deployment.")
            return
        }
        if (op.status == OperatorStatus.RECOVERING) {
            showBanner("${op.callsign} is in the Medbay. Transfer after recovery.")
            return
        }
        val requested = op.transferRequested
        val updatedSquads = SquadCohesionRules.pruneUnavailableMembers(
            squads = state.squads,
            operators = state.operators.filter { it.id != op.id }
        ).filter { updated -> state.squads.find { it.id == updated.id } != updated }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (updatedSquads.isNotEmpty()) {
                repository.saveSquads(updatedSquads)
            }
            if (requested) {
                repository.updateCommanderState { cur ->
                    cur.copy(prestige = cur.prestige + 10.0, lastSimulatedAtMillis = now)
                }
            }
            repository.saveOperator(
                op.copy(
                    status = OperatorStatus.TRANSFERRED,
                    hqRole = null,
                    transferRequested = false,
                    retirementRequested = false
                )
            )
            showBanner(
                if (requested) "${op.rank.code} ${op.callsign} transferred to another command (+10 PRS). Roster slot freed."
                else "${op.rank.code} ${op.callsign} transferred to another command. Roster slot freed."
            )
        }
    }

    fun declineTransferRequest(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.transferRequested } ?: return
        val cooldownTarget = op.missionsCompleted + op.staffOpsSupported + TacticalCalculator.PETITION_COOLDOWN_OPS
        viewModelScope.launch {
            // Session 38: a declined request costs morale
            repository.saveOperator(
                MoraleRules.adjust(op, MoraleRules.REQUEST_DECLINED).copy(
                    transferRequested = false,
                    petitionCooldownUntilOps = cooldownTarget
                )
            )
            showBanner("${op.rank.code} ${op.callsign} retained at Aegis HQ.")
        }
    }

    /** Session 41: stamp join date, age, prior service and time in grade; restart time in grade on a new rank. */
    private suspend fun stampServiceRecords(state: CommandUiState) {
        val today = GameCalendar.today
        val changed = state.operators.mapNotNull { op ->
            if (!op.unlocked || op.status.isOutOfActiveService) return@mapNotNull null
            when {
                op.joinedDay < 0 -> op.copy(
                    joinedDay = today,
                    ageAtJoin = GameCalendar.arrivalAge(op),
                    priorServiceDays = GameCalendar.typicalServiceYears(op.rank) * GameCalendar.DAYS_PER_YEAR,
                    rankSinceDay = today,
                    rankSinceName = op.rank.name,
                    goodConductSinceDay = today
                )
                op.rankSinceName != op.rank.name -> op.copy(rankSinceDay = today, rankSinceName = op.rank.name)
                else -> null
            }
        }
        if (changed.isNotEmpty()) repository.saveOperators(changed)
    }

    /** Session 41: 30 years of service, or a general at 62, means retirement now (once they're home). */
    private val mandatoryRetiring = mutableSetOf<String>()

    private fun enforceMandatoryRetirement(state: CommandUiState, now: Long) {
        val deployed = state.deployments.flatMap { it.operatorIds }.toSet()
        state.operators.firstOrNull {
            it.unlocked && !it.status.isOutOfActiveService && it.status != OperatorStatus.CAPTURED &&
                it.id !in deployed && it.joinedDay >= 0 && it.id !in mandatoryRetiring && GameCalendar.mustRetire(it)
        }?.let { mandatoryRetiring += it.id; approveRetirement(it.id, forced = true) }
    }

    /** Testing build only: tapping the calendar moves it a month (service, ages and time rules; timers keep running). */
    fun advanceCalendarMonth() {
        if (GameTiming.MODE != com.example.domain.TimeMode.TESTING) return
        viewModelScope.launch {
            repository.updateCommanderState { it.copy(calendarDays = it.calendarDays + GameCalendar.DAYS_PER_MONTH) }
            showBanner("Calendar advanced a month (testing).")
        }
    }

    fun approveRetirement(operatorId: String, forced: Boolean = false) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val deployedOpIds = state.deployments.flatMap { it.operatorIds }.toSet()
        if (op.id in deployedOpIds || op.status == OperatorStatus.DEPLOYED) {
            showBanner("Cannot retire ${op.callsign} while on active deployment.")
            return
        }
        val elig = TacticalCalculator.evaluateRetirementEligibility(op)
        if (!forced && !elig.eligible) {
            showBanner("${op.callsign} not yet eligible: ${elig.reasonText}")
            return
        }
        val now = System.currentTimeMillis()
        val preview = TacticalCalculator.previewRetirementLegacy(
            operator = op,
            currentLegacyBps = state.veteranLegacyBps,
            lastRetirementAtMillis = state.lastRetirementAtMillis,
            nowMillis = now
        )

        val retiredOp = op.copy(
            equippedGearIds = emptyMap(),
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
            val forcedNote = if (forced) "Mandatory retirement: " else ""
            val msg = forcedNote + if (preview.atCap) {
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
                MoraleRules.adjust(op, MoraleRules.REQUEST_DECLINED).copy(
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
        // Headgear is saved only when picked by hand; otherwise the kit keeps Auto (or its earlier manual pick)
        val savedHeadgear: HeadgearStyle? = when {
            request.headgearAuto -> null
            request.headgearTouched -> finalHeadgear
            else -> currentKitSettings.headgearStyle
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
        val autoCord = UniformTailorOverrides.SLOT_AUTO
        val finalLeftCord = if (request.equippedLeftCordId == autoCord) autoCord else request.equippedLeftCordId?.takeIf { id ->
            id in ownedMedalIds && medalsById[id]?.let {
                it.category == AwardCategory.SHOULDER_CORD &&
                    (freeOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = false))
            } == true
        }
        val finalRightCord = if (request.equippedRightCordId == autoCord) autoCord else request.equippedRightCordId?.takeIf { id ->
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
            environmentSlot = sanitizeSlot(ov.environmentSlot) { it.id in TacticalCalculator.ENVIRONMENT_BADGE_IDS },
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

        // Only hand-picked fields become manual picks; untouched ones keep following Auto or the squad standard
        val reset = request.followSquadStandard
        val savedDress: DressUniformStyle? = when {
            reset || request.dressAuto -> null
            request.dressTouched -> finalDress
            else -> currentKitSettings.dressStyle
        }
        val savedCamo: CamoPattern? = when {
            reset || request.camoAuto -> null
            request.camoTouched -> request.camoPattern
            else -> currentKitSettings.camoPattern
        }
        val savedGloves: Boolean? = when {
            reset || request.glovesAuto -> null
            request.glovesTouched -> finalGloves
            else -> currentKitSettings.hasWhiteGloves
        }
        val savedKitHeadgear = if (reset) null else savedHeadgear
        val updatedKitSettings = when (targetKit) {
            WardrobeKit.FIELD -> currentKitSettings.copy(camoPattern = savedCamo)
            WardrobeKit.BRAVOS -> currentKitSettings.copy(
                dressStyle = savedDress,
                headgearStyle = savedKitHeadgear,
                dressDisplayMode = if (request.dressDisplayMode == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.RIBBONS
                },
                hasWhiteGloves = false,
                hasDressSash = false
            )
            WardrobeKit.ALPHAS -> currentKitSettings.copy(
                dressStyle = savedDress,
                headgearStyle = savedKitHeadgear,
                dressDisplayMode = request.dressDisplayMode,
                hasWhiteGloves = savedGloves,
                hasDressSash = finalSash,
                equippedLeftCordId = finalLeftCord,
                equippedRightCordId = finalRightCord,
                cordCustomized = true
            )
            WardrobeKit.MESS -> currentKitSettings.copy(
                dressStyle = savedDress,
                headgearStyle = savedKitHeadgear,
                dressDisplayMode = if (request.dressDisplayMode == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.FULL_MEDALS
                },
                hasWhiteGloves = savedGloves,
                hasDressSash = false
            )
        }
        var updatedWardrobe = op.wardrobe.withKit(targetKit, updatedKitSettings)
        // Cords live on the Alphas settings for Alphas and Bravos alike
        if (targetKit != WardrobeKit.ALPHAS) {
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
                    camoPattern = if (targetKit == WardrobeKit.FIELD && request.camoTouched && !request.camoAuto) request.camoPattern else op.camoPattern,
                    dressStyle = if (syncTopLevelDress && targetKit != WardrobeKit.FIELD && request.dressTouched && !request.dressAuto && !reset) finalDress else op.dressStyle,
                    headgearStyle = if (syncTopLevelDress && targetKit != WardrobeKit.FIELD && request.headgearTouched && !request.headgearAuto) finalHeadgear else op.headgearStyle,
                    equippedLeftCordId = finalLeftCord,
                    equippedRightCordId = finalRightCord,
                    hasWhiteGloves = if (targetKit == WardrobeKit.ALPHAS && request.glovesTouched && !request.glovesAuto && !reset) finalGloves else op.hasWhiteGloves,
                    hasDressSash = if (targetKit == WardrobeKit.ALPHAS) finalSash else op.hasDressSash,
                    tailorOverrides = finalOverrides,
                    wardrobe = updatedWardrobe,
                    skinToneOverride = request.skinToneOverride,
                    bodyBuildOverride = request.bodyBuildOverride
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

    /** Item 94: set the team's field camo from mission prep; null puts it back on Auto. */
    fun setTeamFieldCamo(operatorIds: List<String>, camo: CamoPattern?) {
        val state = uiState.value
        val updated = operatorIds.distinct().mapNotNull { id ->
            val op = state.operators.find { it.id == id && !it.isAviator } ?: return@mapNotNull null
            op.copy(wardrobe = op.wardrobe.withKit(WardrobeKit.FIELD, op.wardrobe.field.copy(camoPattern = camo)))
        }
        if (updated.isEmpty()) return
        viewModelScope.launch {
            repository.saveOperators(updated)
            showBanner(if (camo == null) "Team camo set to Auto." else "Team camo set to ${camo.label}.")
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
        val equipElig = TacticalCalculator.evaluateGearEquipEligibility(gear, operator)
        if (!equipElig.eligible) {
            showBanner("${operator.callsign} cannot equip ${gear.name}: ${equipElig.reasonText}")
            return
        }
        // Gear types are shared: equipping never takes the item from anyone else
        viewModelScope.launch {
            repository.saveOperator(operator.copy(equippedGearIds = operator.equippedGearIds + (gear.slot to gear.id)))
            showBanner("Equipped ${gear.name}")
        }
    }

    fun unequipGear(operatorId: String, gearId: String) {
        val state = uiState.value
        val operator = state.operators.find { it.id == operatorId } ?: return
        val gear = state.gearItems.find { it.id == gearId } ?: return
        val deployedOps = state.deployments.flatMap { it.operatorIds }.toSet()
        if (operatorId in deployedOps) {
            showBanner("Cannot unequip gear while deployed.")
            return
        }
        if (operator.equippedGearIds[gear.slot] != gear.id) return
        viewModelScope.launch {
            repository.saveOperator(operator.copy(equippedGearIds = operator.equippedGearIds - gear.slot))
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
        if (result.changedOperators.isEmpty()) {
            showBanner("Deployable field soldiers already hold their optimal loadouts.")
            return
        }
        viewModelScope.launch {
            repository.saveOperators(result.changedOperators)
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
    ): Boolean {
        val state = uiState.value
        val operator = state.operators.find { it.id == operatorId && it.unlocked } ?: return false
        val medal = GameCatalog.medals.find { it.id == medalId } ?: return false

        val ownedMedalIds = state.citations.filter { it.operatorId == operatorId }.map { it.medalId }.toSet()
        val alreadyAwarded = medalId in ownedMedalIds
        val repeatable = TacticalCalculator.isRepeatableAward(medal)
        if (alreadyAwarded && !repeatable) {
            showBanner("${operator.callsign} already holds ${medal.shortName}.")
            return false
        }
        val eligibility = TacticalCalculator.evaluateAwardEligibility(
            medal = medal,
            operator = operator,
            ownedMedalIds = ownedMedalIds,
            allCitations = state.citations,
            heroismCited = state.pendingDeeds.any { it.operatorId == operatorId && it.recommendedMedalId == medalId }
        )
        if (!eligibility.eligible) {
            showBanner("${medal.shortName} locked: ${eligibility.reasonText}")
            return false
        }
        if (state.resources.prestige < medal.prestigeCost) {
            showBanner("Need ${medal.prestigeCost} PRS to confer ${medal.name}.")
            return false
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
            // Session 38: a presented award lifts morale
            val cord = afterAward(MoraleRules.adjust(operator, MoraleRules.AWARD_PRESENTED), medal, ownedMedalIds + medalId, now)
            showBanner(
                "Awarded ${medal.name} to ${operator.rank.code} ${operator.callsign}!" +
                    (cord?.let { " The ${it.shortName} cord comes with it." } ?: "")
            )
        }
        return true
    }

    /**
     * What follows any award: higher aviator wings replace lower ones, a cord brings the soldier into
     * dress uniform, and (item 53) the award's related cord is granted with it at no cost. Returns that cord.
     */
    private suspend fun afterAward(operator: Operator, medal: MedalDefinition, ownedAfter: Set<String>, now: Long): MedalDefinition? {
        when (medal.id) {
            TacticalCalculator.SENIOR_AVIATOR_WINGS_ID ->
                repository.removeCitation(operator.id, TacticalCalculator.AVIATOR_WINGS_ID)
            TacticalCalculator.MASTER_AVIATOR_WINGS_ID -> {
                repository.removeCitation(operator.id, TacticalCalculator.SENIOR_AVIATOR_WINGS_ID)
                repository.removeCitation(operator.id, TacticalCalculator.AVIATOR_WINGS_ID)
            }
        }
        val cord = AwardRules.companionCordFor(medal.id)
            ?.takeIf { it !in ownedAfter }
            ?.let { id -> GameCatalog.medals.find { it.id == id } }
            ?.takeIf { TacticalCalculator.evaluateAwardEligibility(it, operator, ownedAfter).eligible }
        if (cord != null) {
            repository.addCitation(
                AwardedMedal(
                    id = "cit_${UUID.randomUUID().toString().take(8)}",
                    operatorId = operator.id,
                    medalId = cord.id,
                    citationNote = "Comes with the ${medal.name}.",
                    missionTitle = "With the ${medal.shortName}",
                    awardedAtMillis = now
                )
            )
        }
        // Newly awarded cords show through Auto at render time; just bring the soldier into dress uniform.
        // Session 38: always save, so the morale the caller added is kept.
        if (medal.category == AwardCategory.SHOULDER_CORD || cord != null) {
            repository.saveOperator(
                operator.copy(
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    activeWardrobeKit = if (operator.activeWardrobeKit == WardrobeKit.FIELD) WardrobeKit.ALPHAS else operator.activeWardrobeKit
                )
            )
        } else {
            repository.saveOperator(operator)
        }
        return cord
    }

    /**
     * Item 51: grants every listed award that isn't a repeat, in order, while prestige lasts. Stops and
     * says so when prestige runs out. Returns the ids of the deeds it awarded.
     */
    fun awardAll(deeds: List<PendingDeed>, formation: Boolean = false): Set<String> {
        val state = uiState.value
        var prestige = state.resources.prestige
        val owned = state.citations.groupBy { it.operatorId }.mapValues { (_, c) -> c.map { it.medalId }.toMutableSet() }.toMutableMap()
        val picks = mutableListOf<Triple<PendingDeed, Operator, MedalDefinition>>()
        var ranOut = false
        for (deed in deeds) {
            val op = state.operators.find { it.id == deed.operatorId && it.unlocked } ?: continue
            val medal = GameCatalog.medals.find { it.id == deed.recommendedMedalId } ?: continue
            val held = owned.getOrPut(op.id) { mutableSetOf() }
            if (medal.id in held) continue // repeats are awarded one at a time
            if (!TacticalCalculator.evaluateAwardEligibility(medal, op, held, state.citations, heroismCited = true).eligible) continue
            if (prestige < medal.prestigeCost) { ranOut = true; break }
            prestige -= medal.prestigeCost
            held += medal.id
            picks += Triple(deed, op, medal)
        }
        if (picks.isEmpty()) {
            showBanner(if (ranOut) "Not enough prestige for the next award." else "No first-time awards to grant.")
            return emptySet()
        }
        val spent = picks.sumOf { it.third.prestigeCost }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateCommanderState { cur ->
                cur.copy(prestige = (cur.prestige - spent).coerceAtLeast(0.0), lastSimulatedAtMillis = now)
            }
            var cords = 0
            // Session 38: each award lifts morale; at a Parade Ground formation it lifts it a little more
            val boosted = mutableMapOf<String, Operator>()
            val perAward = MoraleRules.AWARD_PRESENTED + if (formation) MoraleRules.FORMATION_MORALE_PER_AWARD else 0
            for ((deed, op0, medal) in picks) {
                val op = MoraleRules.adjust(boosted[op0.id] ?: op0, perAward)
                boosted[op0.id] = op
                repository.addCitation(
                    AwardedMedal(
                        id = "cit_${UUID.randomUUID().toString().take(8)}",
                        operatorId = op.id,
                        medalId = medal.id,
                        citationNote = deed.deedSummary,
                        missionTitle = deed.missionTitle,
                        awardedAtMillis = now
                    )
                )
                if (afterAward(op, medal, owned[op.id].orEmpty(), now) != null) cords++
            }
            showBanner(
                "Awarded ${picks.size} award${if (picks.size == 1) "" else "s"} ($spent PRS)" +
                    (if (cords > 0) " plus $cords cord${if (cords == 1) "" else "s"}" else "") + "." +
                    if (ranOut) " Out of prestige; the rest stay in Pending Awards." else ""
            )
        }
        return picks.map { it.first.id }.toSet()
    }

    /** Award All Eligible on the Uniform tab: every first-time award this soldier qualifies for now. */
    fun awardAllEligible(operatorId: String) {
        val state = uiState.value
        val op = state.operators.find { it.id == operatorId && it.unlocked } ?: return
        val pending = state.pendingDeeds.filter { it.operatorId == op.id }
        val now = System.currentTimeMillis()
        val deeds = AwardRules.eligibleFirstTimeAwards(op, state.citations, pending.map { it.recommendedMedalId }.toSet())
            .map { medal ->
                pending.firstOrNull { it.recommendedMedalId == medal.id } ?: PendingDeed(
                    id = "deed_${UUID.randomUUID().toString().take(8)}",
                    operatorId = op.id,
                    missionTitle = "Uniform Tab Review",
                    deedSummary = "Met the standard for the ${medal.name}.",
                    recommendedMedalId = medal.id,
                    createdAtMillis = now
                )
            }
        awardAll(deeds)
    }

    /** Award all on the Honors screen: every soldier's pending first-time awards. */
    fun awardAllPending() {
        awardAll(uiState.value.pendingDeeds)
    }

    /** Award all on the award pop-up: the rest of the queue; repeat awards stay for one-by-one review. */
    fun awardAllFromQueue() {
        val awarded = awardAll(_awardQueue.value)
        if (awarded.isEmpty()) return
        val rest = _awardQueue.value.filter { it.id !in awarded }
        _awardQueue.value = rest
        if (rest.isEmpty()) _awardQueueTotal.value = 0
    }

    /** Award button on the award pop-up: confers the head of the queue and moves to the next card. */
    fun awardFromQueue() {
        val deed = _awardQueue.value.firstOrNull() ?: return
        val ok = awardMedal(deed.operatorId, deed.recommendedMedalId, deed.deedSummary, deed.missionTitle)
        // A one-time award the soldier already holds can never be granted; close the card instead of looping
        if (ok || isHeldOneTime(uiState.value, deed)) popAwardQueue()
    }

    private fun isHeldOneTime(state: CommandUiState, deed: PendingDeed): Boolean {
        val medal = GameCatalog.medals.find { it.id == deed.recommendedMedalId } ?: return true
        if (TacticalCalculator.isRepeatableAward(medal)) return TacticalCalculator.isAtAwardLimit(deed.operatorId, medal.id, state.citations)
        return state.citations.any { it.operatorId == deed.operatorId && it.medalId == medal.id }
    }

    private var enforcingBillets = false

    private fun enforceEnlistedBillets(state: CommandUiState) {
        if (state.isLoading || enforcingBillets) return
        val misplaced = state.operators.filter {
            it.status == OperatorStatus.HQ_STAFF && it.hqRole?.isEnlistedOnly == true && !it.rank.tier.isEnlisted
        }
        if (misplaced.isEmpty()) return
        enforcingBillets = true
        viewModelScope.launch {
            repository.saveOperators(misplaced.map {
                it.copy(status = OperatorStatus.READY, hqRole = null, uniformMode = UniformMode.FIELD_TACTICAL, activeWardrobeKit = WardrobeKit.FIELD)
            })
            showBanner("The S-3 is now an enlisted billet: ${misplaced.joinToString { "${it.rank.code} ${it.callsign}" }} returned to field duty.")
            enforcingBillets = false
        }
    }

    /** Item 99: remove queued cards for one-time awards now held (or repeat awards at their limit). */
    private fun pruneAwardQueue(state: CommandUiState) {
        val queue = _awardQueue.value
        if (queue.isEmpty()) return
        val kept = queue.filterNot { isHeldOneTime(state, it) }
        if (kept.size == queue.size) return
        _awardQueue.value = kept
        if (kept.isEmpty()) _awardQueueTotal.value = 0
    }

    /** Skip on the award pop-up: the award stays in Pending Awards on the Honors screen. */
    fun skipAwardPrompt() = popAwardQueue()

    private fun popAwardQueue() {
        val rest = _awardQueue.value.drop(1)
        _awardQueue.value = rest
        if (rest.isEmpty()) _awardQueueTotal.value = 0
    }

    private fun enqueueAwardPrompts(deeds: List<PendingDeed>) {
        if (deeds.isEmpty()) return
        _awardQueue.value = _awardQueue.value + deeds
        _awardQueueTotal.value = _awardQueueTotal.value + deeds.size
    }

    /** Medal ids this soldier holds that aren't also waiting in Pending Awards (used for repeat awards). */
    private fun repeatableOwnedIds(state: CommandUiState, operatorId: String): Set<String> {
        val pending = state.pendingDeeds.filter { it.operatorId == operatorId }.map { it.recommendedMedalId }.toSet()
        return state.citations.filter { it.operatorId == operatorId }.map { it.medalId }.toSet() - pending
    }

    /**
     * Awards that [after] qualifies for but [before] didn't, skipping one-time awards already held and
     * anything already pending. Used after promotions so a new rank's awards get offered right away.
     */
    private fun newlyEligibleAwardDeeds(
        before: Operator,
        after: Operator,
        state: CommandUiState,
        reasonTitle: String,
        now: Long
    ): List<PendingDeed> {
        val owned = state.citations.filter { it.operatorId == after.id }.map { it.medalId }.toSet()
        val pending = state.pendingDeeds.filter { it.operatorId == after.id }.map { it.recommendedMedalId }.toSet()
        return GameCatalog.medals.filter { medal ->
            medal.id !in pending &&
                (medal.id !in owned || TacticalCalculator.isRepeatableAward(medal)) &&
                TacticalCalculator.evaluateAwardEligibility(medal, after, owned, state.citations).eligible &&
                !TacticalCalculator.evaluateAwardEligibility(medal, before, owned, state.citations).eligible
        }.map { medal ->
            PendingDeed(
                id = "deed_${UUID.randomUUID().toString().take(8)}",
                operatorId = after.id,
                missionTitle = reasonTitle,
                deedSummary = "Met the standard for the ${medal.name} with ${reasonTitle.lowercase()}.",
                recommendedMedalId = medal.id,
                createdAtMillis = now
            )
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

    /** Closes the current rifle qualification pop-up; an unawarded badge stays pending on the soldier. */
    fun dismissRifleQualResult() {
        _rifleQualQueue.value = _rifleQualQueue.value.drop(1)
    }

    /** Awards a soldier's pending rifle qualification badge so it appears on their uniform. */
    fun awardMarksmanship(operatorId: String) {
        val op = uiState.value.operators.find { it.id == operatorId } ?: return
        val pending = op.pendingMarksmanshipQual ?: return
        viewModelScope.launch {
            repository.saveOperator(op.copy(marksmanshipQual = pending, pendingMarksmanshipQual = null))
            showBanner("Awarded ${pending.shortLabel} badge to ${op.rank.code} ${op.callsign}")
        }
        _rifleQualQueue.value = _rifleQualQueue.value.filterNot { it.operatorId == operatorId && it.improved }
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

    /** Saves a squad's roster, leaders and uniform standard. Standards apply at render time to whoever is in the squad. */
    fun saveSquadWithStandard(
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        teamLeaderId: String?,
        standard: SquadUniformStandard,
        specialForces: Boolean = false
    ) {
        val state = uiState.value
        // Items 58/119: SF squads up to the SF Compound's limit, SF-qualified members only
        val sfLimit = CompoundRules.sfSquadLimit(state.facilities)
        if (specialForces && state.squads.count { it.isSpecialForces && it.id != squadId } >= sfLimit) {
            showBanner(if (sfLimit == 1) "One SF squad until the SF Compound is built." else "The SF Compound houses $sfLimit SF squads at its level.")
            return
        }
        val validIds = memberIds
            .distinct()
            .filter { id ->
                state.operators.find { it.id == id }?.let {
                    SquadCohesionRules.isOperatorEligibleForFieldSquad(it) && (!specialForces || TacticalCalculator.isSfQualified(it))
                } == true
            }
            .take(MAX_SQUAD_SIZE)
        if (validIds.size < MIN_SQUAD_SIZE) {
            showBanner("A squad needs at least $MIN_SQUAD_SIZE field soldiers.")
            return
        }

        val resolvedLeaderId = leaderId?.takeIf { it in validIds }
            ?: SquadCohesionRules.pickDefaultLeaderId(validIds, state.operators)

        val existing = squadId?.let { id -> state.squads.find { it.id == id } }
        val teamLeaderCandidate = TacticalSquad("check", "", validIds, resolvedLeaderId, teamLeaderId = teamLeaderId)
        val resolvedTeamLeaderId = SquadCohesionRules.validTeamLeaderId(teamLeaderCandidate, state.operators)
        val resolvedName = name.trim().take(24).ifBlank {
            existing?.name ?: SquadCohesionRules.nextDefaultSquadName(state.squads.filter { it.id != squadId }, specialForces)
        }
        val now = System.currentTimeMillis()
        // Squads always carry a division patch; a missing or non-division pick gets the first free one.
        // Item 120: SF squads carry their team's arrowhead instead.
        val resolvedPatch = if (specialForces) {
            UnitPatchStyle.sfTeamPatchFor(
                (existing ?: TacticalSquad("sq_new", resolvedName, validIds, null)).copy(name = resolvedName, isSpecialForces = true),
                state.squads
            )
        } else standard.patch.takeIf { it.isDivision }
            ?: existing?.standardPatch?.takeIf { it.isDivision }
            ?: UnitPatchStyle.firstUnusedDivision(state.squads, excludingSquadId = existing?.id)
        val targetSquad = if (existing != null) {
            // Turnover costs cohesion in proportion to the original members who leave
            SquadCohesionRules.applyRosterChange(existing, validIds, resolvedLeaderId).copy(
                name = resolvedName,
                teamLeaderId = resolvedTeamLeaderId,
                standardPatch = resolvedPatch,
                standardCamo = standard.camo,
                standardHeadgear = standard.alphasHeadgear,
                standardDressStyle = standard.alphasDressStyle,
                standardBravosHeadgear = standard.bravosHeadgear,
                standardBravosDressStyle = standard.bravosDressStyle,
                standardMessDressStyle = standard.messDressStyle,
                standardMessHeadgear = standard.messHeadgear,
                standardAlphasGloves = standard.alphasGloves,
                standardMessGloves = standard.messGloves,
                isSpecialForces = specialForces
            )
        } else {
            TacticalSquad(
                id = "sq_${UUID.randomUUID().toString().take(8)}",
                name = resolvedName,
                memberIds = validIds,
                leaderId = resolvedLeaderId,
                teamLeaderId = resolvedTeamLeaderId,
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
                standardMessGloves = standard.messGloves,
                isSpecialForces = specialForces
            )
        }

        // Remove assigned members from any other squad so each soldier belongs to at most 1 squad
        val otherUpdatedSquads = state.squads
            .filter { it.id != targetSquad.id && it.memberIds.any { m -> m in validIds } }
            .map { other ->
                val remaining = other.memberIds.filter { it !in validIds }
                val nextLeader = other.leaderId?.takeIf { it in remaining }
                    ?: SquadCohesionRules.pickDefaultLeaderId(remaining, state.operators)
                val trimmed = SquadCohesionRules.applyRosterChange(other, remaining, nextLeader)
                trimmed.copy(teamLeaderId = SquadCohesionRules.validTeamLeaderId(trimmed, state.operators))
            }

        // Item 127: saving a standard clears members' own picks for the items it sets, so they wear it
        val followers = state.operators.filter { it.id in validIds }.mapNotNull { op ->
            // Session 30: an SF-qualified SPC or below pins SGT when he joins an ODA
            val promoted = if (targetSquad.isSpecialForces && TacticalCalculator.isSfQualified(op) && op.rank.tier == RankTier.SOLDIER) {
                applyTierProgressionOnRankUp(op.promotedTo(OperatorRank.SGT).copy(authorizedTier = RankTier.NCO), op.rank, OperatorRank.SGT)
            } else op
            TacticalCalculator.followSquadStandard(promoted, targetSquad).takeIf { it != op }
        }
        viewModelScope.launch {
            repository.saveSquads(otherUpdatedSquads + targetSquad)
            if (followers.isNotEmpty()) repository.saveOperators(followers)
            val leaderCallsign = resolvedLeaderId?.let { id -> state.operators.find { it.id == id }?.callsign }
            val leaderSuffix = if (leaderCallsign != null) " • Leader: $leaderCallsign" else ""
            showBanner("Saved ${targetSquad.name} (${validIds.size}/$MAX_SQUAD_SIZE$leaderSuffix)")
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
        // Session 38: a good grade lifts morale, once per soldier per cooldown
        val moraleGain = MoraleRules.inspectionMorale(report.gradeLetter)
        val boosted = if (moraleGain > 0) subjects.filter {
            now - it.inspectionMoraleAtMillis >= GameTiming.millis(MoraleRules.INSPECTION_COOLDOWN_MILLIS)
        }.map { MoraleRules.adjust(it, moraleGain).copy(inspectionMoraleAtMillis = now) } else emptyList()
        val finalReport = report.copy(moraleGain = if (boosted.isEmpty()) 0 else moraleGain, moraleSoldiers = boosted.size)
        viewModelScope.launch {
            repository.updateCommanderState { cur ->
                cur.copy(
                    prestige = cur.prestige + report.prestigeEarned,
                    lastInspectionAtMillis = now,
                    inspectionBuffUntilMillis = buffUntil,
                    lastSimulatedAtMillis = now
                )
            }
            if (boosted.isNotEmpty()) repository.saveOperators(boosted)
            _lastInspectionReport.value = finalReport
            showBanner("Inspection ${report.gradeLetter}: +${report.prestigeEarned} PRS • +8% Eff / +10% PRS for 3m." +
                if (boosted.isNotEmpty()) " Morale +$moraleGain for ${boosted.size}." else "")
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
        // Soldiers keep the unit citations they earned with this squad after it's gone
        val keepers = state.operators.mapNotNull { op ->
            val earned = squad.unitCitations.filter { op.id in it.memberIds }.map { it.type }
            if (earned.isNotEmpty()) op.copy(retainedUnitAwards = op.retainedUnitAwards + earned) else null
        }
        viewModelScope.launch {
            if (keepers.isNotEmpty()) repository.saveOperators(keepers)
            repository.deleteSquad(squadId)
            showBanner("Disbanded ${squad.name}." + if (keepers.isNotEmpty()) " Its unit citation stays with the ${keepers.size} soldiers who earned it." else "")
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
        /** Testing build only: resource-bar tap grants. */
        const val CHEAT_CREDITS = 1000.0
        const val CHEAT_SUPPLIES = 500.0
        const val CHEAT_INTEL = 250.0

        fun factory(repository: AegisRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CommandViewModel(repository) as T
                }
            }
    }
}
