package com.stfc.overlay.model

enum class StfcSection(
    val id: String,
    val title: String,
    val url: String,
    val description: String,
    val badgeColorHex: Long
) {
    HOME(
        id = "home",
        title = "Home / Search",
        url = "https://stfc.space/",
        description = "Global search across all STFC entities",
        badgeColorHex = 0xFF00E5FF
    ),
    SHIPS(
        id = "ships",
        title = "Ships",
        url = "https://stfc.space/ships",
        description = "Blueprints, tiers, scrap yields & component costs",
        badgeColorHex = 0xFF2979FF
    ),
    OFFICERS(
        id = "officers",
        title = "Officers",
        url = "https://stfc.space/officers",
        description = "Captain maneuvers, officer abilities, synergy & traits",
        badgeColorHex = 0xFFFFAB00
    ),
    HOSTILES(
        id = "hostiles",
        title = "Hostiles",
        url = "https://stfc.space/hostiles",
        description = "Hostile stats, loot tables & hunting locations",
        badgeColorHex = 0xFFFF5252
    ),
    SYSTEMS(
        id = "systems",
        title = "Systems",
        url = "https://stfc.space/systems",
        description = "Galaxy map, mining nodes, hazards & warp paths",
        badgeColorHex = 0xFF00E676
    ),
    RESEARCH(
        id = "research",
        title = "Research",
        url = "https://stfc.space/researches",
        description = "Research trees, requirements & buffs",
        badgeColorHex = 0xFFAA00FF
    ),
    MISSIONS(
        id = "missions",
        title = "Missions",
        url = "https://stfc.space/missions",
        description = "Mission chains, dilemmas & rewards",
        badgeColorHex = 0xFFFF9100
    ),
    BUILDINGS(
        id = "buildings",
        title = "Buildings",
        url = "https://stfc.space/buildings",
        description = "Station module upgrade costs & prerequisites",
        badgeColorHex = 0xFF1DE9B6
    ),
    SPOCKS_CLUB(
        id = "spocks_club",
        title = "Spock's Club",
        url = "https://spocks.club/",
        description = "Requirement calculators & officer/ship tools",
        badgeColorHex = 0xFFFFD600
    )
}

data class StfcBookmark(
    val title: String,
    val url: String,
    val category: String
)

object StfcPresets {
    val defaultBookmarks = listOf(
        StfcBookmark("Enterprise (G3 Epic)", "https://stfc.space/ships", "Ships"),
        StfcBookmark("Scrap Yard Guide", "https://stfc.space/buildings", "Buildings"),
        StfcBookmark("Pike / Moreau / Chen", "https://stfc.space/officers", "Officers"),
        StfcBookmark("Token & Deep Space Systems", "https://stfc.space/systems", "Systems")
    )
}
