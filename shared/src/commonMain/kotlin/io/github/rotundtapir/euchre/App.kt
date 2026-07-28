// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.AppConfig
import io.github.rotundtapir.cardkit.ui.LocalAppConfig
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPagesDialog
import io.github.rotundtapir.euchre.ui.BotSetupScreen
import io.github.rotundtapir.euchre.ui.GameScreen
import io.github.rotundtapir.euchre.ui.HOUSE_RULE_ROWS
import io.github.rotundtapir.euchre.ui.HomeScreen
import io.github.rotundtapir.euchre.ui.RulesDialog
import io.github.rotundtapir.euchre.ui.SettingsControls
import io.github.rotundtapir.euchre.ui.tutorial.EuchreTutorialSession
import io.github.rotundtapir.euchre.ui.tutorial.LessonPickerDialog
import io.github.rotundtapir.euchre.ui.tutorial.TutorialLesson
import io.github.rotundtapir.euchre.ui.tutorial.tutorialLesson
import io.github.rotundtapir.euchre.ui.tutorial.tutorialLessons
import kotlinx.coroutines.launch

/** Top-level screens the app switches between. */
enum class AppScreen { HOME, BOT_SETUP, GAME }

/**
 * The whole game UI, shared by every entry point (the Android activity, the browser). Each entry
 * point supplies its platform pieces: a [Monetization] implementation, a [SettingsRepository]
 * backend, the [AppConfig] values AGP's BuildConfig used to carry, a seed source, and the optional
 * overrides (intent extras on Android, URL parameters on web) that pin a game for tests.
 */
@Composable
fun EuchreApp(
    monetization: Monetization,
    settings: SettingsRepository,
    appConfig: AppConfig,
    nextSeed: () -> Long,
    // Injected as a parameter (an explicit dependency) rather than fetched inside the body. The
    // default keeps the wasm-safe explicit initializer — a bare viewModel() uses the reflection
    // factory, which is JVM-only and throws on wasm.
    vm: EuchreViewModel = viewModel { EuchreViewModel() },
    // Test overrides. In-memory only, never persisted: a shared link must not be able to change
    // someone's saved settings. (A pinned seed is one of these too, folded into [nextSeed] by the
    // entry point — one seam for "where does a new game's seed come from", not two.)
    animationSpeedOverride: AnimationSpeed? = null,
    soundVolumeOverride: Float? = null,
    botSkillOverride: BotSkill? = null,
    aiBudgetMillisOverride: Long? = null,
) {
    // Saveable so an in-progress game survives activity recreation; the game itself lives in the
    // ViewModel. (On web this degrades to remember {}.)
    var appScreen by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val view by vm.humanView.collectAsState()
    val scope = rememberCoroutineScope()

    // The interactive "How to play": a lesson is picked, its primer read, then its scripted hand is
    // dealt through the normal wiring. The step index is saveable so an activity recreation
    // mid-lesson resumes at the same point in the script.
    var showLessonPicker by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var primerLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var lessonStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeLesson = activeLessonId?.let(::tutorialLesson)

    val settingsControls = rememberSettingsControls(
        settings = settings,
        scope = { block -> scope.launch { block() } },
        animationSpeedOverride = animationSpeedOverride,
        soundVolumeOverride = soundVolumeOverride,
        botSkillOverride = botSkillOverride,
    )
    // One sound engine for the whole app: reacts to game-state transitions and hands back the play
    // function the dealing animation's sound hook uses for shuffle/deal effects.
    val playSound = rememberEuchreSoundEffects(view = view, volume = settingsControls.soundVolume)

    // A lesson forces the trick hold on so every completed trick waits to be explained. Decided
    // once, here: the ViewModel's gates and the felt that raises their signal must agree, and two
    // separate derivations of "is the hold on" could disagree and wedge the game.
    val holdTricks = settingsControls.holdTricks || activeLessonId != null

    // The pacing settings live in the ViewModel (its gates read them as flows), so mirror them in.
    LaunchedEffect(settingsControls.animationSpeed) { vm.setAnimationSpeed(settingsControls.animationSpeed) }
    LaunchedEffect(holdTricks) { vm.setHoldTricks(holdTricks) }

    val startLesson: (TutorialLesson) -> Unit = { lesson ->
        // The script depends on the exact table: pinned seed, pinned dealer, pinned house rules and
        // the standard bots. None of the player's own settings may reach it.
        vm.newGame(
            seed = lesson.seed,
            houseRules = lesson.pinnedRules,
            botSkill = BotSkill.STANDARD,
            firstDealer = lesson.dealer,
        )
        lessonStepIndex = 0
        primerLessonId = null
        activeLessonId = lesson.id
        appScreen = AppScreen.GAME.name
    }

    CompositionLocalProvider(LocalAppConfig provides appConfig) {
        // A single HomeScreen call also covers the first frame after newGame() (screen set to GAME,
        // view still null), so its internal state survives the transition instead of being rebuilt.
        val current = view
        when {
            appScreen == AppScreen.GAME.name && current != null -> GameScreen(
                view = current,
                botNames = vm.botNames,
                settings = settingsControls,
                monetization = monetization,
                holdTricks = holdTricks,
                onAction = vm::submitHumanAction,
                onExit = {
                    activeLessonId = null
                    appScreen = AppScreen.HOME.name
                },
                onResultDismiss = vm.pacing::acknowledgeHandResult,
                onDealAnimationFinish = vm.pacing::dealAnimationFinished,
                onTrickAcknowledge = vm.pacing::acknowledgeTrick,
                soundHook = playSound,
                tutorial = activeLesson?.let { lesson ->
                    remember(lesson, lessonStepIndex) {
                        EuchreTutorialSession(
                            lesson = lesson,
                            stepIndex = lessonStepIndex,
                            onAdvance = { lessonStepIndex++ },
                            onFinish = {
                                scope.launch { settings.setLessonDone(lesson.id, true) }
                                activeLessonId = null
                                appScreen = AppScreen.HOME.name
                            },
                        )
                    }
                },
            )
            appScreen == AppScreen.BOT_SETUP.name -> BotSetupScreen(
                settings = settingsControls,
                onStart = {
                    vm.newGame(
                        seed = nextSeed(),
                        houseRules = settingsControls.houseRules,
                        botSkill = settingsControls.botSkill,
                        aiBudgetMillis = aiBudgetMillisOverride,
                    )
                    appScreen = AppScreen.GAME.name
                },
                onBack = { appScreen = AppScreen.HOME.name },
            )
            else -> HomeScreen(
                monetization = monetization,
                settings = settingsControls,
                onPlayWithBots = { appScreen = AppScreen.BOT_SETUP.name },
                onHowToPlay = { showLessonPicker = true },
            )
        }

        if (showLessonPicker) {
            LessonPickerDialog(
                lessonsDone = rememberLessonsDone(settings),
                onSelect = { lesson ->
                    showLessonPicker = false
                    primerLessonId = lesson.id
                },
                onReadRules = {
                    showLessonPicker = false
                    showRules = true
                },
                onDismiss = { showLessonPicker = false },
            )
        }
        if (showRules) {
            RulesDialog(houseRules = settingsControls.houseRules, onDismiss = { showRules = false })
        }
        primerLessonId?.let(::tutorialLesson)?.let { lesson ->
            TutorialPagesDialog(
                pages = lesson.prologue,
                nextTag = "tutorialPrimerNext",
                finishLabel = "Deal",
                finishTag = "tutorialPrimerStart",
                onFinish = { startLesson(lesson) },
                // Backing out of a primer returns to the picker rather than leaving the tutorial:
                // changing your mind about which lesson to take shouldn't cost a trip via Home.
                onDismiss = {
                    primerLessonId = null
                    showLessonPicker = true
                },
                dismissLabel = "Back",
                uniformBodyHeight = true,
            )
        }
    }
}

/** Which lessons the player has already finished, collected once for the picker. */
@Composable
private fun rememberLessonsDone(settings: SettingsRepository): Set<String> =
    tutorialLessons.filter { lesson ->
        settings.lessonDone(lesson.id).collectAsState(initial = false).value
    }.map { it.id }.toSet()

/**
 * Collects every persisted setting and bundles it with its write-through callback. Test overrides
 * win over the stored value for this session only and are never written back.
 */
@Composable
private fun rememberSettingsControls(
    settings: SettingsRepository,
    scope: (suspend () -> Unit) -> Unit,
    animationSpeedOverride: AnimationSpeed?,
    soundVolumeOverride: Float?,
    botSkillOverride: BotSkill?,
): SettingsControls {
    val persistedSpeed by settings.animationSpeed.collectAsState(initial = SettingsDefaults.ANIMATION_SPEED)
    val sortByDefault by settings.sortHandByDefault.collectAsState(initial = SettingsDefaults.SORT_HAND_BY_DEFAULT)
    val holdTricks by settings.holdTricks.collectAsState(initial = SettingsDefaults.HOLD_TRICKS)
    val persistedVolume by settings.soundVolume.collectAsState(initial = SettingsDefaults.SOUND_VOLUME)
    val persistedBotSkill by settings.botSkill.collectAsState(initial = SettingsDefaults.BOT_SKILL)
    val stickTheDealer by settings.stickTheDealer.collectAsState(initial = SettingsDefaults.STICK_THE_DEALER)
    val defendAlone by settings.defendAlone.collectAsState(initial = SettingsDefaults.DEFEND_ALONE)
    val bennyEnabled by settings.bennyEnabled.collectAsState(initial = SettingsDefaults.BENNY_ENABLED)
    val farmersHand by settings.farmersHand.collectAsState(initial = SettingsDefaults.FARMERS_HAND)

    val animationSpeed = animationSpeedOverride ?: persistedSpeed
    val houseRules = EuchreHouseRules(stickTheDealer, defendAlone, bennyEnabled, farmersHand)
    return SettingsControls(
        animationSpeed = animationSpeed,
        onCycleAnimationSpeed = { scope { settings.setAnimationSpeed(animationSpeed.next()) } },
        sortByDefault = sortByDefault,
        onSetSortByDefault = { value -> scope { settings.setSortHandByDefault(value) } },
        holdTricks = holdTricks,
        onSetHoldTricks = { value -> scope { settings.setHoldTricks(value) } },
        soundVolume = soundVolumeOverride ?: persistedVolume,
        onSetSoundVolume = { value -> scope { settings.setSoundVolume(value) } },
        botSkill = botSkillOverride ?: persistedBotSkill,
        onSetBotSkill = { value -> scope { settings.setBotSkill(value) } },
        houseRules = houseRules,
        onSetHouseRules = { value -> scope { settings.applyHouseRules(houseRules, value) } },
    )
}

/**
 * Persists only the toggles that actually changed, so one switch writes one key. Driven by the same
 * [HOUSE_RULE_ROWS] table the switches are rendered from, so adding a house rule is one entry.
 */
private suspend fun SettingsRepository.applyHouseRules(from: EuchreHouseRules, to: EuchreHouseRules) {
    HOUSE_RULE_ROWS.forEach { row ->
        val value = row.read(to)
        if (value != row.read(from)) row.persist(this, value)
    }
}
