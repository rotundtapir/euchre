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
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.ui.BotSetupScreen
import io.github.rotundtapir.euchre.ui.GameScreen
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
    // someone's saved settings.
    seedOverride: Long? = null,
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

    // The pacing settings live in the ViewModel (its gates read them as flows), so mirror them in.
    LaunchedEffect(settingsControls.animationSpeed) { vm.setAnimationSpeed(settingsControls.animationSpeed) }
    // A lesson forces the trick hold on so every completed trick waits to be explained.
    LaunchedEffect(settingsControls.holdTricks, activeLessonId) {
        vm.setHoldTricks(settingsControls.holdTricks || activeLessonId != null)
    }

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
                onAction = { action -> vm.submitHumanAction(action) },
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
                        seed = seedOverride ?: nextSeed(),
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
                onDismiss = { primerLessonId = null },
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

/** Persists only the toggles that actually changed, so one switch writes one key. */
private suspend fun SettingsRepository.applyHouseRules(from: EuchreHouseRules, to: EuchreHouseRules) {
    if (to.stickTheDealer != from.stickTheDealer) setStickTheDealer(to.stickTheDealer)
    if (to.defendAlone != from.defendAlone) setDefendAlone(to.defendAlone)
    if (to.bennyEnabled != from.bennyEnabled) setBennyEnabled(to.bennyEnabled)
    if (to.farmersHand != from.farmersHand) setFarmersHand(to.farmersHand)
}

/** Routes an action from the UI to the ViewModel's per-action funnels. */
private fun EuchreViewModel.submitHumanAction(action: EuchreAction) = when (action) {
    is EuchreAction.Pass -> passBid()
    is EuchreAction.OrderUp -> orderUp(action.alone)
    is EuchreAction.CallTrump -> callTrump(action.suit, action.alone)
    is EuchreAction.DealerDiscard -> discard(action.card)
    is EuchreAction.DefendAlone -> defendAlone()
    is EuchreAction.DeclineDefend -> declineDefend()
    is EuchreAction.CallFarmers -> callFarmers(action.discards)
    is EuchreAction.DeclineFarmers -> declineFarmers()
    is EuchreAction.PlayCard -> playCard(action.card)
}
