package app.campusbazaar

import app.campusbazaar.chat.messages.ALL_CUSTOM_MESSAGES
import app.campusbazaar.chat.messages.MeetupCardMessage
import app.campusbazaar.chat.messages.NoticeMessage
import app.campusbazaar.chat.messages.OfferCardMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The custom Nexconn message field encoding must round-trip, including JSON number types. */
class CustomMessagesTest {
    @Test fun offerRoundTripsAndToleratesDoubles() {
        val offer = OfferCardMessage().apply {
            offerId = "o1"; listingId = "l1"; listingTitle = "Casio fx-991EX"; amount = 650; askingPrice = 700; fromUserId = "cbzuA"
        }
        val fields = offer.encodeFields().mapValues { (_, v) -> if (v is Int) v.toDouble() else v } // JSON decoders often yield doubles
        val decoded = OfferCardMessage().apply { decodeFields(fields) }
        assertEquals("o1", decoded.offerId)
        assertEquals(650, decoded.amount)
        assertEquals(700, decoded.askingPrice)
        assertEquals("cbz:offer", decoded.messageType())
    }

    @Test fun meetupKeepsOptionalCoordinates() {
        val m = MeetupCardMessage().apply { meetupId = "m1"; spot = "Library"; scheduledAt = 1_790_000_000_000; lat = 28.5; lng = 77.2 }
        val decoded = MeetupCardMessage().apply { decodeFields(m.encodeFields()) }
        assertEquals(28.5, decoded.lat!!, 0.0)
        assertEquals(1_790_000_000_000, decoded.scheduledAt)
        val noPin = MeetupCardMessage().apply { decodeFields(MeetupCardMessage().apply { spot = "Gate" }.encodeFields()) }
        assertNull(noPin.lat)
    }

    @Test fun noticeMatchesServerFieldNames() {
        val fromServer = mapOf<String, Any?>("kind" to "rate_deal", "title" to "Deal done", "body" to "Rate it", "listingId" to "l9", "peerUserId" to "cbzuB")
        val n = NoticeMessage().apply { decodeFields(fromServer) }
        assertEquals("rate_deal", n.kind)
        assertEquals("l9", n.listingId)
        assertNull(n.wantedId)
    }

    @Test fun typesAreUniqueAndNotReserved() {
        val types = ALL_CUSTOM_MESSAGES.map { it.getDeclaredConstructor().newInstance().messageType() }
        assertEquals(types.size, types.toSet().size)
        assertTrue(types.all { it.startsWith("cbz:") && !it.startsWith("RC:") && it.length <= 32 })
    }
}
