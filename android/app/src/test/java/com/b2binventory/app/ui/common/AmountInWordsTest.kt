package com.b2binventory.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInWordsTest {

    @Test
    fun rupeesUseLakhAndCrore() {
        assertEquals("Seven Hundred Rupees", AmountWords.of(700.0, "INR"))
        assertEquals("One Rupee", AmountWords.of(1.0, "INR"))
        assertEquals("Twelve Lakh Thirty Four Thousand Five Hundred Sixty Seven Rupees and Fifty Paise",
            AmountWords.of(1_234_567.50, "INR"))
        assertEquals("Two Crore Rupees", AmountWords.of(20_000_000.0, "INR"))
        assertEquals("Zero Rupees", AmountWords.of(0.0, "INR"))
    }

    @Test
    fun otherCurrenciesUseThousandAndMillion() {
        assertEquals("One Million Two Hundred Thousand US Dollars and 5/100",
            AmountWords.of(1_200_000.05, "USD"))
    }
}
