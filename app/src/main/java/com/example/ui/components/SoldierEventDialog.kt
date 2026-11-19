package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.PendingSoldierEvent
import com.example.domain.SoldierEventContext
import com.example.domain.SoldierEvents
import com.example.domain.model.ResourceBundle
import com.example.ui.theme.LocalAegisColors

/** Item 69: one soldier event with its choices, a "Decide later" button and the time left. */
@Composable
fun SoldierEventDialog(
    event: PendingSoldierEvent,
    context: SoldierEventContext,
    resources: ResourceBundle,
    onChoose: (String) -> Unit,
    onLater: () -> Unit
) {
    val extra = LocalAegisColors.current
    val card = SoldierEvents.card(event, context)
    val minsLeft = ((event.expiresAtMillis - context.now).coerceAtLeast(0L) + 59_999L) / 60_000L
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("soldier_event_dialog"),
        title = { Text(card.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(card.body, style = MaterialTheme.typography.bodyMedium)
                card.choices.forEach { choice ->
                    val (cr, sup) = SoldierEvents.choiceCost(event.kind, choice.id)
                    val affordable = resources.credits >= cr && resources.supplies >= sup
                    OutlinedButton(
                        onClick = { onChoose(choice.id) },
                        enabled = affordable,
                        modifier = Modifier.fillMaxWidth().testTag("event_choice_${choice.id}")
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(choice.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (affordable) choice.result else "Not enough resources",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                        }
                    }
                }
                Text("Expires in about $minsLeft min.", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("event_later")) { Text("Decide later") }
        }
    )
}

/** Item 69: brings a deferred event back. */
@Composable
fun SoldierEventBell(onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick, modifier = Modifier.testTag("soldier_event_bell")) {
        BadgedBox(badge = { Badge { Text("1") } }) {
            Icon(Icons.Filled.Notifications, contentDescription = "Soldier request waiting")
        }
    }
}
