package com.astrbot.control.ui.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.control.data.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 屏幕 ViewModel 基类：统一 loading / error / toast（均为 StateFlow，供 UI 收集） */
open class BaseVm(protected val api: ApiClient) : ViewModel() {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val toast = MutableStateFlow<String?>(null)

    fun clearError() { error.value = null }
    fun clearToast() { toast.value = null }
    fun showToast(msg: String) { toast.value = msg }

    /** 执行一次性操作（如按钮点击），错误写入 error */
    protected fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "发生错误"
            }
        }
    }

    /** 带 loading 转圈的加载操作 */
    protected fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "加载失败"
            } finally {
                loading.value = false
            }
        }
    }
}
