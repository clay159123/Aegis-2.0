package com.example.data.local

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AegisRepository(private val dao: AegisDao) {

    private val commanderMutex = Mutex()

    val commanderState: Flow<CommanderStateEntity?> = dao.observeCommanderState()

    val facilities: Flow<List<FacilityState>> = dao.observeFacilities().map { list ->
        list.mapNotNull { entity ->
            FacilityType.entries.find { it.id == entity.typeId }?.let { type ->
                FacilityState(type = type, level = entity.level)
            }
        }.sortedBy { FacilityType.entries.indexOf(it.type) }
    }

    val operators: Flow<List<Operator>> = dao.observeOperators().map { list ->
        list.map { it.toDomain() }
            .sortedWith(
                compareBy<Operator> { it.status.isOutOfActiveService }
                    .thenByDescending { it.unlocked }
                    .thenByDescending { it.xp }
            )
    }

    val gearItems: Flow<List<GearItem>> = dao.observeGear().map { list ->
        list.map { it.toDomain() }
            .sortedWith(compareBy<GearItem> { it.slot.ordinal }.thenBy { it.baseCreditCost })
    }

    val activeDeployments: Flow<List<ActiveDeployment>> = dao.observeDeployments().map { list ->
        list.map { it.toDomain() }
    }

    val citations: Flow<List<AwardedMedal>> = dao.observeCitations().map { list ->
        list.map { it.toDomain() }
    }

    val pendingDeeds: Flow<List<PendingDeed>> = dao.observePendingDeeds().map { list ->
        list.map { it.toDomain() }
    }

    val squads: Flow<List<TacticalSquad>> = dao.observeSquads().map { list ->
        list.map { it.toDomain() }
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
            rotationUpdatedAtMillis = nowMillis
        )
        dao.upsertCommanderState(initialState)

        dao.upsertFacilities(
            FacilityType.entries.map { FacilityEntity(typeId = it.id, level = 1) }
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

        dao.upsertSquad(
            SquadEntity(
                id = "sq_alpha",
                name = "Alpha Squad",
                memberIdsCsv = "op_kestrel,op_vesper,op_cipher",
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
        dao.upsertFacility(FacilityEntity(typeId = facility.type.id, level = facility.level))
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
    unlocked = unlocked,
    recruitCreditCost = recruitCreditCost,
    recruitIntelCost = recruitIntelCost,
    bio = bio,
    authorizedTier = run {
        val parsed = RankTier.entries.find { it.name == authorizedTierName } ?: OperatorRank.forXp(xp).tier
        if (parsed == RankTier.NCO && xp > OperatorRank.SFC.xpRequired) RankTier.SNCO else parsed
    }
)

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
    unlocked = unlocked,
    recruitCreditCost = recruitCreditCost,
    recruitIntelCost = recruitIntelCost,
    bio = bio
)

private fun encodeTailorOverridesCsv(o: UniformTailorOverrides): String {
    if (o == UniformTailorOverrides()) return ""
    val leaderPinToken = when (o.leaderPinOverride) {
        true -> "1"
        false -> "0"
        null -> "A"
    }
    return listOf(
        "patch:${o.unitPatch.name}",
        "cpatch:${o.combatPatch.name}",
        "mark:${o.marksmanshipBadge.name}",
        "neck:${o.neckSlot}",
        "tab1:${o.tabSlot1}",
        "tab2:${o.tabSlot2}",
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
        leaderPinOverride = when (map["lpin"]) {
            "1" -> true
            "0" -> false
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
        equippedByOperatorId = equippedByOperatorId,
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
    equippedByOperatorId = equippedByOperatorId,
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
    squadId = squadId
)

private fun ActiveDeployment.toEntity(): DeploymentEntity = DeploymentEntity(
    id = id,
    templateId = templateId,
    operatorIdsCsv = operatorIds.joinToString(","),
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    efficiencyPct = efficiencyPct,
    avgSurvivalPct = avgSurvivalPct,
    squadId = squadId
)

private fun SquadEntity.toDomain(): TacticalSquad {
    val members = memberIdsCsv.split(",").filter { it.isNotBlank() }.distinct().take(MAX_SQUAD_SIZE)
    return TacticalSquad(
        id = id,
        name = name,
        memberIds = members,
        leaderId = leaderId?.takeIf { it in members } ?: members.firstOrNull(),
        cohesionXp = cohesionXp,
        missionsTogether = missionsTogether,
        createdAtMillis = createdAtMillis,
        standardPatch = UnitPatchStyle.entries.find { it.name == standardPatchName } ?: UnitPatchStyle.AUTO_MOS,
        standardCamo = standardCamoName?.let { n -> CamoPattern.entries.find { it.name == n } },
        standardHeadgear = standardHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardDressStyle = standardDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardBravosHeadgear = standardBravosHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardBravosDressStyle = standardBravosDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardMessDressStyle = standardMessDressStyleName?.let { n -> DressUniformStyle.entries.find { it.name == n } },
        standardMessHeadgear = standardMessHeadgearName?.let { n -> HeadgearStyle.entries.find { it.name == n } },
        standardWhiteGloves = standardWhiteGloves
    )
}

private fun TacticalSquad.toEntity(): SquadEntity {
    val members = memberIds.distinct().take(MAX_SQUAD_SIZE)
    return SquadEntity(
        id = id,
        name = name,
        memberIdsCsv = members.joinToString(","),
        leaderId = leaderId?.takeIf { it in members } ?: members.firstOrNull(),
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
        standardWhiteGloves = standardWhiteGloves
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
