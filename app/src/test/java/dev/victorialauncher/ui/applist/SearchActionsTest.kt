// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a number, a link, an address, a timer or an alarm out of what was typed. */
class SearchActionsTest {

    @Test
    fun `a phone number offers to call it and to text it`() {
        assertEquals(
            listOf(SearchAction.Call("0612345678", "0612345678"), SearchAction.Text("0612345678", "0612345678")),
            searchActions("0612345678"),
        )
        assertEquals(
            listOf(
                SearchAction.Call("+31 6 1234 5678", "+31612345678"),
                SearchAction.Text("+31 6 1234 5678", "+31612345678"),
            ),
            searchActions(" +31 6 1234 5678 "),
        )
    }

    @Test
    fun `phone numbers are dialled as their digits`() {
        assertEquals("112", phoneNumber("112"))
        assertEquals("911", phoneNumber("911"))
        assertEquals("0612345678", phoneNumber("06-12345678"))
        assertEquals("0201234567", phoneNumber("(020) 123 4567"))
        assertEquals("0201234567", phoneNumber("020/1234567"))
        assertEquals("0612345678", phoneNumber("06.12.34.56.78"))
        assertEquals("+442079460958", phoneNumber("+44 20 7946 0958"))
        assertEquals("5551234567", phoneNumber("555-123-4567"))
        assertEquals("18005551234", phoneNumber("1-800-555-1234"))
        assertEquals("12345", phoneNumber("12345"))
        assertEquals("+15551234567", phoneNumber("+1 (555) 123-4567"))
        assertEquals("0475123456", phoneNumber("0475 / 12 34 56"))
        assertEquals("0201234567", phoneNumber("020 - 123 4567"))
        assertEquals("021234567", phoneNumber("02.123.45.67"))
        assertNotNull(phoneNumber("+44 (0)20 7946 0958"))
    }

    @Test
    fun `sums, dates, years and decimals are not phone numbers`() {
        listOf(
            "1500-250", "2024-10-15", "100-20", "3.14159265", "1234", "2048", "+1", "1,5",
            "06--1234567", "0612345678)", "06 1234 abc", "12", "",
        ).forEach { assertNull(it, phoneNumber(it)) }
    }

    @Test
    fun `sums with brackets or long figures are not phone numbers`() {
        listOf("(100-20)/2", "(250 - 50) / 4", "100000 - 2500", "100000-2500", "1200000/12").forEach {
            assertNull(it, phoneNumber(it))
        }
    }

    @Test
    fun `a router's address is not a phone number`() {
        listOf("192.168.178.1", "192.168.1.254", "172.16.10.10", "255.255.255.0").forEach {
            assertNull(it, phoneNumber(it))
        }
    }

    @Test
    fun `a link opens in the browser, made https when it has no scheme`() {
        assertEquals(listOf(SearchAction.OpenLink("example.com", "https://example.com/x")), searchActions("example.com/x"))
        assertEquals("smulders.be" to "https://smulders.be", webAddress("smulders.be"))
        assertEquals("www.smulders.com" to "https://www.smulders.com", webAddress("www.smulders.com"))
        assertEquals("example.com" to "https://example.com/a?b=1", webAddress("https://example.com/a?b=1"))
        assertEquals("example.com" to "HTTP://Example.COM", webAddress("HTTP://Example.COM"))
        assertEquals("sub.example.co.uk" to "https://sub.example.co.uk/page", webAddress("sub.example.co.uk/page"))
        assertEquals("example.com" to "https://example.com:8080/x", webAddress("example.com:8080/x"))
    }

    @Test
    fun `things with a dot in them are not all links`() {
        listOf(
            "file.txt", "version1.2", "com.google.android.gm", "com.whatsapp", "e.g.", "i.e", "a.b",
            "example.com:80a", "ftp://x.com", "1.2.3.4", "-bad.com", "exa_mple.com", "google.com/search?q=a b",
            "Spotify",
        ).forEach { assertNull(it, webAddress(it)) }
    }

    @Test
    fun `an address offers an email`() {
        assertEquals(
            listOf(SearchAction.Email("jan.peeters+work@smulders.com")),
            searchActions("jan.peeters+work@smulders.com"),
        )
        listOf("a@b", "@b.com", "a b@c.com", "a@b.c").forEach { assertNull(it, emailAddress(it)) }
    }

    @Test
    fun `a timer, however it is asked for`() {
        assertEquals(listOf(SearchAction.Timer(600)), searchActions("timer 10m"))
        assertEquals(5400, timerSeconds("timer 1h30"))
        assertEquals(600, timerSeconds("10 min timer"))
        assertEquals(300, timerSeconds("timer 5:00"))
        assertEquals(600, timerSeconds("timer 10"))
        assertEquals(90, timerSeconds("timer 90s"))
        assertEquals(5400, timerSeconds("timer 1h 30m"))
        assertEquals(5400, timerSeconds("timer 1 hour and 30 minutes"))
        assertEquals(1200, timerSeconds("set a timer for 20 minutes"))
        assertEquals(600, timerSeconds("a 10 minute timer"))
        assertEquals(600, timerSeconds("10-minute timer"))
        assertEquals(5400, timerSeconds("timer 1:30:00"))
        assertEquals(5400, timerSeconds("timer 1.5h"))
        assertEquals(150, timerSeconds("timer 2,5 min"))
        assertEquals(180, timerSeconds("Timer 3 Min"))
        assertEquals(90, timerSeconds("timer 1m30"))
    }

    @Test
    fun `timers nobody could set are not offered`() {
        listOf(
            "timer", "timer 0", "timer 25h", "timer 10 months", "timer 30s10", "timer 5:75", "timer 1h2h",
            "timer app", "timers",
        ).forEach { assertNull(it, timerSeconds(it)) }
    }

    @Test
    fun `a timer is labelled with its length`() {
        assertEquals("10 min", formatDuration(600))
        assertEquals("1 h 30 min", formatDuration(5400))
        assertEquals("1 min 30 s", formatDuration(90))
        assertEquals("45 s", formatDuration(45))
        assertEquals("2 h", formatDuration(7200))
    }

    @Test
    fun `an alarm, however it is asked for`() {
        assertEquals(listOf(SearchAction.Alarm(7, 30)), searchActions("alarm 7:30"))
        assertEquals(6 to 0, alarmTime("alarm 6am"))
        assertEquals(6 to 15, alarmTime("wake me at 6:15"))
        assertEquals(18 to 45, alarmTime("alarm 18:45"))
        assertEquals(18 to 30, alarmTime("alarm 6:30 pm"))
        assertEquals(0 to 0, alarmTime("alarm 12am"))
        assertEquals(12 to 0, alarmTime("alarm 12pm"))
        assertEquals(7 to 30, alarmTime("alarm 730"))
        assertEquals(7 to 30, alarmTime("alarm 0730"))
        assertEquals(7 to 0, alarmTime("alarm at 7"))
        assertEquals(6 to 45, alarmTime("set an alarm for 6.45"))
        assertEquals(7 to 0, alarmTime("wake me up at 7am"))
        assertEquals(7 to 30, alarmTime("7:30 alarm"))
        assertEquals(7 to 30, alarmTime("alarm 7h30"))
        assertEquals(6 to 0, alarmTime("alarm 6 a.m."))
    }

    @Test
    fun `times that are not on a clock are not offered`() {
        listOf("alarm", "alarm clock", "alarm 25:00", "alarm 7:60", "alarm 13pm", "alarms 7").forEach {
            assertNull(it, alarmTime(it))
        }
    }

    @Test
    fun `an alarm is read back on a 24-hour clock`() {
        assertEquals("07:30", formatClock(7, 30))
        assertEquals("18:05", formatClock(18, 5))
        assertEquals("00:00", formatClock(0, 0))
    }

    @Test
    fun `most queries offer nothing`() {
        listOf(
            "Spotify", "maps", "2048", "g", "", "200*15%", "10 km", "plate 2000x1000x20", "1500-250",
            "(100-20)/2", "192.168.178.1",
        ).forEach {
            assertTrue(it, searchActions(it).isEmpty())
        }
    }
}
