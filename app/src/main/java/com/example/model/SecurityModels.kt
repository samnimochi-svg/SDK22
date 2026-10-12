package com.example.model

enum class RiskLevel(val label: String) {
    SAFE("安全"),
    WARNING("注意"),
    DANGEROUS("危険")
}

data class SecurityCheckItem(
    val title: String,
    val description: String,
    val level: RiskLevel,
    val isHighlighted: Boolean = false
)

data class SecurityAuditResult(
    val overallLevel: RiskLevel,
    val items: List<SecurityCheckItem>,
    val requiresExplicitConsent: Boolean,
    val summary: String
)
