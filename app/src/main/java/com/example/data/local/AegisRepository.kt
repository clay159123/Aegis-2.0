package com.example.data.local

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AegisRepository(private val dao: AegisDao) {

    companion object {
        private const val TAG = "AegisRepository"

        /**
         * Bump this whenever game rules or saved data shapes change, even if no Room table
         * changed (new award ids, gear ownership rules, renamed missions). Saves with a
         * different value are replaced with a new game on launch.
         */
        const val SAVE_FORMAT_VERSION = 31
    }

    private val commanderMutex = Mutex()

    val commanderState: Flow<CommanderStateEntity?> = dao.observeCommanderState()

    val facilities: Flow<List<FacilityState>> = dao.observeFacilities().map { list ->
        list.mapNotNull { entity ->
            FacilityType.entries.find { it.id == entity.typeId }?.let { type ->
                FacilityState(
                    type = type,
                    level = entity.level,
                    upgradeEndsAtMillis = entity.upgradeEndsAtMillis,
                    upgradeStartedAtMillis = entity.upgradeStartedAtMillis
                )
            }
        }.sortedBy { FacilityType.entries.indexOf(it.type) }
    }

    val operators: Flow<List<Operator>> = dao.observeOperators().map { list ->
        list.mapSafely("operator") { it.toDomain() }
            .sortedWith(
                compareBy<Operator> { it.status.isOutOfActiveService }
                    .thenByDescending { it.unlocked }
                    .thenByDescending { it.xp }
            )
    }

    val gearItems: Flow<List<GearItem>> = dao.observeGear().map { list ->
        list.mapSafely("gear") { it.toDomain() }
            .sortedWith(compareBy<GearItem> { it.slot.ordinal }.thenBy { it.baseCreditCost })
    }

    val activeDeployments: Flow<List<ActiveDeployment>> = dao.observeDeployments().map { list ->
        list.mapSafely("deployment") { it.toDomain() }
    }

    val citations: Flow<List<AwardedMedal>> = dao.observeCitations().map { list ->
        list.mapSafely("citation") { it.toDomain() }
    }

    val pendingDeeds: Flow<List<PendingDeed>> = dao.observePendingDeeds().map { list ->
        list.mapSafely("pending deed") { it.toDomain() }
    }

    val squads: Flow<List<TacticalSquad>> = dao.observeSquads().map { list ->
        list.mapSafely("squad") { it.toDomain() }
    }

    /** Result of the startup load: the commander state and whether the old save was replaced. */
    data class LoadResult(val state: CommanderStateEntity, val wasReset: Boolean)

    /**
     * Startup entry point. Loads the save if it matches the current game, otherwise
     * wipes it and starts a new command. A save is replaced when Room had to rebuild the
     * schema, when its saveFormatVersion differs from [SAVE_FORMAT_VERSION], or when any
     * saved record fails to map to the current models.
     */
    suspend fun loadOrResetSave(nowMillis: Long = System.currentTimeMillis()): LoadResult {
        val existing = try {
            dao.getCommanderStateOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Commander state failed to load; starting a new save", e)
            return LoadResult(wipeAndSeed(nowMillis), wasReset = true)
        }
        val roomRebuiltSchema = AegisDatabase.consumeDestructiveResetFlag()
        if (existing == null) {
            return LoadResult(ensureSeeded(nowMillis), wasReset = roomRebuiltSchema)
        }
        if (existing.saveFormatVersion != SAVE_FORMAT_VERSION) {
            Log.i(TAG, "Save format ${existing.saveFormatVersion} != $SAVE_FORMAT_VERSION; starting a new save")
            return LoadResult(wipeAndSeed(nowMillis), wasReset = true)
        }
        if (!savedDataLoadsCleanly()) {
            return LoadResult(wipeAndSeed(nowMillis), wasReset = true)
        }
        return LoadResult(ensureSeeded(nowMillis), wasReset = false)
    }

    /**
     * Strict check used once at startup: every saved record must map to the current models.
     * The public flows use [mapSafely] so a bad record can never crash the UI, but at startup
     * we want to know about it and replace the save instead.
     */
    private suspend fun savedDataLoadsCleanly(): Boolean = try {
        dao.observeOperators().first().forEach { it.toDomain() }
        dao.observeGear().first().forEach { it.toDomain() }
        dao.observeDeployments().first().forEach { it.toDomain() }
        dao.observeCitations().first().forEach { it.toDomain() }
        dao.observePendingDeeds().first().forEach { it.toDomain() }
        dao.observeSquads().first().forEach { it.toDomain() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Saved data failed to map to current models; starting a new save", e)
        false
    }

    private suspend fun wipeAndSeed(nowMillis: Long): CommanderStateEntity =
        commanderMutex.withLock {
            dao.clearAllData()
            ensureSeeded(nowMillis)
        }

    suspend fun ensureSeeded(nowMillis: Long = System.currentTimeMillis()): CommanderStateEntity {
        val existing = dao.getCommanderStateOnce()
        if (existing != null) {
            dao.insertMissingGear(GameCatalog.initialGear.map { it.toEntity() })
            return existing
        }

        val initialRotatingIds = TacticalCalculator.pickNextRotatingMissions().joinToString(",")
        val initialState = CommanderStateEntity(
            id = 1,
            credits = 320.0,
            supplies = 160.0,
            intel = 55.0,
            prestige = 34.0,
            totalMissionsCompleted = 10,
            lastSimulatedAtMillis = nowMillis,
            veteranLegacyBps = 0,
            retiredVeteransCount = 0,
            lastRetirementAtMillis = 0L,
            rotatingMissionIdsCsv = initialRotatingIds,
            rotationUpdatedAtMillis = nowMillis,
            saveFormatVersion = SAVE_FORMAT_VERSION
        )
        dao.upsertCommanderState(initialState)

        dao.upsertFacilities(
            FacilityType.entries.map { FacilityEntity(typeId = it.id, level = it.startLevel) }
        )

        dao.upsertOperators(GameCatalog.initialOperators.map { it.toEntity() })
        dao.upsertGearList(GameCatalog.initialGear.map { it.toEntity() })

        // Seed starter Army awards across Officer (Kestrel), NCO (Vesper), and Soldier (Cipher)
        val starterCitations = listOf(
            // Kestrel (CPT Field Officer): Army Service Ribbon, Bronze Valor Star, Ranger Tab, Infantry Blue Cord
            CitationEntity(
                id = "cit_seed_kestrel_service",
                operatorId = "op_kestrel",
                medalId = "medal_basic_training",
                citationNote = "Completed Basic Officer Leadership & Range Qualification.",
                missionTitle = "Drill: Basic Combat & Infantry Qual",
                awardedAtMillis = nowMillis - 300_000_000L
            ),
            CitationEntity(
                id = "cit_seed_kestrel_valor",
                operatorId = "op_kestrel",
                medalId = "medal_bronze_valor",
                citationNote = "Led Veridian Ridge stabilization under zero-visibility fog.",
                missionTitle = "Op: Whispering Pines",
                awardedAtMillis = nowMillis - 180_000_000L
            ),
            CitationEntity(
                id = "cit_seed_kestrel_ranger",
                operatorId = "op_kestrel",
                medalId = "insig_ranger_tab",
                citationNote = "Graduated Army Ranger School with honor patrol score.",
                missionTitle = "HQ School Qualification",
                awardedAtMillis = nowMillis - 120_000_000L
            ),
            CitationEntity(
                id = "cit_seed_kestrel_infantry",
                operatorId = "op_kestrel",
                medalId = "cord_infantry_blue",
                citationNote = "Graduated Marksmanship & Infantry Qualification prior to leading field patrols.",
                missionTitle = "School: Marksmanship & Tactical Basics",
                awardedAtMillis = nowMillis - 90_000_000L
            ),
            // Vesper (SGT NCO): Night Ops Ribbon, NCO Dev Ribbon, Jump Wings, Airborne Crimson Cord
            CitationEntity(
                id = "cit_seed_vesper_night",
                operatorId = "op_vesper",
                medalId = "medal_night_owl",
                citationNote = "Zero-light NVG pathfinder run with zero detections.",
                missionTitle = "Drill: Airborne & Night NVG School",
                awardedAtMillis = nowMillis - 210_000_000L
            ),
            CitationEntity(
                id = "cit_seed_vesper_nco",
                operatorId = "op_vesper",
                medalId = "medal_nco_dev",
                citationNote = "Graduated Army Basic Leader Course upon Sergeant promotion.",
                missionTitle = "NCO Academy",
                awardedAtMillis = nowMillis - 150_000_000L
            ),
            CitationEntity(
                id = "cit_seed_vesper_jump",
                operatorId = "op_vesper",
                medalId = "insig_parachutist",
                citationNote = "Completed five static-line nocturnal jumps onto Drop Zone Pine.",
                missionTitle = "Airborne School",
                awardedAtMillis = nowMillis - 110_000_000L
            ),
            CitationEntity(
                id = "cit_seed_vesper_cord",
                operatorId = "op_vesper",
                medalId = "cord_airborne_crimson",
                citationNote = "Assigned to 1st Rapid Airborne Recon detachment.",
                missionTitle = "Airborne Assignment",
                awardedAtMillis = nowMillis - 95_000_000L
            ),
            CitationEntity(
                id = "cit_seed_vesper_abn_tab",
                operatorId = "op_vesper",
                medalId = "insig_airborne_tab",
                citationNote = "Earned Airborne School Tab alongside Maroon Beret and Jump Wings.",
                missionTitle = "School: Army Airborne & Parachutist School",
                awardedAtMillis = nowMillis - 90_000_000L
            ),
            // Cipher (PV2 Post-Basic Soldier): Army Service Ribbon ONLY (no special schools or cords yet)
            CitationEntity(
                id = "cit_seed_cipher_basic",
                operatorId = "op_cipher",
                medalId = "medal_basic_training",
                citationNote = "Completed Basic Combat Training — arrived at HQ post-Basic with no specialty schools.",
                missionTitle = "Basic Combat Training Graduation",
                awardedAtMillis = nowMillis - 170_000_000L
            )
        )
        starterCitations.forEach { dao.insertCitation(it) }
        // Item 43: starters also hold everything else their record already earns
        GameCatalog.initialOperators.filter { it.unlocked }.forEach { op ->
            val held = starterCitations.filter { it.operatorId == op.id }.map { it.medalId }.toSet()
            TacticalCalculator.earnedStartingAwards(op, held).forEachIndexed { i, medalId ->
                dao.insertCitation(
                    CitationEntity(
                        id = "cit_seed_${op.id}_$medalId",
                        operatorId = op.id,
                        medalId = medalId,
                        citationNote = "Earned before reporting to Aegis HQ.",
                        missionTitle = "Prior Service Record",
                        awardedAtMillis = nowMillis - 80_000_000L + i
                    )
                )
            }
        }

        dao.upsertSquad(
            SquadEntity(
                id = "sq_alpha",
                name = "Alpha Squad",
                memberIdsCsv = "op_kestrel,op_vesper,op_cipher,op_warden",
                leaderId = "op_kestrel",
                cohesionXp = 120,
                missionsTogether = 2,
                createdAtMillis = nowMillis
            )
        )
        return initialState
    }

    suspend fun saveCommanderState(state: CommanderStateEntity) {
        commanderMutex.withLock {
            dao.upsertCommanderState(state)
        }
    }

    suspend fun updateCommanderState(
        transform: (CommanderStateEntity) -> CommanderStateEntity
    ): CommanderStateEntity = commanderMutex.withLock {
        val current = dao.getCommanderStateOnce() ?: ensureSeeded()
        val updated = transform(current)
        dao.upsertCommanderState(updated)
        updated
    }

    suspend fun saveFacility(facility: FacilityState) {
        dao.upsertFacility(
            FacilityEntity(
                typeId = facility.type.id,
                level = facility.level,
                upgradeEndsAtMillis = facility.upgradeEndsAtMillis,
                upgradeStartedAtMillis = facility.upgradeStartedAtMillis
            )
        )
    }

    suspend fun saveOperator(operator: Operator) {
        dao.upsertOperator(operator.toEntity())
    }

    suspend fun saveOperators(operators: List<Operator>) {
        dao.upsertOperators(operators.map { it.toEntity() })
    }

    suspend fun saveGear(gear: GearItem) {
        dao.upsertGear(gear.toEntity())
    }

    suspend fun saveGearList(items: List<GearItem>) {
        dao.upsertGearList(items.map { it.toEntity() })
    }

    suspend fun saveDeployment(deployment: ActiveDeployment) {
        dao.upsertDeployment(deployment.toEntity())
    }

    suspend fun removeDeployment(deploymentId: String) {
        dao.deleteDeploymentById(deploymentId)
    }

    suspend fun addCitation(awardedMedal: AwardedMedal) {
        dao.insertCitation(awardedMedal.toEntity())
        dao.deletePendingDeedsForAward(awardedMedal.operatorId, awardedMedal.medalId)
    }

    /** Removes a held award, used when higher wings replace lower ones. */
    suspend fun removeCitation(operatorId: String, medalId: String) {
        dao.deleteCitationForAward(operatorId, medalId)
    }

    suspend fun addPendingDeeds(deeds: List<PendingDeed>) {
        dao.insertPendingDeeds(deeds.map { it.toEntity() })
    }

    suspend fun dismissPendingDeed(deedId: String) {
        dao.deletePendingDeedById(deedId)
    }

    suspend fun clearPendingDeedsForOperator(operatorId: String) {
        dao.deletePendingDeedsForOperator(operatorId)
    }

    suspend fun saveSquad(squad: TacticalSquad) {
        dao.upsertSquad(squad.toEntity())
    }

    suspend fun saveSquads(squads: List<TacticalSquad>) {
        dao.upsertSquads(squads.map { it.toEntity() })
    }

    suspend fun deleteSquad(squadId: String) {
        dao.deleteSquadById(squadId)
    }

    suspend fun deleteOperatorAndRecords(operatorId: String) {
        dao.deletePendingDeedsForOperator(operatorId)
        dao.deleteCitationsForOperator(operatorId)
        dao.deleteOperatorById(operatorId)
    }

    suspend fun resetCampaign(nowMillis: Long = System.currentTimeMillis()): CommanderStateEntity =
        commanderMutex.withLock {
            dao.clearAllData()
            ensureSeeded(nowMillis)
        }
}

/** Maps each saved record, skipping (and logging) any record that no longer fits the current models. */
private inline fun <E, D> List<E>.mapSafely(label: String, transform: (E) -> D): List<D> =
    mapNotNull { entity ->
        try {
            transform(entity)
        } catch (e: Exception) {
            Log.w("AegisRepository", "Skipping unreadable saved $label", e)
            null
        }
    }

private fun OperatorEntity.toDomain(): Operator = Operator(
    id = id,
    name = name,
    callsign = callsign,
    branch = ServiceBranch.entries.find { it.name == branchName } ?: ServiceBranch.ARMY,
    xp = xp,
    specialty = OperatorSpecialty.entries.find { it.name == specialtyName } ?: OperatorSpecialty.ASSAULT,
    status = OperatorStatus.entries.find { it.name == statusName } ?: OperatorStatus.READY,
    recoveryUntilMillis = recoveryUntilMillis,
    transferRequested = transferRequested,
    retirementRequested = retirementRequested,
    petitionCooldownUntilOps = petitionCooldownUntilOps,
    staffOpsSupported = staffOpsSupported,
    hqRole = hqRoleName?.let { roleStr -> HqStaffRole.entries.find { it.name == roleStr } },
    baseFirepower = baseFirepower,
    baseStealth = baseStealth,
    baseTech = baseTech,
    baseResilience = baseResilience,
    uniformMode = UniformMode.entries.find { it.name == uniformModeName } ?: UniformMode.DRESS_UNIFORM,
    dressDisplayMode = DressDisplayMode.entries.find { it.name == dressDisplayModeName } ?: DressDisplayMode.RIBBONS,
    camoPattern = CamoPattern.entries.find { it.name == camoPatternName } ?: CamoPattern.WOODLAND_OLIVE,
    dressStyle = DressUniformStyle.entries.find { it.name == dressStyleName } ?: DressUniformStyle.ARMY_SERVICE_GREEN,
    headgearStyle = HeadgearStyle.entries.find { it.name == headgearStyleName } ?: HeadgearStyle.OBSIDIAN_BERET,
    equippedLeftCordId = equippedLeftCordId,
    equippedRightCordId = equippedRightCordId,
    hasWhiteGloves = hasWhiteGloves,
    hasDressSash = hasDressSash,
    tailorOverrides = decodeTailorOverridesCsv(tailorOverridesCsv),
    activeWardrobeKit = WardrobeKit.entries.find { it.name == activeWardrobeKitName }
        ?.takeIf { !(it == WardrobeKit.FIELD && statusName == OperatorStatus.HQ_STAFF.name) }
        ?: if (uniformModeName == UniformMode.FIELD_TACTICAL.name && statusName != OperatorStatus.HQ_STAFF.name) {
            WardrobeKit.FIELD
        } else {
            WardrobeKit.ALPHAS
        },
    wardrobe = decodeOperatorWardrobeCsv(wardrobeCsv),
    missionsCompleted = missionsCompleted,
    combatMissionsCompleted = combatMissionsCompleted,
    completedMissionIds = completedMissionIdsCsv
        .split(",")
        .filter { it.isNotBlank() }
        .map { id ->
            if (id == "msn_support_school") {
                when (specialtyName) {
                    OperatorSpecialty.BREACHER.name -> "msn_engineer_school"
                    OperatorSpecialty.MEDIC.name -> "msn_medical_school"
                    else -> "msn_logistics_school"
                }
            } else {
                id
            }
        }
        .toSet(),
    schoolAttempts = decodeAttemptsCsv(schoolAttemptsCsv),
    highestRiskSurvived = highestRiskName?.let { name -> MissionRisk.entries.find { it.name == name } },
    timesWounded = timesWounded,
    combatPatchHistory = decodeCombatPatchHistoryCsv(combatPatchHistoryCsv),
    equippedGearIds = decodeEquippedGearCsv(equippedGearCsv),
    skinToneOverride = skinToneName?.let { n -> SkinTone.entries.find { it.name == n } },
    marksmanshipQual = marksmanshipQualName?.let { n -> MarksmanshipBadgeTier.entries.find { it.name == n } },
    pendingMarksmanshipQual = pendingMarksmanshipQualName?.let { n -> MarksmanshipBadgeTier.entries.find { it.name == n } },
    lastQualScore = lastQualScore,
    warrantGrade = warrantGrade,
    flagGrade = flagGrade,
    flightSkill = flightSkill,
    combatSortiesFlown = combatSortiesFlown,
    combatJumps = combatJumps,
    expPointsFirepower = expPointsFirepower,
    expPointsStealth = expPointsStealth,
    expPointsTech = expPointsTech,
    expPointsResilience = expPointsResilience,
    retainedUnitAwards = retainedUnitAwardsCsv.split(",").mapNotNull { UnitAwardType.fromName(it) },
    flightHoursLogged = flightHoursLogged,
    goodConductStreak = goodConductStreak,
    serviceActions = serviceActions,
    lastArcomAtAction = lastArcomAtAction,
    lastAamAtAction = lastAamAtAction,
    bodyBuildOverride = bodyBuildName?.let { n -> BodyBuild.entries.find { it.name == n } },
    eventBuffs = SoldierEventBuffs.fromCsv(eventBuffsCsv),
    capturedAtMillis = capturedAtMillis,
    captivityResolveAtMillis = captivityResolveAtMillis,
    captureTheater = captureTheaterName?.let { n -> TheaterRegion.entries.find { it.name == n } },
    timesCaptured = timesCaptured,
    powTalksFailures = powTalksFailures,
    powTalksCooldownUntilMillis = powTalksCooldownUntilMillis,
    powRescueClosed = powRescueClosed,
    languageRegions = languageRegionsCsv.split(",").mapNotNull { n -> TheaterRegion.entries.find { it.name == n } }.toSet(),
    unlocked = unlocked,
    recruitCreditCost = recruitCreditCost,
    recruitIntelCost = recruitIntelCost,
    bio = bio,
    authorizedTier = run {
        val parsed = RankTier.entries.find { it.name == authorizedTierName } ?: OperatorRank.forXp(xp).tier
        if (parsed == RankTier.NCO && xp > OperatorRank.SFC.xpRequired) RankTier.SNCO else parsed
    }
)

/** Loadout stored as "SLOT=gearId;SLOT=gearId" */
private fun encodeEquippedGearCsv(ids: Map<GearSlot, String>): String =
    ids.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}=${it.value}" }

private fun decodeEquippedGearCsv(csv: String): Map<GearSlot, String> =
    csv.split(";").mapNotNull { part ->
        val (slotName, gearId) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val slot = GearSlot.entries.find { it.name == slotName } ?: return@mapNotNull null
        gearId.takeIf { it.isNotBlank() }?.let { slot to it }
    }.toMap()

private fun Operator.toEntity(): OperatorEntity = OperatorEntity(
    id = id,
    name = name,
    callsign = callsign,
    branchName = branch.name,
    xp = xp,
    authorizedTierName = authorizedTier.name,
    specialtyName = specialty.name,
    statusName = status.name,
    recoveryUntilMillis = recoveryUntilMillis,
    transferRequested = transferRequested,
    retirementRequested = retirementRequested,
    petitionCooldownUntilOps = petitionCooldownUntilOps,
    staffOpsSupported = staffOpsSupported,
    hqRoleName = hqRole?.name,
    baseFirepower = baseFirepower,
    baseStealth = baseStealth,
    baseTech = baseTech,
    baseResilience = baseResilience,
    uniformModeName = uniformMode.name,
    dressDisplayModeName = dressDisplayMode.name,
    camoPatternName = camoPattern.name,
    dressStyleName = dressStyle.name,
    headgearStyleName = headgearStyle.name,
    equippedLeftCordId = equippedLeftCordId,
    equippedRightCordId = equippedRightCordId,
    hasWhiteGloves = hasWhiteGloves,
    hasDressSash = hasDressSash,
    tailorOverridesCsv = encodeTailorOverridesCsv(tailorOverrides),
    activeWardrobeKitName = activeWardrobeKit.name,
    wardrobeCsv = encodeOperatorWardrobeCsv(wardrobe),
    missionsCompleted = missionsCompleted,
    combatMissionsCompleted = combatMissionsCompleted,
    completedMissionIdsCsv = completedMissionIds.joinToString(","),
    schoolAttemptsCsv = encodeAttemptsCsv(schoolAttempts),
    highestRiskName = highestRiskSurvived?.name,
    timesWounded = timesWounded,
    combatPatchHistoryCsv = encodeCombatPatchHistoryCsv(combatPatchHistory),
    equippedGearCsv = encodeEquippedGearCsv(equippedGearIds),
    skinToneName = skinToneOverride?.name,
    marksmanshipQualName = marksmanshipQual?.name,
    pendingMarksmanshipQualName = pendingMarksmanshipQual?.name,
    lastQualScore = lastQualScore,
    warrantGrade = warrantGrade,
    flagGrade = flagGrade,
    flightSkill = flightSkill,
    combatSortiesFlown = combatSortiesFlown,
    combatJumps = combatJumps,
    expPointsFirepower = expPointsFirepower,
    expPointsStealth = expPointsStealth,
    expPointsTech = expPointsTech,
    expPointsResilience = expPointsResilience,
    retainedUnitAwardsCsv = retainedUnitAwards.joinToString(",") { it.name },
    flightHoursLogged = flightHoursLogged,
    goodConductStreak = goodConductStreak,
    serviceActions = serviceActions,
    lastArcomAtAction = lastArcomAtAction,
    lastAamAtAction = lastAamAtAction,
    bodyBuildName = bodyBuildOverride?.name,
    eventBuffsCsv = eventBuffs.toCsv(),
    capturedAtMillis = capturedAtMillis,
    captivityResolveAtMillis = captivityResolveAtMillis,
    captureTheaterName = captureTheater?.name,
    timesCaptured = timesCaptured,
    powTalksFailures = powTalksFailures,
    powTalksCooldownUntilMillis = powTalksCooldownUntilMillis,
    powRescueClosed = powRescueClosed,
    languageRegionsCsv = languageRegions.joinToString(",") { it.name },
    unlocked = unlocked,
    recruitCreditCost = recruitCreditCost,
    recruitIntelCost = recruitIntelCost,
    bio = bio
)

private fun encodeTailorOverridesCsv(o: UniformTailorOverrides): String {
    if (o == UniformTailorOverrides()) return ""
    val leaderPinToken = when (o.leaderRoleOverride) {
        LeaderRole.SQUAD_LEADER -> "1"
        LeaderRole.TEAM_LEADER -> "T"
        LeaderRole.NONE -> "0"
        null -> "A"
    }
    return listOf(
        "patch:${o.unitPatch.name}",
        "cpatch:${o.combatPatch.name}",
        "mark:${o.marksmanshipBadge.name}",
        "neck:${o.neckSlot}",
        "tab1:${o.tabSlot1}",
        "tab2:${o.tabSlot2}",
        "tab3:${o.tabSlot3}",
        "pin1:${o.pinSlot1}",
        "pin2:${o.pinSlot2}",
        "pin3:${o.pinSlot3}",
        "med1:${o.medalSlot1}",
        "med2:${o.medalSlot2}",
        "med3:${o.medalSlot3}",
        "med4:${o.medalSlot4}",
        "cit:${if (o.showUnitCitation) "1" else "0"}",
        "str:${if (o.showServiceStripes) "1" else "0"}",
        "obars:${if (o.showOverseasBars) "1" else "0"}",
        "lpin:$leaderPinToken",
        "free:${if (o.freeTailorOverride) "1" else "0"}"
    ).joinToString(",")
}

private fun decodeTailorOverridesCsv(csv: String): UniformTailorOverrides {
    if (csv.isBlank()) return UniformTailorOverrides()
    val map = csv.split(",")
        .mapNotNull { part ->
            val idx = part.indexOf(':')
            if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }
        .toMap()
    return UniformTailorOverrides(
        unitPatch = map["patch"]?.let { name -> UnitPatchStyle.entries.find { it.name == name } }
            ?: UnitPatchStyle.AUTO_MOS,
        combatPatch = map["cpatch"]?.let { name -> UnitPatchStyle.entries.find { it.name == name } }
            ?: UnitPatchStyle.AUTO_MOS,
        marksmanshipBadge = map["mark"]?.let { name -> MarksmanshipBadgeTier.entries.find { it.name == name } }
            ?: MarksmanshipBadgeTier.AUTO,
        neckSlot = map["neck"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        tabSlot1 = map["tab1"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        tabSlot2 = map["tab2"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        tabSlot3 = map["tab3"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        pinSlot1 = map["pin1"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        pinSlot2 = map["pin2"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        pinSlot3 = map["pin3"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        medalSlot1 = map["med1"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        medalSlot2 = map["med2"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        medalSlot3 = map["med3"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        medalSlot4 = map["med4"]?.takeIf { it.isNotBlank() } ?: UniformTailorOverrides.SLOT_AUTO,
        showUnitCitation = map["cit"] != "0",
        showServiceStripes = map["str"] != "0",
        showOverseasBars = map["obars"] != "0",
        leaderRoleOverride = when (map["lpin"]) {
            "1" -> LeaderRole.SQUAD_LEADER
            "T" -> LeaderRole.TEAM_LEADER
            "0" -> LeaderRole.NONE
            else -> null
        },
        freeTailorOverride = map["free"] == "1"
    )
}

private fun encodeWardrobeKitSettings(s: WardrobeKitSettings): String {
    if (s == WardrobeKitSettings()) return ""
    return buildList {
        s.dressStyle?.let { add("ds=${it.name}") }
        s.headgearStyle?.let { add("hg=${it.name}") }
        s.camoPattern?.let { add("cm=${it.name}") }
        s.dressDisplayMode?.let { add("dm=${it.name}") }
        s.hasWhiteGloves?.let { add("gl=${if (it) "1" else "0"}") }
        s.hasDressSash?.let { add("sa=${if (it) "1" else "0"}") }
        if (s.cordCustomized) {
            add("cc=1")
            s.equippedLeftCordId?.let { add("lc=$it") }
            s.equippedRightCordId?.let { add("rc=$it") }
        }
    }.joinToString(";")
}

private fun decodeWardrobeKitSettings(raw: String): WardrobeKitSettings {
    if (raw.isBlank()) return WardrobeKitSettings()
    val map = raw.split(";")
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }
        .toMap()
    return WardrobeKitSettings(
        dressStyle = map["ds"]?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        headgearStyle = map["hg"]?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        camoPattern = map["cm"]?.let { n -> CamoPattern.entries.find { it.name == n } },
        dressDisplayMode = map["dm"]?.let { n -> DressDisplayMode.entries.find { it.name == n } },
        hasWhiteGloves = map["gl"]?.let { it == "1" },
        hasDressSash = map["sa"]?.let { it == "1" },
        equippedLeftCordId = map["lc"]?.takeIf { it.isNotBlank() },
        equippedRightCordId = map["rc"]?.takeIf { it.isNotBlank() },
        cordCustomized = map["cc"] == "1"
    )
}

private fun encodeOperatorWardrobeCsv(w: OperatorWardrobe): String {
    if (w == OperatorWardrobe()) return ""
    return buildList {
        encodeWardrobeKitSettings(w.field).takeIf { it.isNotEmpty() }?.let { add("F:$it") }
        encodeWardrobeKitSettings(w.bravos).takeIf { it.isNotEmpty() }?.let { add("B:$it") }
        encodeWardrobeKitSettings(w.alphas).takeIf { it.isNotEmpty() }?.let { add("A:$it") }
        encodeWardrobeKitSettings(w.mess).takeIf { it.isNotEmpty() }?.let { add("M:$it") }
    }.joinToString("|")
}

private fun decodeOperatorWardrobeCsv(csv: String): OperatorWardrobe {
    if (csv.isBlank()) return OperatorWardrobe()
    var result = OperatorWardrobe()
    csv.split("|").forEach { chunk ->
        val idx = chunk.indexOf(':')
        if (idx <= 0) return@forEach
        val key = chunk.substring(0, idx).trim()
        val body = chunk.substring(idx + 1)
        val parsed = decodeWardrobeKitSettings(body)
        result = when (key) {
            "F" -> result.copy(field = parsed)
            "B" -> result.copy(bravos = parsed)
            "A" -> result.copy(alphas = parsed)
            "M" -> result.copy(mess = parsed)
            else -> result
        }
    }
    return result
}

/** "campaignId|PATCH|millis" records separated by ';' */
private fun encodeCombatPatchHistoryCsv(records: List<CombatPatchRecord>): String =
    records.joinToString(";") { "${it.campaignId}|${it.patch.name}|${it.recordedAtMillis}" }

private fun decodeCombatPatchHistoryCsv(csv: String): List<CombatPatchRecord> =
    csv.split(";").filter { it.isNotBlank() }.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size != 3) return@mapNotNull null
        val patch = UnitPatchStyle.entries.find { it.name == parts[1] } ?: return@mapNotNull null
        val at = parts[2].toLongOrNull() ?: return@mapNotNull null
        CombatPatchRecord(campaignId = parts[0], patch = patch, recordedAtMillis = at)
    }

private fun encodeAttemptsCsv(map: Map<String, Int>): String =
    map.entries.filter { it.key.isNotBlank() && it.value > 0 }
        .joinToString(",") { "${it.key}:${it.value}" }

private fun decodeAttemptsCsv(csv: String): Map<String, Int> =
    csv.split(",")
        .mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            id to count
        }
        .toMap()

private fun GearEntity.toDomain(): GearItem {
    val meta = GameCatalog.gearMetaById[id]
    return GearItem(
        id = id,
        name = meta?.name ?: name,
        slot = GearSlot.entries.find { it.name == slotName } ?: GearSlot.WEAPON,
        tier = tier,
        maxTier = maxTier,
        weightKg = weightKg,
        firepowerPerTier = firepowerPerTier,
        stealthPerTier = stealthPerTier,
        techPerTier = techPerTier,
        resiliencePerTier = resiliencePerTier,
        survivalBonusPct = survivalBonusPct,
        unlocked = unlocked,
        baseCreditCost = baseCreditCost,
        baseSupplyCost = baseSupplyCost,
        description = meta?.description ?: description,
        requiredSchoolId = meta?.requiredSchoolId,
        requiredSchoolLabel = meta?.requiredSchoolLabel,
        requiredArmoryLevel = meta?.requiredArmoryLevel ?: 1
    )
}

private fun GearItem.toEntity(): GearEntity = GearEntity(
    id = id,
    name = name,
    slotName = slot.name,
    tier = tier,
    maxTier = maxTier,
    weightKg = weightKg,
    firepowerPerTier = firepowerPerTier,
    stealthPerTier = stealthPerTier,
    techPerTier = techPerTier,
    resiliencePerTier = resiliencePerTier,
    survivalBonusPct = survivalBonusPct,
    unlocked = unlocked,
    baseCreditCost = baseCreditCost,
    baseSupplyCost = baseSupplyCost,
    description = description
)

private fun DeploymentEntity.toDomain(): ActiveDeployment = ActiveDeployment(
    id = id,
    templateId = templateId,
    operatorIds = operatorIdsCsv.split(",").filter { it.isNotBlank() },
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    efficiencyPct = efficiencyPct,
    avgSurvivalPct = avgSurvivalPct,
    squadId = squadId,
    insertion = InsertionMethod.entries.find { it.name == insertionName } ?: InsertionMethod.GROUND,
    pilotId = pilotId,
    platoonLeaderId = platoonLeaderId,
    missionTitle = missionTitle,
    flightHours = flightHours,
    targetOperatorId = targetOperatorId,
    languageRegion = languageRegionName?.let { n -> TheaterRegion.entries.find { it.name == n } },
    coverBlownAtMillis = coverBlownAtMillis,
    coverBlown = coverBlown,
    coverPushOn = coverPushOn
)

private fun ActiveDeployment.toEntity(): DeploymentEntity = DeploymentEntity(
    id = id,
    templateId = templateId,
    operatorIdsCsv = operatorIds.joinToString(","),
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    efficiencyPct = efficiencyPct,
    avgSurvivalPct = avgSurvivalPct,
    squadId = squadId,
    insertionName = insertion.name,
    pilotId = pilotId,
    platoonLeaderId = platoonLeaderId,
    missionTitle = missionTitle,
    flightHours = flightHours,
    targetOperatorId = targetOperatorId,
    languageRegionName = languageRegion?.name,
    coverBlownAtMillis = coverBlownAtMillis,
    coverBlown = coverBlown,
    coverPushOn = coverPushOn
)

/** Citations stored as "missionId|title|millis|member+member;..." */
private fun encodeUnitCitationsCsv(citations: List<UnitCitation>): String =
    citations.joinToString(";") { c ->
        val title = c.missionTitle.replace(Regex("[|;+]"), " ")
        "${c.missionId}|$title|${c.earnedAtMillis}|${c.memberIds.joinToString("+")}|${c.type.name}"
    }

private fun decodeUnitCitationsCsv(csv: String): List<UnitCitation> =
    csv.split(";").mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size < 4) return@mapNotNull null
        UnitCitation(
            missionId = parts[0],
            missionTitle = parts[1],
            earnedAtMillis = parts[2].toLongOrNull() ?: 0L,
            memberIds = parts[3].split("+").filter { it.isNotBlank() },
            type = parts.getOrNull(4)?.let { UnitAwardType.fromName(it) } ?: UnitAwardType.MERITORIOUS_UNIT_COMMENDATION
        )
    }

private fun SquadEntity.toDomain(): TacticalSquad {
    val members = memberIdsCsv.split(",").filter { it.isNotBlank() }.distinct().take(MAX_SQUAD_SIZE)
    return TacticalSquad(
        id = id,
        name = name,
        memberIds = members,
        leaderId = leaderId?.takeIf { it in members } ?: members.firstOrNull(),
        teamLeaderId = teamLeaderId?.takeIf { it in members && members.size >= TEAM_LEADER_MIN_SQUAD_SIZE },
        unitCitations = decodeUnitCitationsCsv(unitCitationsCsv),
        cohesionXp = cohesionXp,
        missionsTogether = missionsTogether,
        createdAtMillis = createdAtMillis,
        standardPatch = UnitPatchStyle.entries.find { it.name == standardPatchName }?.takeIf { it.isDivision }
            ?: UnitPatchStyle.DIVISION_1,
        standardCamo = standardCamoName?.let { n -> CamoPattern.entries.find { it.name == n } },
        standardHeadgear = standardHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardDressStyle = standardDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardBravosHeadgear = standardBravosHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardBravosDressStyle = standardBravosDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardMessDressStyle = standardMessDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardMessHeadgear = standardMessHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardAlphasGloves = standardAlphasGloves,
        standardMessGloves = standardMessGloves,
        isSpecialForces = isSpecialForces
    )
}

private fun TacticalSquad.toEntity(): SquadEntity {
    val members = memberIds.distinct().take(MAX_SQUAD_SIZE)
    return SquadEntity(
        id = id,
        name = name,
        memberIdsCsv = members.joinToString(","),
        leaderId = leaderId?.takeIf { it in members } ?: members.firstOrNull(),
        teamLeaderId = teamLeaderId?.takeIf { it in members },
        unitCitationsCsv = encodeUnitCitationsCsv(unitCitations),
        cohesionXp = cohesionXp.coerceIn(0, MAX_SQUAD_COHESION_XP),
        missionsTogether = missionsTogether.coerceAtLeast(0),
        createdAtMillis = createdAtMillis,
        standardPatchName = standardPatch.name,
        standardCamoName = standardCamo?.name,
        standardHeadgearName = standardHeadgear?.name,
        standardDressStyleName = standardDressStyle?.name,
        standardBravosHeadgearName = standardBravosHeadgear?.name,
        standardBravosDressStyleName = standardBravosDressStyle?.name,
        standardMessDressStyleName = standardMessDressStyle?.name,
        standardMessHeadgearName = standardMessHeadgear?.name,
        standardAlphasGloves = standardAlphasGloves,
        standardMessGloves = standardMessGloves,
        isSpecialForces = isSpecialForces
    )
}

private fun CitationEntity.toDomain(): AwardedMedal = AwardedMedal(
    id = id,
    operatorId = operatorId,
    medalId = medalId,
    citationNote = citationNote,
    missionTitle = missionTitle,
    awardedAtMillis = awardedAtMillis
)

private fun AwardedMedal.toEntity(): CitationEntity = CitationEntity(
    id = id,
    operatorId = operatorId,
    medalId = medalId,
    citationNote = citationNote,
    missionTitle = missionTitle,
    awardedAtMillis = awardedAtMillis
)

private fun PendingDeedEntity.toDomain(): PendingDeed = PendingDeed(
    id = id,
    operatorId = operatorId,
    missionTitle = missionTitle,
    deedSummary = deedSummary,
    recommendedMedalId = recommendedMedalId,
    createdAtMillis = createdAtMillis
)

private fun PendingDeed.toEntity(): PendingDeedEntity = PendingDeedEntity(
    id = id,
    operatorId = operatorId,
    missionTitle = missionTitle,
    deedSummary = deedSummary,
    recommendedMedalId = recommendedMedalId,
    createdAtMillis = createdAtMillis
)
