package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.TacticalCalculator
import com.example.domain.model.OperatorRank
import com.example.ui.theme.LocalAegisColors

@Composable
fun CommandersGuideDialog(
    isFirstTimeBrief: Boolean,
    onDismiss: () -> Unit,
    onConfirmResetCampaign: () -> Unit
) {
    val extra = LocalAegisColors.current
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        BackHandler { showResetConfirm = false }
    } else {
        BackHandler { onDismiss() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = extra.brassBright
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isFirstTimeBrief) "INITIAL DIRECTIVE • AEGIS HQ" else "COMMANDER'S OPERATIONS GUIDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                Text(
                    text = "Aegis Ops Field Manual",
                    style = MaterialTheme.typography.titleLarge,
                    color = extra.brassBright
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Command an Army task force from a local Forward Operating Base. Upgrade facilities, qualify soldiers at specialty schools, staff HQ desks, and confer decorations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GuideSectionCard(
                    icon = Icons.Filled.Radar,
                    title = "1. Ops & Specialty Schools",
                    body = "Run Drills or launch field missions & schools. Technical schools enforce MOS fit, prerequisites, pass odds, and attempt limits, while NCO Academy (BLC), Senior Leader Course (SLC), and Officer Candidate School (OCS) unlock Promotion Boards across career tiers."
                )

                GuideSectionCard(
                    icon = Icons.Filled.Groups,
                    title = "2. Squad & Armory",
                    body = "Enlist post-Basic Privates (PV2), equip/upgrade Weapons, Optics, Armor, Helmets & Utility kits, and tailor field camo patterns. Standard kit equips any soldier, while specialized weapons and tactical gear lock behind matching Army Schools and Armory Bay facility levels."
                )

                GuideSectionCard(
                    icon = Icons.Filled.AccountBalance,
                    title = "3. Directorate HQ Wing",
                    body = "Assign seasoned NCOs (SGT E-5+) and Officers (O-1+) to Commanding Officer (CO), XO, and S-1–S-4 Staff desks. HQ Staff continue promoting and earning honors via Staff Support & Diplomatic/School ops, but do not deploy on routine combat raids."
                )

                GuideSectionCard(
                    icon = Icons.Filled.Checkroom,
                    title = "4. Uniforms, Promotion Boards & Tailor",
                    body = "Ranks within a category (Soldier PVT–SPC, NCO SGT–SSG, SNCO SFC–SGM, Officer 2LT–COL, Flag BG–GEN) advance automatically with XP. Crossing categories (Soldier → NCO → SNCO → Officer → Flag Officer) convenes a Promotion Board with merit prerequisites (BLC, SLC, OCS, ops & honors), HQ board selection opinions, and a permanent Commander Endorsement bonus. Earned awards stack permanently and can be arranged in the Tailor Studio."
                )

                GuideSectionCard(
                    icon = Icons.Filled.MilitaryTech,
                    title = "5. Veteran Retirement & Legacy",
                    body = "Long-serving soldiers (${OperatorRank.SGT.code} E-5+ with ${TacticalCalculator.MIN_RETIREMENT_OPS}+ ops) can submit Retirement Requests that you may Approve or Deny. Approving frees their Field or HQ billet, grants muster-out PRS, memorializes them in the Hall of Honor, and adds a small permanent Veteran Legacy boost to Base Ops (capped at ${TacticalCalculator.formatBpsPercent(TacticalCalculator.MAX_VETERAN_LEGACY_BPS)}). Retirements too close together trigger spacing damping that recovers over 30 minutes."
                )

                HorizontalDivider(color = extra.borderSubtle)

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LOCAL CAMPAIGN SAVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Your progress is stored locally on this device. You can reset back to initial HQ orders anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("request_reset_campaign"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Campaign Save")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("dismiss_command_guide"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brass,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(if (isFirstTimeBrief) "Assume Command" else "Close Guide")
            }
        }
    )

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Confirm Campaign Reset", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "This will permanently clear all local soldiers, promotions, medals, HQ staff billets, gear upgrades, and credits, restoring initial starting orders. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onConfirmResetCampaign()
                        onDismiss()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_reset_campaign"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Reset Campaign")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirm = false },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("cancel_reset_campaign")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GuideSectionCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    val extra = LocalAegisColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, extra.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = extra.signalsCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
