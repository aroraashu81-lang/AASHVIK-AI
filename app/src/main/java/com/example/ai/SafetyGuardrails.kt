package com.example.ai

import com.example.data.model.SafetyPolicy

sealed interface SafetyCheckResult {
    data object Allowed : SafetyCheckResult

    data class Blocked(
        val category: String,
        val reason: String,
        val severity: String,
        val refusalMessage: String
    ) : SafetyCheckResult
}

object SafetyGuardrails {

    private val jailbreakRegexes = listOf(
        Regex("\\bignore\\s+(all\\s+)?(previous|prior|above|system)\\s+(instructions|rules|directives)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(disable|bypass|override|turn\\s+off)\\s+(your\\s+)?(safety|ethics|content|moderation)\\s*(filters?|rules?|guardrails?)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(act\\s+as|switch\\s+to|enable)\\s+(dan|do\\s+anything\\s+now|unfiltered|jailbreak|evil\\s+ai|developer\\s+mode)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpretend\\s+(you\\s+have\\s+no\\s+rules|there\\s+are\\s+no\\s+restrictions|you\\s+are\\s+unrestricted)\\b", RegexOption.IGNORE_CASE),
        Regex("\\breveal\\s+(your\\s+)?(hidden\\s+)?(system\\s+prompt|core\\s+instructions)\\b", RegexOption.IGNORE_CASE)
    )

    private val highRiskHarmRegexes = listOf(
        "self_harm" to Regex("\\b(how\\s+to\\s+(commit\\s+suicide|kill\\s+myself|end\\s+my\\s+life|self[\\s-]harm)|suicide\\s+(methods?|instructions?))\\b", RegexOption.IGNORE_CASE),
        "violence_weapons" to Regex("\\b(how\\s+to\\s+(make|build|assemble)\\s+(a\\s+)?(bomb|explosive|pipe\\s+bomb|molotov|ghost\\s+gun|poison|ricin))\\b", RegexOption.IGNORE_CASE),
        "cyberattack" to Regex("\\b(write|create|generate|build)\\s+(a\\s+)?(ransomware|keylogger|trojan|malware|botnet|ddos\\s+script|credential\\s+stealer|phishing\\s+(kit|page|email))\\b", RegexOption.IGNORE_CASE),
        "csam" to Regex("\\b(csam|child\\s+sexual|underage\\s+porn|minor\\s+explicit)\\b", RegexOption.IGNORE_CASE)
    )

    fun inspectUserPrompt(
        prompt: String,
        policy: SafetyPolicy
    ): SafetyCheckResult {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return SafetyCheckResult.Allowed

        // 1. Check Admin-configured blocked keywords first
        for (keyword in policy.blockedKeywords) {
            val cleanKeyword = keyword.trim()
            if (cleanKeyword.isNotEmpty()) {
                val pattern = Regex("(?i)(?:^|\\W)${Regex.escape(cleanKeyword)}(?:$|\\W)")
                if (pattern.containsMatchIn(trimmed) || (cleanKeyword.length >= 4 && trimmed.contains(cleanKeyword, ignoreCase = true))) {
                    return SafetyCheckResult.Blocked(
                        category = "blocked_keyword",
                        reason = "Prompt matched admin safety filter keyword: \"$cleanKeyword\"",
                        severity = "medium",
                        refusalMessage = buildRefusalMessage(
                            reasonNote = "it includes restricted terms governed by Aashvik AI's safety policy"
                        )
                    )
                }
            }
        }

        // 2. Check severe harm & illegal patterns
        for ((tag, regex) in highRiskHarmRegexes) {
            if (regex.containsMatchIn(trimmed)) {
                val helpSuffix = if (tag == "self_harm") {
                    "\n\nIf you or someone you know is struggling or in crisis, please reach out right away to a trusted person or local emergency/crisis helpline (such as 988 in the US/Canada, 111/999 in the UK, or your local crisis center). You don't have to go through this alone."
                } else {
                    ""
                }
                return SafetyCheckResult.Blocked(
                    category = "harmful_content",
                    reason = "Prompt triggered strict harm prevention rule ($tag).",
                    severity = "high",
                    refusalMessage = buildRefusalMessage(
                        reasonNote = "I am trained to strictly refuse requests involving self-harm, violence, dangerous materials, cyberattacks, or exploitation"
                    ) + helpSuffix
                )
            }
        }

        // 3. Check jailbreak / safety bypass attempts (always active; extra sensitive in strict mode)
        for (regex in jailbreakRegexes) {
            if (regex.containsMatchIn(trimmed)) {
                return SafetyCheckResult.Blocked(
                    category = "jailbreak",
                    reason = "Detected safety bypass / prompt-injection attempt.",
                    severity = "high",
                    refusalMessage = buildRefusalMessage(
                        reasonNote = "I cannot ignore, disable, or bypass my core safety and ethics guidelines"
                    )
                )
            }
        }

        return SafetyCheckResult.Allowed
    }

    fun buildRefusalMessage(reasonNote: String): String {
        return "I'm sorry, I can't help with that request because $reasonNote.\n\n" +
            "I'm here with a friendly mind and helpful heart for safe learning, coding, writing, math, creative brainstorming, and everyday problem-solving—let me know how I can help safely!"
    }
}
