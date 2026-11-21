package app.campusbazaar.domain

/**
 * Pure negotiation rules shared by the chat cards and sheets. The server enforces the same rules;
 * these keep the UI honest (which buttons show) and resolve live card state.
 */
object DealRules {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val COUNTERED = "countered"
    const val WITHDRAWN = "withdrawn"

    private fun finality(state: String?): Int = when (state) {
        null, "" -> -1
        PENDING, "proposed" -> 0
        else -> 1
    }

    /**
     * An offer/meetup card's state comes from two sources: Nexconn message metadata (instant,
     * pushed to both phones) and the server record (authoritative, fetched). A final state from
     * either source wins over "pending"; when both are final, the server wins.
     */
    fun resolveState(metadataState: String?, serverState: String?, initial: String = PENDING): String {
        val meta = finality(metadataState)
        val server = finality(serverState)
        return when {
            server >= 1 -> serverState!!
            meta >= 1 -> metadataState!!
            server == 0 -> serverState!!
            meta == 0 -> metadataState!!
            else -> initial
        }
    }

    /** Only the student who did not make the offer can answer it, and only while it is pending. */
    fun canRespond(state: String, fromUserId: String, me: String): Boolean = state == PENDING && fromUserId != me

    fun canProposeMeetup(listingStatus: ListingStatus): Boolean = listingStatus == ListingStatus.Active || listingStatus == ListingStatus.Reserved

    /** Validates an offer amount; returns a message to show, or null when fine. */
    fun validateOffer(amount: Int?, askingPrice: Int, negotiable: Boolean): String? = when {
        amount == null -> "Enter an amount in rupees."
        amount <= 0 -> "Offer at least ₹1."
        amount > 500_000 -> "That's above the campus limit of ₹5,00,000."
        !negotiable && amount != askingPrice -> "The seller set a fixed price of ${Money.rupees(askingPrice)}."
        else -> null
    }

    /** Short, friendly hint comparing an offer with the asking price. */
    fun offerHint(amount: Int, askingPrice: Int): String? {
        if (askingPrice <= 0 || amount <= 0) return null
        val pct = ((askingPrice - amount) * 100.0 / askingPrice).toInt()
        return when {
            amount > askingPrice -> "Above the asking price"
            pct == 0 -> "Asking price"
            pct <= 15 -> "$pct% below asking. Likely to be accepted"
            pct <= 35 -> "$pct% below asking. Expect a counter"
            else -> "$pct% below asking. The seller may decline"
        }
    }

    /** A rough AI-range check so sellers see if their price is far from the suggestion. */
    fun priceVersusRange(price: Int, min: Int?, max: Int?): String? {
        if (min == null || max == null || min <= 0 || price <= 0) return null
        return when {
            price < min * 0.7 -> "Well below the suggested range. Students will snap it up."
            price > max * 1.3 -> "Above the suggested range. It may take longer to sell."
            else -> null
        }
    }
}

object InputRules {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email."
        !emailRegex.matches(email.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.length < 8 -> "Use at least 8 characters."
        password.length > 128 -> "That's too long."
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.trim().length < 2 -> "Enter your name."
        name.trim().length > 60 -> "Keep it under 60 characters."
        else -> null
    }

    fun titleError(title: String): String? = when {
        title.trim().length < 3 -> "Give it a title (at least 3 characters)."
        title.trim().length > 80 -> "Keep the title under 80 characters."
        else -> null
    }
}
