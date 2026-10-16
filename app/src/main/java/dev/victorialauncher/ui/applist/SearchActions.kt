// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

/**
 * Something the search box can hand to another app for what was typed: a number to dial, a
 * link to open. Only ever started by a tap or by Go, never by typing alone, and each goes to an
 * app that shows what it is about to do — the dialler rather than a call, the clock app's own
 * timer screen — so nothing here acts on a misreading by itself.
 */
internal sealed interface SearchAction {
    /** [shown] as typed, for the label; [dial] as digits and a leading +, for the dialler. */
    data class Call(val shown: String, val dial: String) : SearchAction

    data class Text(val shown: String, val dial: String) : SearchAction

    /** [url] with a scheme, which a bare "example.com" has added for it. */
    data class OpenLink(val host: String, val url: String) : SearchAction

    data class Email(val address: String) : SearchAction

    data class Timer(val seconds: Int) : SearchAction

    data class Alarm(val hour: Int, val minute: Int) : SearchAction
}

/**
 * The actions [text] offers, in the order Go would take them; empty for most queries, and empty
 * rather than an exception for anything a parser trips over, since this runs on every keystroke.
 */
internal fun searchActions(text: String): List<SearchAction> =
    runCatching { actionsFor(text.trim()) }.getOrElse { emptyList() }

private fun actionsFor(input: String): List<SearchAction> {
    if (input.isEmpty() || input.length > MAX_ACTION_INPUT) return emptyList()
    timerSeconds(input)?.let { return listOf(SearchAction.Timer(it)) }
    alarmTime(input)?.let { (hour, minute) -> return listOf(SearchAction.Alarm(hour, minute)) }
    emailAddress(input)?.let { return listOf(SearchAction.Email(it)) }
    webAddress(input)?.let { (host, url) -> return listOf(SearchAction.OpenLink(host, url)) }
    phoneNumber(input)?.let { dial ->
        return listOf(SearchAction.Call(input, dial), SearchAction.Text(input, dial))
    }
    return emptyList()
}

private const val MAX_ACTION_INPUT = 200

// ---- Phone numbers ----

private const val PHONE_SEPARATORS = " -.()/"

/**
 * [text] as a number to dial — its digits, with a leading + kept — or null.
 *
 * Arithmetic and dates are made of the same characters, so a number broken up by separators has
 * to look like a phone number as well: starting the way a full number does (+, 0 or a bracketed
 * area code) or being long enough that nothing else is likely. That is what tells 06-12345678
 * from 1500-250 and 2024-10-15. Written solid, three digits are a short code like 112 and five or
 * more a number; four are left alone, being as often a year or a PIN or the name of a game.
 */
internal fun phoneNumber(text: String): String? {
    val t = text.trim()
    if (t.isEmpty() || t.length > 32) return null
    val dial = StringBuilder()
    var digits = 0
    var separators = 0
    var dots = 0
    for ((index, c) in t.withIndex()) {
        when {
            c in '0'..'9' -> {
                digits++
                dial.append(c)
            }
            c == '+' && index == 0 -> dial.append(c)
            c in PHONE_SEPARATORS -> {
                separators++
                if (c == '.') dots++
            }
            else -> return null
        }
    }
    if (digits !in 3..15) return null
    if (t.last() !in '0'..'9') return null
    if (DOUBLED_SEPARATOR.containsMatchIn(t)) return null
    // An area code is bracketed as digits alone, (020) or (0); a bracket around anything else, or
    // one divided by something, is a sum, as (100-20)/2 is.
    if (BRACKETED_SUM.containsMatchIn(t)) return null
    val opensLikeANumber = t[0] == '+' || t[0] == '(' || t[0] == '0'
    if (separators == 0) {
        if (!(t[0] == '+' || digits == 3 || digits >= 5)) return null
    } else {
        // A single point between digits is a decimal number, not a number to dial; and so is a
        // point after a lone 0 or beside a minus, as in 0.75-0.25 or 125.75-33.25, which are sums.
        if (dots == 1 && separators == 1) return null
        if (dots > 0 && ('-' in t || DECIMAL_FROM_ZERO.containsMatchIn(t))) return null
        // A router's address, as 192.168.178.1 is. No address in use starts with a 0, so a number
        // dotted into four groups from a 0, as 02.123.45.67 is in Brussels, is still dialled.
        if (t[0] != '0' && IPV4_SHAPE.matches(t)) return null
        // Without a +, a 0 or a bracket in front, a number is written in short groups, as
        // 555-123-4567 is; a run of five figures or more beside a separator is a sum, as
        // 100000 - 2500 and 1200000/12 are.
        if (!opensLikeANumber && LONG_RUN.containsMatchIn(t)) return null
        if (!(opensLikeANumber && digits >= 6) && digits < 9) return null
    }
    return dial.toString()
}

private val DOUBLED_SEPARATOR = Regex("[-./]{2}")

private val DECIMAL_FROM_ZERO = Regex("(?:^|[^0-9])0\\.[0-9]")

private val BRACKETED_SUM = Regex("\\([^)]*[^0-9 )]|\\)\\s*/")

private val IPV4_SHAPE = Regex("^[0-9]{1,3}(?:\\.[0-9]{1,3}){3}$")

private val LONG_RUN = Regex("[0-9]{5,}")

// ---- E-mail ----

private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$")

internal fun emailAddress(text: String): String? = text.trim().takeIf { EMAIL.matches(it) }

// ---- Links ----

/**
 * The generic top-level domains common enough to be worth recognising. Every two-letter one is a
 * country's and is let through as well; anything else that merely has a dot in it — a file name,
 * a version — is not a link someone meant to open.
 */
private val GENERIC_TLDS = setOf(
    "com", "org", "net", "edu", "gov", "mil", "int", "info", "biz", "name", "pro", "mobi",
    "io", "ai", "app", "dev", "page", "blog", "shop", "store", "online", "site", "tech", "xyz",
    "cloud", "news", "wiki", "live", "life", "world", "today", "top", "club", "art", "design",
    "studio", "media", "email", "energy", "travel", "museum", "aero", "asia", "eus", "cat",
)

/**
 * The domains a package name starts from, as com.google.android.gm does. Only these: news, app,
 * shop or blog lead a site's address as often as not, as in news.ycombinator.com.
 */
private val PACKAGE_ROOTS = setOf("com", "org", "net", "edu", "gov", "mil", "int", "io", "biz")

private val HOST_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

/**
 * The host and full address of a link, or null. A scheme is optional and only http(s) is
 * accepted; without one, https is assumed, as a browser's own address bar does.
 */
internal fun webAddress(text: String): Pair<String, String>? {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t) return null
    val lower = t.lowercase()
    val schemed = lower.startsWith("http://") || lower.startsWith("https://")
    if (!schemed && "://" in t) return null
    val rest = if (schemed) t.substringAfter("://") else t
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringBefore(':').lowercase()
    if (':' in authority) {
        val port = authority.substringAfter(':')
        if (port.isEmpty() || port.length > 5 || port.any { it !in '0'..'9' }) return null
    }
    val labels = host.split('.')
    if (labels.size < 2 || labels.any { !HOST_LABEL.matches(it) }) return null
    val tld = labels.last()
    if (tld.any { it !in 'a'..'z' }) return null
    if (tld.length != 2 && tld !in GENERIC_TLDS) return null
    // com.google.android.gm is a package name, which search also finds apps by, and not a site.
    if (labels.size >= 3 && labels.first() in PACKAGE_ROOTS) return null
    return host to (if (schemed) t else "https://$t")
}

// ---- Timers ----

private const val LONGEST_TIMER_SECONDS = 24 * 60 * 60

private val TIMER_FIRST = Regex("^(?:set (?:a )?)?timer (?:for )?(.+)$")
private val TIMER_LAST = Regex("^(?:a )?(.+?)[ -]timer$")
private val CLOCK_HMS = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$")
private val CLOCK_MS = Regex("^(\\d{1,3}):(\\d{2})$")

/** One figure and its unit, as in "1h", "30 min" or "1.5 hours"; the unit may be left off. */
private val DURATION_PART = Regex(
    "^(\\d+(?:[.,]\\d+)?)\\s*(hours|hour|hrs|hr|h|minutes|minute|mins|min|m|seconds|second|secs|sec|s)?(?![a-z])\\s*",
)

private val TIMER_SPACES = Regex("\\s+")

/**
 * How long a timer [text] asks for, in seconds, or null if it does not ask for one.
 *
 * "timer 10m", "10 min timer", "timer 1h30", "timer 5:00". A figure with no unit after an hour is
 * minutes and after a minute is seconds, which is how "1h30" is said; on its own it is minutes,
 * since that is what a kitchen timer is set in.
 */
internal fun timerSeconds(text: String): Int? {
    val said = text.trim().lowercase().replace(TIMER_SPACES, " ")
    val duration = (TIMER_FIRST.matchEntire(said) ?: TIMER_LAST.matchEntire(said))
        ?.groupValues?.get(1)?.trim() ?: return null
    val seconds = durationSeconds(duration) ?: return null
    return seconds.takeIf { it in 1..LONGEST_TIMER_SECONDS }
}

private fun durationSeconds(text: String): Int? {
    CLOCK_HMS.matchEntire(text)?.let { match ->
        val (h, m, s) = match.destructured
        if (m.toInt() > 59 || s.toInt() > 59) return null
        return h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
    }
    CLOCK_MS.matchEntire(text)?.let { match ->
        val (m, s) = match.destructured
        if (s.toInt() > 59) return null
        return m.toInt() * 60 + s.toInt()
    }
    // "10-minute timer" is said as often as "10 minute timer".
    var rest = text.replace(" and ", " ").replace('-', ' ').trim()
    var total = 0.0
    // Seconds in the unit of the part before; parts have to come largest first.
    var previous: Int? = null
    while (rest.isNotEmpty()) {
        val match = DURATION_PART.find(rest) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = when (match.groupValues[2]) {
            "hours", "hour", "hrs", "hr", "h" -> 3600
            "minutes", "minute", "mins", "min", "m" -> 60
            "seconds", "second", "secs", "sec", "s" -> 1
            else -> when (previous) {
                null -> 60
                3600 -> 60
                60 -> 1
                else -> return null
            }
        }
        if (previous != null && unit >= previous) return null
        total += amount * unit
        previous = unit
        rest = rest.substring(match.range.last + 1)
    }
    if (previous == null || total > LONGEST_TIMER_SECONDS) return null
    return Math.round(total).toInt()
}

/** "1 h 30 min", "10 min", "45 s": a timer's length as its label gives it. */
internal fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it h" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ")
}

// ---- Alarms ----

private val ALARM_FIRST = Regex(
    "^(?:set (?:an |the )?alarm (?:at |for )?|alarm (?:at |for )?|wake me (?:up )?(?:at )?)(.+)$",
)
private val ALARM_LAST = Regex("^(.+?) alarm$")

/** 7:30, 7.30, 7h30, 730, 6am, 6:15 pm. */
private val CLOCK_TIME = Regex("^(\\d{1,2})(?:[:.h](\\d{2})|(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$")

/**
 * The hour and minute an alarm [text] asks for, on a 24-hour clock, or null.
 *
 * "alarm 7:30", "alarm 6am", "wake me at 6:15". Without am or pm the hour is taken as written on
 * a 24-hour clock, so "alarm 6" is six in the morning.
 */
internal fun alarmTime(text: String): Pair<Int, Int>? {
    val said = text.trim().lowercase().replace(TIMER_SPACES, " ")
    val time = (ALARM_FIRST.matchEntire(said) ?: ALARM_LAST.matchEntire(said))
        ?.groupValues?.get(1)?.trim() ?: return null
    val match = CLOCK_TIME.matchEntire(time) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { "0" }.toInt()
    if (minute > 59) return null
    val half = match.groupValues[4]
    val hour24 = when {
        half.isEmpty() -> hour.takeIf { it in 0..23 } ?: return null
        hour !in 1..12 -> return null
        half.startsWith("p") -> hour % 12 + 12
        else -> hour % 12
    }
    return hour24 to minute
}

/** 07:30, the way an alarm's time is read back. */
internal fun formatClock(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
