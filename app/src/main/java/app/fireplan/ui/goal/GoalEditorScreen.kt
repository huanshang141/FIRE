package app.fireplan.ui.goal

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fireplan.data.FireRepository
import app.fireplan.data.toInput
import app.fireplan.domain.FireInput
import app.fireplan.ui.LocalViewModelFactory
import app.fireplan.ui.formatPercentInput
import app.fireplan.ui.formatPlain
import app.fireplan.ui.parseNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GoalEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FireRepository.get(app)

    var netWorth by mutableStateOf("")
    var monthlyIncome by mutableStateOf("")
    var monthlyExpenses by mutableStateOf("")
    var withdrawalPercent by mutableStateOf("4")
    var returnPercent by mutableStateOf("5")
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var saving by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val goal = repo.getGoal()
            if (goal != null) {
                val input = goal.toInput()
                netWorth = formatPlain(input.netWorth)
                monthlyIncome = formatPlain(input.monthlyIncome)
                monthlyExpenses = formatPlain(input.monthlyExpenses)
                withdrawalPercent = formatPercentInput(input.withdrawalRate)
                returnPercent = formatPercentInput(input.annualReturn)
            }
            ready = true
        }
    }

    fun save(onSaved: () -> Unit) {
        val parsed = FireInput(
            netWorth = parseNumber(netWorth) ?: return invalid(),
            monthlyIncome = parseNumber(monthlyIncome) ?: return invalid(),
            monthlyExpenses = parseNumber(monthlyExpenses) ?: return invalid(),
            withdrawalRate = parseNumber(withdrawalPercent)?.div(100.0) ?: return invalid(),
            annualReturn = parseNumber(returnPercent)?.div(100.0) ?: return invalid(),
        )
        error = null
        saving = true
        viewModelScope.launch {
            try {
                repo.saveGoal(parsed)
                onSaved()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                this@GoalEditorViewModel.error = "保存失败：${error.message ?: "未知错误"}"
            } finally {
                saving = false
            }
        }
    }

    private fun invalid() {
        error = "请填写有效数字"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    onBack: () -> Unit,
    viewModel: GoalEditorViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("编辑目标") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "提取率默认 4%，预期年化实际收益率默认 5%。保存时会记一条净资产快照。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField("净资产（元）", viewModel.netWorth) { viewModel.netWorth = it }
            NumberField("月收入（元）", viewModel.monthlyIncome) { viewModel.monthlyIncome = it }
            NumberField("月支出（元）", viewModel.monthlyExpenses) { viewModel.monthlyExpenses = it }
            NumberField("安全提取率（%）", viewModel.withdrawalPercent) { viewModel.withdrawalPercent = it }
            NumberField("预期年化实际收益率（%）", viewModel.returnPercent) { viewModel.returnPercent = it }
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = viewModel.ready && !viewModel.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (viewModel.saving) "保存中" else "保存")
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
