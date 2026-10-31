package com.pandyabhavik.habittracker.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

enum class Rarity(val label: String, val odds: String) {
    COMMON("Common", "60%"),
    UNCOMMON("Uncommon", "25%"),
    RARE("Rare", "1 in 10"),
    HOLO("Holo", "1 in 20"),
    LEGENDARY("Legendary", "comebacks only"),
    SECRET("Secret", "unlock only"),
}

data class Cat(
    val id: String,
    val name: String,
    val breed: String,
    val rarity: Rarity,
    val line: String,
    /** How to unlock a secret cat. */
    val hint: String? = null,
)

/** Purrfect Days, Series 1: 24 cats. 17 come from packs, 6 are secret, 1 is Legendary. */
object Catalog {
    val all: List<Cat> = listOf(
        Cat("mango", "Mango", "Orange Tabby", Rarity.COMMON, "Fed. Walked. Unstoppable."),
        Cat("oreo", "Oreo", "Tuxedo", Rarity.COMMON, "Dressed for a Perfect Day."),
        Cat("patches", "Patches", "Calico", Rarity.COMMON, "Earned this nap."),
        Cat("pickles", "Pickles", "Grey Tabby", Rarity.COMMON, "Small steps. Big strut."),
        Cat("snowball", "Snowball", "White Cat", Rarity.COMMON, "Fresh out of the bath."),
        Cat("miso", "Miso", "Siamese", Rarity.UNCOMMON, "Self-care is a habit too."),
        Cat("blue", "Blue", "Russian Blue", Rarity.UNCOMMON, "If I fits, I sits. If I streaks, I streaks."),
        Cat("dumpling", "Dumpling", "Scottish Fold", Rarity.UNCOMMON, "Inner peace. Outer streak."),
        Cat("biscuit", "Biscuit", "Munchkin", Rarity.UNCOMMON, "Short legs. Long streak."),
        Cat("pumpkin", "Pumpkin", "Exotic Shorthair", Rarity.UNCOMMON, "Kitchen closed. Belly full."),
        Cat("thor", "Thor", "Maine Coon", Rarity.RARE, "Big cat. Bigger streak."),
        Cat("jaguar", "Jaguar", "Bengal", Rarity.RARE, "Pouncing on tomorrow."),
        Cat("dobby", "Dobby", "Sphynx", Rarity.RARE, "Cold outside. Hot streak."),
        Cat("mochi", "Mochi", "Japanese Bobtail", Rarity.RARE, "Clean paws, clean streak."),
        Cat("midnight", "Midnight", "Black Cat", Rarity.HOLO, "Night walks hit different."),
        Cat("ghost", "Ghost", "Turkish Angora", Rarity.HOLO, "Dreaming in 10k steps."),
        Cat("cosmo", "Cosmo", "Abyssinian", Rarity.HOLO, "Out of this world."),
        Cat("maneki", "Maneki", "Lucky Cat", Rarity.LEGENDARY, "Welcome back. Good fortune ahead.", "Win a 3-day comeback"),
        Cat("ragdoll", "Ragdoll", "Ragdoll", Rarity.SECRET, "Night owl, still perfect.", "Close the day after 11 PM"),
        Cat("persian", "Snow Queen", "Persian", Rarity.SECRET, "All done before lunch.", "All four closed before noon"),
        Cat("savannah", "Zuri", "Savannah", Rarity.SECRET, "Lighter. Faster. Fiercer.", "Hit a new lowest weight"),
        Cat("norwegian", "Bjorn", "Norwegian Forest", Rarity.SECRET, "Twenty thousand steps through the snow.", "20,000 steps in one day"),
        Cat("british", "Earl Grey", "British Shorthair", Rarity.SECRET, "A proper week. No shortcuts.", "7 Perfect Days, no swaps"),
        Cat("lion", "Leo", "Lion", Rarity.SECRET, "King of the month.", "A full Perfect Month"),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): Cat = byId.getValue(id)

    fun find(id: String?): Cat? = id?.let { byId[it] }

    val packCats: List<Cat> = all.filter { it.rarity in PACK_RARITIES }

    val SIZE = all.size

    private val PACK_RARITIES = setOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.HOLO)
}

object Packs {
    /** Out of 100: Common 60, Uncommon 25, Rare 10, Holo 5. */
    val ODDS = listOf(Rarity.COMMON to 60, Rarity.UNCOMMON to 25, Rarity.RARE to 10, Rarity.HOLO to 5)

    const val ODDS_TEXT = "Common 60% · Uncommon 25% · Rare 10% · Holo 5%"

    fun rollRarity(random: Random, atLeast: Rarity? = null): Rarity {
        val allowed = ODDS.filter { atLeast == null || it.first.ordinal >= atLeast.ordinal }
        val total = allowed.sumOf { it.second }
        var r = random.nextInt(total)
        for ((rarity, weight) in allowed) {
            if (r < weight) return rarity
            r -= weight
        }
        return allowed.last().first
    }

    /** One sticker from a pack. */
    fun roll(random: Random, atLeast: Rarity? = null): Cat {
        val rarity = rollRarity(random, atLeast)
        val pool = Catalog.packCats.filter { it.rarity == rarity }
        return pool[random.nextInt(pool.size)]
    }

    /** What a milestone streak guarantees: Rare or better at 7 and 14, Holo from 30. */
    fun milestoneGuarantee(streak: Int): Rarity? = when {
        streak !in Streaks.MILESTONES -> null
        streak < 30 -> Rarity.RARE
        else -> Rarity.HOLO
    }

    fun milestoneText(milestone: Int): String =
        if (milestone < 30) "a Rare or better sticker, guaranteed" else "a Holo sticker, guaranteed"
}

object Secrets {
    /**
     * The secret cats a Perfect Day on [today] unlocks. [days] holds the history including today,
     * with today's perfectAt set. [owned] are the cat ids already collected.
     */
    fun unlocked(
        days: Map<LocalDate, Day>,
        today: LocalDate,
        perfectAtLocal: LocalTime,
        owned: Set<String>,
    ): List<String> {
        val day = days[today] ?: return emptyList()
        if (day.perfectAt == null) return emptyList()
        val out = ArrayList<String>()
        if (perfectAtLocal >= LocalTime.of(23, 0)) out += "ragdoll"
        if (perfectAtLocal < LocalTime.NOON) out += "persian"
        val w = day.weightKg
        val earlier = days.filterKeys { it.isBefore(today) }.values.mapNotNull { it.weightKg }
        if (w != null && earlier.size >= 3 && w < earlier.min() - 0.01) out += "savannah"
        if (day.steps >= 20_000) out += "norwegian"
        val week = (0..6).map { days[today.minusDays(it.toLong())] }
        if (week.all { it != null && it.perfectAt != null && !it.savedWithSwaps }) out += "british"
        val month = YearMonth.from(today)
        if (today == month.atEndOfMonth() &&
            (1..month.lengthOfMonth()).all { days[month.atDay(it)]?.perfectAt != null }
        ) out += "lion"
        return out.filter { it !in owned }
    }
}
