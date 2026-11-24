package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.*

enum class PromotionEndorsementFocus(
    val title: String,
    val subtitle: String,
    val statBonus: StatProfile
) {
    COMBAT_LEADERSHIP(
        title = "Frontline Spearhead",
        subtitle = "Board cites offensive initiative & decisive combat leadership (+1 FP)",
        statBonus = StatProfile(1, 0, 0, 0)
    ),
    TACTICAL_CUNNING(
        title = "Operational Craft",
        subtitle = "Board cites field reconnaissance, technical fluency & planning (+1 ST, +1 TC)",
        statBonus = StatProfile(0, 1, 1, 0)
    ),
    COMMAND_FORTITUDE(
        title = "Unit Anchor & Stamina",
        subtitle = "Board cites poise under pressure & troop endurance (+2 RS)",
        statBonus = StatProfile(0, 0, 0, 2)
    )
}

enum class BoardStance(val label: String) {
    STRONGLY_ENDORSE("STRONGLY ENDORSE"),
    ENDORSE("ENDORSE"),
    RESERVATIONS("WITH RESERVATIONS"),
    DEFER("DEFER / NOT READY")
}

enum class BoardConsensus(val label: String, val summary: String) {
    UNANIMOUS("Unanimous Selection", "All three board reviewers recommend immediate promotion."),
    FAVORABLE("Favorable Majority", "Board supports advancement pending Commander endorsement."),
    SPLIT("Marginal / Split Board", "Minimum prerequisites met, though reviewers note development gaps."),
    BLOCKED("Dossier Incomplete", "Board cannot authorize advancement until all merit prerequisites pass.")
}

data class PromotionMeritCheck(
    val id: String,
    val label: String,
    val statusText: String,
    val satisfied: Boolean
)

data class BoardMemberOpinion(
    val reviewerRole: String,
    val reviewerName: String,
    val stance: BoardStance,
    val commentary: String
)

data class PromotionBoardEvaluation(
    val currentRank: OperatorRank,
    val nextRank: OperatorRank?,
    val isCrossTierGate: Boolean,
    val creditCost: Double,
    val prestigeCost: Int,
    val meritChecks: List<PromotionMeritCheck>,
    val allMeritsMet: Boolean,
    val opinions: List<BoardMemberOpinion>,
    val consensus: BoardConsensus,
    val requiredSchoolId: String? = null,
    val requiredSchoolTitle: String? = null,
    /** Items 126 and 135: why no board can sit (billet cap, enlisted billet), shown on the HQ card. */
    val blockedReason: String? = null
)

object PromotionBoardEvaluator {

    fun evaluate(
        operator: Operator,
        allOperators: List<Operator>,
        citations: List<AwardedMedal>,
        gearItems: List<GearItem>,
        facilities: List<FacilityState>
    ): PromotionBoardEvaluation {
        val currentRank = operator.rank
        val nextRank = OperatorRank.nextRank(currentRank)
        // A seated Command Chief Warrant Officer mentors the warrant corps: cheaper warrant boards
        val warrantDiscountPct = if (currentRank.isWarrant) {
            allOperators.filter { it.status == OperatorStatus.HQ_STAFF && it.id != operator.id }
                .maxOfOrNull { it.hqRole?.warrantPromotionDiscountPct ?: 0 } ?: 0
        } else 0
        // Item 110: a seated Command Sergeant Major makes enlisted boards cheaper
        val enlistedDiscountPct = if (currentRank.tier.isEnlisted && nextRank?.tier?.isEnlisted == true) {
            allOperators.filter { it.status == OperatorStatus.HQ_STAFF && it.id != operator.id }
                .maxOfOrNull { it.hqRole?.enlistedBoardDiscountPct ?: 0 } ?: 0
        } else 0
        val discountPct = maxOf(warrantDiscountPct, enlistedDiscountPct)
        val creditCost = currentRank.promotionCreditCost() * (100 - discountPct) / 100.0
        val prestigeCost = currentRank.promotionPrestigeCost() * (100 - discountPct) / 100

        // Item 107: an HQ billet's top rank ends promotions there (treated like having no next rank).
        // Item 135: a COL capped as XO can still go before the general board when a general billet is open.
        val seatedRole = operator.hqRole.takeIf { operator.status == OperatorStatus.HQ_STAFF }
        val blockedReason = when {
            nextRank == null -> null
            operator.isAtHqRankCap ->
                "${seatedRole?.shortCode ?: "Billet"} tops out at ${operator.hqRankCap?.code}. Move to a senior billet or the field to rise further."
            // Item 126: the S-3 and CSM are enlisted billets; a commission means leaving them
            seatedRole?.isEnlistedOnly == true && !nextRank.tier.isEnlisted ->
                "${seatedRole.shortCode} is an enlisted billet. Return to the field to pursue a commission."
            // Session 38: broken morale keeps a soldier from a board (pins inside a tier still work)
            OperatorRank.isBoardGate(currentRank, nextRank) && MoraleRules.boardBlocker(operator) != null ->
                MoraleRules.boardBlocker(operator)
            else -> null
        }
        if (nextRank == null || blockedReason != null) {
            return PromotionBoardEvaluation(
                currentRank = currentRank,
                nextRank = null,
                isCrossTierGate = false,
                creditCost = creditCost,
                prestigeCost = prestigeCost,
                meritChecks = emptyList(),
                allMeritsMet = false,
                opinions = emptyList(),
                consensus = BoardConsensus.BLOCKED,
                blockedReason = blockedReason
            )
        }

        val isCrossTier = OperatorRank.isBoardGate(currentRank, nextRank)
        if (!isCrossTier) {
            return PromotionBoardEvaluation(
                currentRank = currentRank,
                nextRank = nextRank,
                isCrossTierGate = false,
                creditCost = creditCost,
                prestigeCost = prestigeCost,
                meritChecks = emptyList(),
                allMeritsMet = true,
                opinions = emptyList(),
                consensus = BoardConsensus.FAVORABLE
            )
        }

        val totalOps = operator.missionsCompleted + operator.staffOpsSupported
        val opCitations = citations.filter { it.operatorId == operator.id }
        val opMedalDefs = opCitations.mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
        val maxHazard = operator.highestRiskSurvived?.baseDangerPenalty ?: -1
        val completedSchoolsCount = operator.completedMissionIds.count { id ->
            GameCatalog.missions.any { it.id == id && it.scope == MissionScope.TRAINING }
        }
        val activeTierCount = allOperators.count {
            it.unlocked && !it.status.isOutOfActiveService && it.rank.tier == nextRank.tier
        }

        val meritChecks = mutableListOf<PromotionMeritCheck>()
        var requiredSchoolId: String? = null
        var requiredSchoolTitle: String? = null

        // 1. Experience threshold check (must hit tier XP gate)
        meritChecks += PromotionMeritCheck(
            id = "xp_gate",
            label = "Tier XP Ceiling",
            statusText = "${operator.xp}/${nextRank.xpRequired} XP",
            satisfied = operator.xp >= nextRank.xpRequired
        )

        // 2. Duty standing check (cannot pin cross-tier rank while in Medbay)
        val inGoodStanding = operator.status != OperatorStatus.RECOVERING && !operator.status.isOutOfActiveService
        meritChecks += PromotionMeritCheck(
            id = "standing",
            label = "Duty Standing",
            statusText = if (inGoodStanding) "Active Duty (${operator.status.label})" else "Blocked (${operator.status.label})",
            satisfied = inGoodStanding
        )

        // 3. Tier-specific Leadership School & Merit Prerequisites
        when (nextRank.tier) {
            RankTier.WARRANT -> {
                val opsNeeded = TacticalCalculator.warrantOpsRequired(nextRank)
                meritChecks += PromotionMeritCheck(
                    id = "ops_warrant",
                    label = "Warrant Service Record",
                    statusText = "$totalOps/$opsNeeded Ops Completed",
                    satisfied = totalOps >= opsNeeded
                )
                if (operator.isAviator) {
                    val skillNeeded = TacticalCalculator.warrantFlightSkillRequired(nextRank)
                    meritChecks += PromotionMeritCheck(
                        id = "flight_warrant",
                        label = "Flight Proficiency",
                        statusText = "Flight Skill ${operator.flightSkill}/$skillNeeded",
                        satisfied = operator.flightSkill >= skillNeeded
                    )
                } else if (nextRank.warrantGrade >= 3) {
                    meritChecks += PromotionMeritCheck(
                        id = "honors_warrant",
                        label = "Technical Merit",
                        statusText = "${opMedalDefs.size}/1 Decoration Held",
                        satisfied = opMedalDefs.isNotEmpty()
                    )
                }
            }
            RankTier.NCO -> {
                requiredSchoolId = "msn_nco_school"
                requiredSchoolTitle = "Basic Leader Course (BLC)"
                val hasBlc = requiredSchoolId in operator.completedMissionIds
                meritChecks += PromotionMeritCheck(
                    id = "school_blc",
                    label = "NCO Academy (BLC)",
                    statusText = if (hasBlc) "Graduated" else "Required",
                    satisfied = hasBlc
                )
                meritChecks += PromotionMeritCheck(
                    id = "ops_nco",
                    label = "Operational Experience",
                    statusText = "$totalOps/2 Ops Completed",
                    satisfied = totalOps >= 2
                )
            }
            RankTier.SNCO -> {
                requiredSchoolId = "msn_snco_school"
                requiredSchoolTitle = "Senior Leader Course (SLC)"
                val hasSlc = requiredSchoolId in operator.completedMissionIds
                meritChecks += PromotionMeritCheck(
                    id = "school_slc",
                    label = "Senior NCO Academy (SLC)",
                    statusText = if (hasSlc) "Graduated" else "Required",
                    satisfied = hasSlc
                )
                meritChecks += PromotionMeritCheck(
                    id = "ops_snco",
                    label = "Operational Experience",
                    statusText = "$totalOps/5 Ops Completed",
                    satisfied = totalOps >= 5
                )
                meritChecks += PromotionMeritCheck(
                    id = "honors_snco",
                    label = "Service Honors",
                    statusText = "${opMedalDefs.size}/1 Decoration Held",
                    satisfied = opMedalDefs.isNotEmpty()
                )
                val fieldCredible = operator.highestRiskSurvived != null || operator.staffOpsSupported >= 2
                meritChecks += PromotionMeritCheck(
                    id = "field_snco",
                    label = "Deployment Credibility",
                    statusText = if (fieldCredible) "Field Op / 2+ HQ Ops Met" else "Need 1+ Field Op or 2+ HQ Ops",
                    satisfied = fieldCredible
                )
            }
            RankTier.OFFICER -> {
                requiredSchoolId = "msn_ocs_school"
                requiredSchoolTitle = "Officer Candidate School (OCS)"
                val hasOcs = requiredSchoolId in operator.completedMissionIds
                meritChecks += PromotionMeritCheck(
                    id = "school_ocs",
                    label = "Officer Candidate School (OCS)",
                    statusText = if (hasOcs) "Commission Qualified" else "Mandatory to Commission",
                    satisfied = hasOcs
                )
                meritChecks += PromotionMeritCheck(
                    id = "ops_officer",
                    label = "Operational Record",
                    statusText = "$totalOps/8 Ops Completed",
                    satisfied = totalOps >= 8
                )
                meritChecks += PromotionMeritCheck(
                    id = "honors_officer",
                    label = "Meritorious Honors",
                    statusText = "${opMedalDefs.size}/2 Decorations Held",
                    satisfied = opMedalDefs.size >= 2
                )
                val combatProven = maxHazard >= MissionRisk.MODERATE.baseDangerPenalty || operator.staffOpsSupported >= 3
                meritChecks += PromotionMeritCheck(
                    id = "combat_officer",
                    label = "Contested Seasoning",
                    statusText = if (combatProven) "Mod+ Op / 3+ HQ Ops Met" else "Survive Moderate+ Op or 3+ HQ Ops",
                    satisfied = combatProven
                )
            }
            RankTier.FLAG_OFFICER -> {
                // Item 61: every star is a board; each one asks for more (BG 12 ops, MG 16, LTG 20, GEN 25)
                val opsNeeded = when (nextRank) {
                    OperatorRank.MG -> 16
                    OperatorRank.LTG -> 20
                    OperatorRank.GEN -> 25
                    else -> 12
                }
                meritChecks += PromotionMeritCheck(
                    id = "ops_flag",
                    label = "Executive Service Record",
                    statusText = "$totalOps/$opsNeeded Ops Completed",
                    satisfied = totalOps >= opsNeeded
                )
                val commandTenureMet = operator.status == OperatorStatus.HQ_STAFF ||
                    operator.staffOpsSupported >= 3 ||
                    completedSchoolsCount >= 3 ||
                    operator.missionsCompleted >= 6
                meritChecks += PromotionMeritCheck(
                    id = "command_flag",
                    label = "Command & Staff Tenure",
                    statusText = if (commandTenureMet) {
                        "Staff / Academy Command Met"
                    } else {
                        "HQ Staff, 3+ HQ Ops, 3+ Schools, or 6+ Ops"
                    },
                    satisfied = commandTenureMet
                )
                val hasKeyHonor = opMedalDefs.any {
                    it.tier == MedalTier.VALOR ||
                        it.tier == MedalTier.SUPREME ||
                        it.id == "medal_defense_superior" ||
                        it.id == "medal_meritorious" ||
                        it.id == "cord_general_aiguillette"
                }
                val honorsNeeded = 2 + nextRank.flagGrade
                val honorsFlagOk = opMedalDefs.size >= honorsNeeded && hasKeyHonor
                meritChecks += PromotionMeritCheck(
                    id = "honors_flag",
                    label = "Distinguished Honors",
                    statusText = if (honorsFlagOk) {
                        "${opMedalDefs.size} Honors (Valor/Leadership Held)"
                    } else {
                        "${opMedalDefs.size}/$honorsNeeded Honors (incl. Valor/Merit)"
                    },
                    satisfied = honorsFlagOk
                )
                // Item 135: generals serve only on HQ staff, so a star needs a general billet to fill
                // Item 135: seat them as CO or Deputy first; the XO and other billets top out below BG
                val generalBillet = TacticalCalculator.seatedGeneralBillet(operator)
                meritChecks += PromotionMeritCheck(
                    id = "billet_flag",
                    label = "General Officer Billet",
                    statusText = when {
                        generalBillet != null -> "Seated as ${generalBillet.shortCode}"
                        operator.status == OperatorStatus.HQ_STAFF && operator.hqRole == HqStaffRole.DEPUTY_COMMANDER ->
                            "Deputy tops out at ${HqChain.deputyCap().code} under the current CO"
                        else -> "Must hold the CO or Deputy Commander billet"
                    },
                    satisfied = generalBillet != null
                )
            }
            RankTier.SOLDIER -> Unit
        }

        val allMeritsMet = meritChecks.all { it.satisfied }
        val opinions = buildBoardOpinions(
            operator = operator,
            nextRank = nextRank,
            allOperators = allOperators,
            citations = citations,
            opMedalDefs = opMedalDefs,
            gearItems = gearItems,
            facilities = facilities,
            totalOps = totalOps,
            completedSchoolsCount = completedSchoolsCount,
            activeTargetTierCount = activeTierCount,
            meritChecks = meritChecks,
            allMeritsMet = allMeritsMet
        )

        val consensus = when {
            !allMeritsMet -> BoardConsensus.BLOCKED
            opinions.all { it.stance == BoardStance.STRONGLY_ENDORSE } -> BoardConsensus.UNANIMOUS
            opinions.any { it.stance == BoardStance.RESERVATIONS || it.stance == BoardStance.DEFER } -> BoardConsensus.SPLIT
            else -> BoardConsensus.FAVORABLE
        }

        return PromotionBoardEvaluation(
            currentRank = currentRank,
            nextRank = nextRank,
            isCrossTierGate = true,
            creditCost = creditCost,
            prestigeCost = prestigeCost,
            meritChecks = meritChecks,
            allMeritsMet = allMeritsMet,
            opinions = opinions,
            consensus = consensus,
            requiredSchoolId = requiredSchoolId,
            requiredSchoolTitle = requiredSchoolTitle
        )
    }

    private fun buildBoardOpinions(
        operator: Operator,
        nextRank: OperatorRank,
        allOperators: List<Operator>,
        citations: List<AwardedMedal>,
        opMedalDefs: List<MedalDefinition>,
        gearItems: List<GearItem>,
        facilities: List<FacilityState>,
        totalOps: Int,
        completedSchoolsCount: Int,
        activeTargetTierCount: Int,
        meritChecks: List<PromotionMeritCheck>,
        allMeritsMet: Boolean
    ): List<BoardMemberOpinion> {
        fun staffReviewerName(role: HqStaffRole, fallback: String): String {
            val holder = allOperators.firstOrNull {
                it.unlocked && it.status == OperatorStatus.HQ_STAFF && it.hqRole == role
            }
            return if (holder != null) "${holder.rank.code} ${holder.callsign}" else fallback
        }

        val s1Name = staffReviewerName(HqStaffRole.S1_PERSONNEL_HONORS, "Directorate S-1 Desk")
        val s3Name = staffReviewerName(HqStaffRole.S3_OPS_DIRECTOR, "Brigade CSM / S-3 Panel")
        val coHolder = allOperators.firstOrNull {
            it.unlocked && it.status == OperatorStatus.HQ_STAFF && it.hqRole == HqStaffRole.CO_COMMANDING_OFFICER
        } ?: allOperators.firstOrNull {
            it.unlocked && it.status == OperatorStatus.HQ_STAFF && it.hqRole == HqStaffRole.XO_CHIEF_OF_STAFF
        }
        val coName = coHolder?.let { "${it.rank.code} ${it.callsign}" } ?: "Directorate Command Chief"

        val valorCount = opMedalDefs.count { it.tier == MedalTier.VALOR || it.tier == MedalTier.SUPREME }
        val honorsMissing = meritChecks.any { !it.satisfied && (it.id.startsWith("honors_") || it.id == "standing" || it.id == "xp_gate") }
        val s1Stance = when {
            honorsMissing -> BoardStance.DEFER
            opMedalDefs.size >= nextRank.tier.order + 1 || valorCount >= 1 -> BoardStance.STRONGLY_ENDORSE
            opMedalDefs.size >= nextRank.tier.order - 1 -> BoardStance.ENDORSE
            else -> BoardStance.RESERVATIONS
        }
        val topHonorNames = opMedalDefs.take(2).joinToString(", ") { it.shortName }
        val s1Comment = when (s1Stance) {
            BoardStance.DEFER -> {
                val blocker = meritChecks.firstOrNull { !it.satisfied && it.id in setOf("xp_gate", "standing", "honors_snco", "honors_officer", "honors_flag") }
                "Packet holds ${opMedalDefs.size} award(s); blocked on ${blocker?.label ?: "personnel prerequisite"} (${blocker?.statusText ?: "pending"})."
            }
            BoardStance.STRONGLY_ENDORSE ->
                "Exemplary file with ${opMedalDefs.size} decoration(s) (${topHonorNames}). Fully merits ${nextRank.tier.shortLabel} authority."
            BoardStance.ENDORSE ->
                "Clean administrative standing and ${opMedalDefs.size} recorded honor(s). Ready for ${nextRank.code} pay grade."
            BoardStance.RESERVATIONS ->
                "Minimum board packet met, though ${operator.callsign} holds only ${opMedalDefs.size} decoration(s) for a ${nextRank.tier.shortLabel} candidate."
        }

        // 2. S-3 / Ops & CSM Panel
        val failedSchoolTries = operator.schoolAttempts.entries.sumOf { (schoolId, tries) ->
            val passed = if (schoolId in operator.completedMissionIds) 1 else 0
            (tries - passed).coerceAtLeast(0)
        }
        val opsMissing = meritChecks.any { !it.satisfied && (it.id.startsWith("school_") || it.id.startsWith("ops_") || it.id.startsWith("combat_") || it.id.startsWith("command_") || it.id.startsWith("hazard_") || it.id.startsWith("field_")) }
        val riskLabel = operator.highestRiskSurvived?.label ?: "Garrison/Training Only"
        val s3Stance = when {
            opsMissing -> BoardStance.DEFER
            failedSchoolTries >= 2 && operator.timesWounded >= 2 -> BoardStance.RESERVATIONS
            completedSchoolsCount >= nextRank.tier.order + 1 && totalOps >= nextRank.tier.order * 3 -> BoardStance.STRONGLY_ENDORSE
            else -> BoardStance.ENDORSE
        }
        val s3Comment = when (s3Stance) {
            BoardStance.DEFER -> {
                val blocker = meritChecks.firstOrNull { !it.satisfied && !it.id.startsWith("xp") && !it.id.startsWith("honors") && it.id != "standing" }
                "Field panel withholds endorsement: ${blocker?.label ?: "school/ops gate"} not yet cleared (${blocker?.statusText ?: "pending"})."
            }
            BoardStance.STRONGLY_ENDORSE ->
                "$completedSchoolsCount Army schools graduated, $totalOps ops logged ($riskLabel). Troop credibility at ${nextRank.code} is unquestioned."
            BoardStance.ENDORSE ->
                "Passed required academy track with $totalOps ops ($riskLabel). Solid operational readiness for ${nextRank.tier.shortLabel} duties."
            BoardStance.RESERVATIONS ->
                "Clears the bar, but $failedSchoolTries school re-test(s) and ${operator.timesWounded} medevac(s) counsel steady supervision."
        }

        // 3. CO / Branch Chief (evaluates MOS readiness + current Directorate rank distribution)
        val opStats = TacticalCalculator.operatorStats(operator, gearItems, citations, facilities)
        val tierLabelPlural = when (nextRank.tier) {
            RankTier.SNCO -> "SNCOs"
            RankTier.OFFICER -> "Officers"
            RankTier.FLAG_OFFICER -> "Flag Officers"
            RankTier.WARRANT -> "Warrant Officers"
            else -> "${nextRank.tier.shortLabel}s"
        }
        val overcrowdingNote = when {
            nextRank.tier == RankTier.SNCO && activeTargetTierCount >= 3 ->
                " Note: roster already carries $activeTargetTierCount active SNCOs — ensure billet balance."
            nextRank.tier == RankTier.OFFICER && activeTargetTierCount >= 3 ->
                " Note: $activeTargetTierCount active Officers already on rolls; confirm command need."
            activeTargetTierCount == 0 ->
                " Would become the first active ${nextRank.tier.shortLabel} on Directorate orders."
            else ->
                " Directorate currently fields $activeTargetTierCount active $tierLabelPlural."
        }
        val coStance = when {
            !allMeritsMet -> BoardStance.DEFER
            nextRank.tier == RankTier.SNCO && activeTargetTierCount >= 4 -> BoardStance.RESERVATIONS
            opStats.totalPower >= nextRank.xpRequired / 40 + 45 -> BoardStance.STRONGLY_ENDORSE
            else -> BoardStance.ENDORSE
        }
        val coComment = if (!allMeritsMet) {
            "Board cannot forward to Commander signature until every merit check turns green.$overcrowdingNote"
        } else {
            "Effective power ${opStats.totalPower} in ${operator.specialty.label} (${operator.specialty.roleTag}).$overcrowdingNote"
        }

        return listOf(
            BoardMemberOpinion(
                reviewerRole = "S-1 Personnel & Honors",
                reviewerName = s1Name,
                stance = s1Stance,
                commentary = s1Comment
            ),
            BoardMemberOpinion(
                reviewerRole = "S-3 Operations & CSM",
                reviewerName = s3Name,
                stance = s3Stance,
                commentary = s3Comment
            ),
            BoardMemberOpinion(
                reviewerRole = "Commanding Officer / Branch",
                reviewerName = coName,
                stance = coStance,
                commentary = coComment
            )
        )
    }
}
