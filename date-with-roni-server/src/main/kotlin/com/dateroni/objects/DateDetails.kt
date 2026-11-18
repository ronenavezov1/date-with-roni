package com.dateroni.objects

import java.time.Instant

// ============================================================
//  הדייט שנקבע, כפי שהוא נקרא מ-Postgres.
//
//  whenText הוא מקור האמת — מה שהיא אמרה במילים שלה.
//  at הוא הפענוח, והוא לרוב null: "יום שלישי בערב" הוא זמן
//  ברור לשני בני אדם ולא לחותמת זמן.
// ============================================================

data class DateDetails(
    val scheduled: Boolean,
    val whenText: String?,
    val place: String?,
    val at: Instant?,
    val requestedAt: Instant?,
)
