package app.campusbazaar

import app.campusbazaar.domain.Category
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.InputRules
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MoneyTest {
    @Test fun formatsWithIndianGrouping() {
        assertEquals("₹650", Money.rupees(650))
        assertEquals("₹1,250", Money.rupees(1250))
        assertEquals("₹1,25,000", Money.rupees(125000))
    }

    @Test fun parsesWhatStudentsType() {
        assertEquals(1200, Money.parse("₹ 1,200"))
        assertEquals(450, Money.parse("450"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("123456789"))
    }
}

class DealRulesTest {
    @Test fun finalStateFromEitherSourceBeatsPending() {
        assertEquals("accepted", DealRules.resolveState("accepted", "pending"))
        assertEquals("declined", DealRules.resolveState("pending", "declined"))
        assertEquals("countered", DealRules.resolveState(null, "countered"))
    }

    @Test fun serverWinsWhenBothAreFinal() {
        assertEquals("accepted", DealRules.resolveState("declined", "accepted"))
    }

    @Test fun defaultsToInitialWhenUnknown() {
        assertEquals("pending", DealRules.resolveState(null, null))
        assertEquals("proposed", DealRules.resolveState(null, null, initial = "proposed"))
        assertEquals("confirmed", DealRules.resolveState("proposed", "confirmed", initial = "proposed"))
    }

    @Test fun onlyTheOtherStudentCanRespondToAPendingOffer() {
        assertTrue(DealRules.canRespond("pending", fromUserId = "a", me = "b"))
        assertTrue(!DealRules.canRespond("pending", fromUserId = "a", me = "a"))
        assertTrue(!DealRules.canRespond("accepted", fromUserId = "a", me = "b"))
    }

    @Test fun validatesOffers() {
        assertNull(DealRules.validateOffer(500, 600, negotiable = true))
        assertNotNull(DealRules.validateOffer(null, 600, negotiable = true))
        assertNotNull(DealRules.validateOffer(0, 600, negotiable = true))
        assertNotNull(DealRules.validateOffer(500, 600, negotiable = false))
        assertNull(DealRules.validateOffer(600, 600, negotiable = false))
    }

    @Test fun hintsHowLikelyAnOfferIs() {
        assertEquals("Asking price", DealRules.offerHint(600, 600))
        assertTrue(DealRules.offerHint(540, 600)!!.contains("Likely"))
        assertTrue(DealRules.offerHint(300, 600)!!.contains("decline"))
    }

    @Test fun flagsPricesFarFromTheAiRange() {
        assertNull(DealRules.priceVersusRange(700, 600, 900))
        assertNotNull(DealRules.priceVersusRange(200, 600, 900))
        assertNotNull(DealRules.priceVersusRange(2000, 600, 900))
        assertNull(DealRules.priceVersusRange(700, null, null))
    }
}

class InputRulesTest {
    @Test fun emails() {
        assertNull(InputRules.emailError("asha@college.edu"))
        assertNotNull(InputRules.emailError("asha@"))
        assertNotNull(InputRules.emailError(""))
    }

    @Test fun passwordsAndNames() {
        assertNotNull(InputRules.passwordError("short"))
        assertNull(InputRules.passwordError("long enough"))
        assertNotNull(InputRules.nameError(" a "))
        assertNull(InputRules.titleError("Casio fx-991EX"))
        assertNotNull(InputRules.titleError("ab"))
    }
}

class CatalogTest {
    @Test fun idsMatchTheServerEnums() {
        assertEquals(listOf("books", "electronics", "cycles", "hostel_room", "notes_stationery", "sports", "other"), Category.entries.map { it.id })
        assertEquals(listOf("new", "like_new", "good", "fair", "for_parts"), Condition.entries.map { it.id })
        assertEquals(Category.Other, Category.of("unknown"))
    }

    @Test fun relativeTimes() {
        val now = Instant.parse("2026-11-20T12:00:00Z")
        val zone = ZoneId.of("Asia/Kolkata")
        assertEquals("just now", TimeText.relative(now.minusSeconds(20), now, zone))
        assertEquals("5 min ago", TimeText.relative(now.minusSeconds(300), now, zone))
        assertEquals("3 h ago", TimeText.relative(now.minusSeconds(3 * 3600), now, zone))
        assertEquals("", TimeText.relative(null, now, zone))
    }
}
