// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.runtime.Composable
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPage
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPagesDialog
import io.github.rotundtapir.euchre.EuchreHouseRules

/**
 * The rules of Euchre *as this app implements them*, on cardkit's paged card-face reader. The house
 * rules section reflects [houseRules] so the text never describes a variant the current settings
 * have switched off.
 *
 * Deliberately no `narration` here, ever (same as 500's RulesDialog): the shared reader renders a
 * "♪" mute toggle for any NarrationState it is handed, and this dialog is reachable from a regular
 * game's settings cog — narration is a tutorial feature, and its mute must never surface where
 * nothing speaks. GameFlowTest pins this.
 */
@Composable
fun RulesDialog(houseRules: EuchreHouseRules, onDismiss: () -> Unit) {
    TutorialPagesDialog(
        pages = rulesPages(houseRules),
        nextTag = "rulesNext",
        finishLabel = "Close",
        finishTag = "rulesClose",
        onFinish = onDismiss,
        onDismiss = onDismiss,
        uniformBodyHeight = true,
    )
}

/**
 * The rules pages. Pure (a plain function of the house rules) so their wording can be checked
 * without a composition.
 */
fun rulesPages(houseRules: EuchreHouseRules): List<TutorialPage> = listOf(
    TutorialPage(
        "The game",
        "Euchre is a trick-taking game for four players in fixed partnerships — you and the " +
            "player opposite against the two beside you. First side to 10 points wins.\n\n" +
            "The deck is just 24 cards: 9, 10, J, Q, K, A in each suit. Everyone gets five, the " +
            "next card is turned face up, and the rest sits face down as the kitty.",
    ),
    TutorialPage(
        "Bowers — the twist",
        "Trumps do not rank the way you expect. The Jack of the trump suit is the highest card " +
            "in the game (the RIGHT BOWER). The other Jack of the same colour joins the trump " +
            "suit as the second highest (the LEFT BOWER) — it stops being a card of its printed " +
            "suit entirely.\n\n" +
            "With ♠ trump the order is J♠, J♣, A♠, K♠, Q♠, 10♠, 9♠. J♣ is a spade now: you may " +
            "not play it to follow clubs, and it beats every club anyway.\n\n" +
            "Non-trump suits rank plainly, A high down to 9.",
    ),
    TutorialPage(
        "Making trump",
        "Starting left of the dealer, each player may ORDER UP the turned card's suit as trump, " +
            "or pass. If someone orders it up, the dealer takes that card into hand and buries " +
            "one — so the dealer's side gains a known trump.\n\n" +
            "If all four pass, the card is turned down and a second round goes around: now each " +
            "player may name ANY suit except the one just turned down, or pass again.",
    ),
    TutorialPage(
        "Playing the hand",
        "The player to the dealer's left leads the first trick. You must follow the led suit if " +
            "you can — remembering that the left bower counts as a trump, not as its printed " +
            "suit. Otherwise play anything. Highest trump wins, or the highest card of the led " +
            "suit if no trump is played. The winner leads the next trick.",
    ),
    TutorialPage(
        "Scoring",
        "The side that made trump (the MAKERS) must win at least three of the five tricks.\n\n" +
            "• 3 or 4 tricks: 1 point\n" +
            "• all 5 tricks (a march): 2 points\n" +
            "• fewer than 3: the other side is said to have EUCHRED them and scores 2\n\n" +
            "Only one side ever scores in a hand.",
    ),
    TutorialPage(
        "Going alone",
        "When you make trump you may declare that you are going ALONE. Your partner lays their " +
            "cards down and sits the hand out, so you play three-handed against two.\n\n" +
            "A lone hand that takes all five tricks scores 4 instead of 2. Taking three or four " +
            "still scores just 1, and being euchred still gives the other side 2 — so it is a " +
            "bet on a very strong hand.",
    ),
    TutorialPage("House rules", houseRulesBody(houseRules)),
)

/** The house-rules page body, listing each toggle's effect and whether it is currently on. */
private fun houseRulesBody(houseRules: EuchreHouseRules): String {
    fun line(on: Boolean, name: String, description: String) =
        "${if (on) "ON" else "off"} — $name: $description"
    return "These are switched in Settings and apply to the next game you start.\n\n" +
        line(
            houseRules.stickTheDealer,
            "Stick the dealer",
            "in round two the dealer may not pass, so a hand is never thrown in.",
        ) + "\n\n" +
        line(
            houseRules.defendAlone,
            "Defend alone",
            "against a lone maker, a defender may go it alone too; euchring them then scores 4.",
        ) + "\n\n" +
        line(
            houseRules.bennyEnabled,
            "Benny (joker)",
            "a Joker joins the deck as the highest trump. Turned up, the dealer must name trump " +
                "and take it.",
        ) + "\n\n" +
        line(
            houseRules.farmersHand,
            "Farmer's hand",
            "a hand of nothing but nines and tens may swap three cards with the kitty before " +
                "bidding.",
        )
}
