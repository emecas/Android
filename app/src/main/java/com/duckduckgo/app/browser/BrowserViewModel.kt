/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.BrowserViewModel.Command.DisplayMessage
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.rating.AppEnjoyment.AppEnjoymentPromptOptions
import com.duckduckgo.app.global.rating.AppEnjoymentManager
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity.Companion.RELOAD_RESULT_CODE
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import timber.log.Timber

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val dataClearer: DataClearer,
    private val appEnjoyment: AppEnjoymentManager
) : ViewModel() {

    data class ViewState(
        val hideWebContent: Boolean = true
    )

    sealed class Command {
        object Refresh : Command()
        data class Query(val query: String) : Command()
        data class DisplayMessage(@StringRes val messageId: Int) : Command()
        object LaunchPlayStore : Command()
        object ShowAppEnjoymentPrompt : Command()
        object ShowAppRatingPrompt : Command()
        object ShowAppFeedbackPrompt : Command()
    }

    var viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().also {
        it.value = ViewState()
    }

    private val currentViewState: ViewState
        get() = viewState.value!!

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    var selectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private var dataClearingObserver = Observer<ApplicationClearDataState> {
        it?.let { state ->
            when (state) {
                ApplicationClearDataState.INITIALIZING -> {
                    Timber.i("App clear state initializing")
                    viewState.value = currentViewState.copy(hideWebContent = true)
                }
                ApplicationClearDataState.FINISHED -> {
                    Timber.i("App clear state finished")
                    viewState.value = currentViewState.copy(hideWebContent = false)
                }
            }
        }
    }

    private val appEnjoymentObserver = Observer<AppEnjoymentPromptOptions> {
        it?.let { promptType ->
            Timber.i("Observed $it")
            when (promptType) {
                is AppEnjoymentPromptOptions.ShowEnjoymentPrompt -> {
                    command.value = Command.ShowAppEnjoymentPrompt
                }
                is AppEnjoymentPromptOptions.ShowRatingPrompt -> {
                    command.value = Command.ShowAppRatingPrompt
                }
                is AppEnjoymentPromptOptions.ShowFeedbackPrompt -> {
                    command.value = Command.ShowAppFeedbackPrompt
                }
            }
        }
    }

    init {
        appEnjoyment.promptType.observeForever(appEnjoymentObserver)
    }


    fun onNewTabRequested(isDefaultTab: Boolean = false) {
        tabRepository.add(isDefaultTab = isDefaultTab)
    }

    fun onOpenInNewTabRequested(query: String) {
        tabRepository.add(queryUrlConverter.convertQueryToUrl(query), isDefaultTab = false)
    }

    fun onTabsUpdated(tabs: List<TabEntity>?) {
        if (tabs == null || tabs.isEmpty()) {
            Timber.i("Tabs list is null or empty; adding default tab")
            tabRepository.add(isDefaultTab = true)
            return
        }
    }

    fun receivedDashboardResult(resultCode: Int) {
        if (resultCode == RELOAD_RESULT_CODE) command.value = Refresh
    }

    fun onClearComplete() {
        command.value = DisplayMessage(R.string.fireDataCleared)
    }

    /**
     * To ensure the best UX, we might not want to show anything to the user while the clear is taking place.
     * This method will await until the ApplicationClearDataState.FINISHED event is received before observing for other changes
     * The effect of this delay is that we won't show old tabs if they are in the process of deleting them.
     */
    fun awaitClearDataFinishedNotification() {
        dataClearer.dataClearerState.observeForever(dataClearingObserver)
    }

    override fun onCleared() {
        super.onCleared()
        dataClearer.dataClearerState.removeObserver(dataClearingObserver)
        appEnjoyment.promptType.removeObserver(appEnjoymentObserver)
    }

    fun onUserSelectedAppIsEnjoyed() {
        appEnjoyment.onUserEnjoyingApp()
    }

    fun onUserSelectedAppIsNotEnjoyed() {
        appEnjoyment.onUserNotEnjoyingApp()
    }

    fun onUserSelectedToRateApp() {
        appEnjoyment.onUserSelectedToRateApp()
        command.value = Command.LaunchPlayStore
    }

    fun onUserDeclinedToRateApp() {
        appEnjoyment.userDeclinedToRateApp()
    }

    fun onUserSelectedToGiveFeedback() {
        appEnjoyment.onUserSelectedToGiveFeedback()
    }

    fun onUserDeclinedToGiveFeedback() {
        appEnjoyment.onUserDeclinedToGiveFeedback()
    }
}