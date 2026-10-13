package app.fireplan.ui.progress

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fireplan.data.FireRepository
import app.fireplan.data.db.GoalEntity
import app.fireplan.data.db.NetWorthSnapshotEntity
import app.fireplan.data.toInput
import app.fireplan.domain.FireCalculator
import app.fireplan.domain.FireOutlook
import app.fireplan.domain.FireProjection
import app.fireplan.ui.LocalViewModelFactory
import app.fireplan.ui.NetWorthSparkline
import app.fireplan.ui.formatDate
import app.fireplan.ui.formatPercent
import app.fireplan.ui.formatYuan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUi(
    val loading: Boolean = true,
    val goal: GoalEntity? = null,
    val projection: FireProjection? = null,
    val snapshots: List<NetWorthSnapshotEntity> = emptyList(),
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FireRepository.get(app)
    val ui: StateFlow<ProgressUi> = combine(repo.observeGoal(), repo.observeSnapshots()) { goal, snapshots ->
        ProgressUi(
            loading = false,
            goal = goal,
            projection = goal?.let { FireCalculator.calculate(it.toInput()) },
            snapshots = snapshots,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())
}

@Composable
fun ProgressScreen(
    onEdit: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val ui by viewModel.ui.collectAsState()
    val goal = ui.goal
    val projection = ui.projection
    when {
        ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        goal == null || projection == null -> EmptyProgress(onEdit)
        else -> ProgressBody(goal, projection, ui.snapshots, onEdit)
    }
}

@Composable
private fun EmptyProgress(onEdit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("还没有目标", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "填写净资产、月收入、月支出、安全提取率和预期年化收益率后，这里会算出 FIRE 数字、储蓄率和预计日期。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEdit) { Text("去填写目标") }
    }
}

@Composable
private fun ProgressBody(
    goal: GoalEntity,
    projection: FireProjection,
    snapshots: List<NetWorthSnapshotEntity>,
    onEdit: () -> Unit,
) {
    val fraction = (projection.progress ?: 0.0).coerceIn(0.0, 1.0).toFloat()
    val remaining = projection.fireNumber?.let { (it - goal.netWorth).coerceAtLeast(0.0) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(168.dp),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                formatPercent(fraction.toDouble()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        StatCard("FIRE 数字", projection.fireNumber?.let(::formatYuan) ?: "无法计算")
        StatCard(
            "还差",
            when {
                projection.outlook == FireOutlook.REACHED -> "0 元"
                remaining != null -> formatYuan(remaining)
                else -> "无法计算"
            },
        )
        StatCard(
            "储蓄率",
            projection.savingsRate?.let(::formatPercent) ?: "年收入为 0，不显示储蓄率",
        )
        StatCard("预计日期", dateLine(projection))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("净资产", style = MaterialTheme.typography.titleMedium)
                if (snapshots.isEmpty()) {
                    Text("保存目标后会在这里留下折线。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    NetWorthSparkline(snapshots.map { it.netWorth })
                    Text(
                        "${snapshots.size} 个快照 · 最近 ${formatYuan(snapshots.last().netWorth)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("编辑目标") }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun dateLine(projection: FireProjection): String = when (projection.outlook) {
    FireOutlook.REACHED -> "已经达成 · ${projection.targetDate?.let(::formatDate) ?: "今天"}"
    FireOutlook.PROJECTED -> projection.targetDate?.let(::formatDate) ?: "无法计算"
    FireOutlook.UNREACHABLE -> "按当前数字无法达成"
    FireOutlook.UNDEFINED -> "无法计算"
}
