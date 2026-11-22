package com.winter.mypass.feature.about.viewmodel

import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winter.mypass.feature.about.model.OpenSourceLibrary
import com.winter.mypass.feature.about.model.loadOpenSourceNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 开源清单读取状态。
 * @author Tingdong
 * @date 2026-11-22
 */
internal sealed interface OpenSourceState {
    data object Loading : OpenSourceState
    data object Failed : OpenSourceState
    data class Ready(val libraries: List<OpenSourceLibrary>) : OpenSourceState
}

/**
 * 在页面生命周期内加载并缓存开源清单。
 * @param resources 应用级资源
 * @author Tingdong
 * @date 2026-11-22
 */
internal class OpenSourceLicensesViewModel(private val resources: Resources) : ViewModel() {
    private val mutableState = MutableStateFlow<OpenSourceState>(OpenSourceState.Loading)
    val state = mutableState.asStateFlow()
    private var loading: Job? = null

    init { load() }

    /** 加载或重试读取，避免重复加载。 */
    fun load() {
        if (loading?.isActive == true || mutableState.value is OpenSourceState.Ready) return
        mutableState.value = OpenSourceState.Loading
        loading = viewModelScope.launch {
            try {
                mutableState.value = OpenSourceState.Ready(loadOpenSourceNotices(resources))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("OpenSourceLicenses", "Cannot read bundled open source notices", error)
                mutableState.value = OpenSourceState.Failed
            }
        }
    }
}
