package app.fireplan.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.fireplan.ui.chat.ChatViewModel
import app.fireplan.ui.goal.GoalEditorViewModel
import app.fireplan.ui.progress.ProgressViewModel
import app.fireplan.ui.settings.SettingsViewModel

class AppViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            ProgressViewModel::class.java -> ProgressViewModel(app)
            GoalEditorViewModel::class.java -> GoalEditorViewModel(app)
            ChatViewModel::class.java -> ChatViewModel(app)
            SettingsViewModel::class.java -> SettingsViewModel(app)
            else -> throw IllegalArgumentException("未知的 ViewModel：${modelClass.name}")
        } as T
    }
}
