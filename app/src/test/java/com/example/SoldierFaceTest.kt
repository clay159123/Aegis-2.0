package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.model.OperatorSpecialty
import com.example.ui.components.SoldierFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Art 1: faces stay fixed per soldier until the Tailor rerolls them. */
class SoldierFaceTest {
    private fun op(i: Int) = GameCatalog.createPostBasicRecruit("face_$i", OperatorSpecialty.entries[i % OperatorSpecialty.entries.size], emptySet(), i)

    @Test fun sameSoldierSameFace() {
        val o = op(1)
        assertEquals(SoldierFace.traits(o), SoldierFace.traits(o.copy(morale = 10, name = "Someone Else")))
    }

    @Test fun newFaceChangesTraits() {
        val changed = (0 until 20).count { i ->
            val o = op(i)
            SoldierFace.traits(o) != SoldierFace.traits(o.copy(faceSeed = o.faceSeed + 1))
        }
        assertTrue("reroll should change nearly every face, changed $changed", changed >= 19)
    }

    @Test fun facesVaryAcrossSoldiers() {
        val distinct = (0 until 24).map { SoldierFace.traits(op(it)) }.toSet().size
        assertTrue(distinct >= 22)
    }

    @Test fun scarNeedsTwoWounds() {
        (0 until 30).forEach { assertNotEquals(true, SoldierFace.traits(op(it).copy(timesWounded = 1)).scar) }
    }
}
