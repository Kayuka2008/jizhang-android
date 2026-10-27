package com.jizhang.app.ui.add

import com.jizhang.app.ui.glass.liquidSurface
import com.jizhang.app.ui.glass.LocalLiquidScene

import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.verticalScrollWithBoundaryHaptics

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.jizhang.app.ui.common.scrollBoundaryHaptics
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidButton as Button
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jizhang.app.ui.common.AnimateContentChanges
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.ui.common.ATTR_ON_PRIMARY
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.ATTR_PRIMARY
import com.jizhang.app.ui.common.FlipState
import com.jizhang.app.ui.common.flipContent
import com.jizhang.app.ui.common.rememberFlipState
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 记一笔 / 编辑账目 —— 纯 Compose（miuix）版（v1.53）。
 *
 * 原 activity_add_transaction.xml + item_picker_cell.xml + PickerAdapter 由本 Screen
 * 的 LazyVerticalGrid 取代（网格选中态用 Compose 直接画圆形底，不再靠
 * GradientDrawable 在 bind 里现造背景）。
 *
 * ★ 普通竖屏保留原有布局；实际高度不足时切换为可滚动表单。
 *   自上而下：顶栏 / 金额卡 / 分类面板 / 信息栏 / 键盘。
 *   - 金额卡与分类面板之间留 10dp 页面底色（两块圆角面宽度不同，贴身会像叠一起）。
 *   - 分类面板用「比页面底色高一档」的容器色 + 20dp 圆角（原 bg_category_panel）。
 *   - 键盘面板顶部两角 24dp、底部直角（原 bg_keypad_panel），键高 50dp、键间距 2dp。
 *
 * ★ 业务（金额解析、分类/账户校验、写库、弹窗）全部留在 Activity。
 *   这里只消费：[AddUiState] + 一串回调。
 */

/** 类型分段按钮：支出 / 收入 / 转账，与旧版 TxKind 一一对应。 */
internal enum class AddKindTab { EXPENSE, INCOME, TRANSFER }

/** 分类网格的一格。id < 0 表示特殊入口（「管理分类」）。 */
internal data class AddCategoryCell(
    val id: Long,
    val name: String,
    val iconRes: Int,
    val special: Boolean,
)

/** 页面完整快照（Activity 每次变化重建一份，Compose 只读）。 */
internal data class AddUiState(
    val kind: AddKindTab,
    val amountText: String,
    val categories: List<AddCategoryCell>,
    val selectedCategoryId: Long?,
    val categoryEmpty: Boolean,
    val dateText: String,
    val noteText: String,
    val noteIsHint: Boolean,
    val accountText: String,
    val toAccountText: String,
    val canSave: Boolean,
    val editing: Boolean,
)

@Composable
internal fun AddTransactionScreen(
    state: AddUiState,
    onClose: () -> Unit,
    onPickKind: (AddKindTab) -> Unit,
    onDelete: () -> Unit,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onPickDate: () -> Unit,
    onEditNote: () -> Unit,
    onPickAccount: () -> Unit,
    onPickToAccount: () -> Unit,
    onPickCategory: (AddCategoryCell) -> Unit,
) {
    val kindFlip = rememberFlipState()
    AnimateContentChanges(state.kind, kindFlip)
    val form: @Composable ColumnScope.(Boolean) -> Unit = { scrollable ->
        TransactionForm(
            state = state,
            kindFlip = kindFlip,
            scrollable = scrollable,
            onPickDate = onPickDate,
            onEditNote = onEditNote,
            onPickAccount = onPickAccount,
            onPickToAccount = onPickToAccount,
            onPickCategory = onPickCategory,
        )
    }
    val keypad: @Composable () -> Unit = {
        Keypad(state, onKey, onClear, onSave, onPickDate)
    }
    SecondaryPage(
        headerHeight = 56.dp,
        header = { statusBarHeight ->
            TopBar(
                kind = state.kind,
                editing = state.editing,
                statusBarHeight = statusBarHeight,
                onClose = onClose,
                onPickKind = { tab ->
                    kindFlip.goTo(
                        AddKindTab.entries.indexOf(tab) - AddKindTab.entries.indexOf(state.kind),
                    )
                    onPickKind(tab)
                },
                onDelete = onDelete,
            )
        },
    ) { topInset ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bottomInset = rememberAdaptiveInsets().bottom
            val contentHeight = (maxHeight - topInset).coerceAtLeast(0.dp)
            val keypadHeight = KEYPAD_CONTENT_HEIGHT + bottomInset
            val keypadWidth = (maxWidth * .42f).coerceIn(256.dp, 360.dp)
            // Reserve a usable category row before the regular weighted panel collapses.
            val formHeight = 120.dp + 62.dp + 96.dp +
                if (state.kind == AddKindTab.TRANSFER) 46.dp else 0.dp
            val compact = contentHeight < formHeight + keypadHeight

            when {
                !compact -> Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(topInset))
                    form(false)
                    keypad()
                }

                maxWidth >= 600.dp && contentHeight >= keypadHeight -> {
                    Row(Modifier.fillMaxSize().padding(top = topInset)) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .verticalScrollWithBoundaryHaptics()
                                .padding(bottom = bottomInset),
                        ) {
                            form(true)
                        }
                        Column(
                            Modifier.width(keypadWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            keypad()
                        }
                    }
                }

                contentHeight >= keypadHeight + 112.dp -> {
                    Column(Modifier.fillMaxSize().padding(top = topInset)) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth()
                                .verticalScrollWithBoundaryHaptics(),
                        ) {
                            form(true)
                        }
                        keypad()
                    }
                }

                else -> {
                    // Tiny windows keep every key at its natural height and allow reaching Save.
                    Column(Modifier.fillMaxSize().verticalScrollWithBoundaryHaptics()) {
                        Spacer(Modifier.height(topInset))
                        form(true)
                        keypad()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TransactionForm(
    state: AddUiState,
    kindFlip: FlipState,
    scrollable: Boolean,
    onPickDate: () -> Unit,
    onEditNote: () -> Unit,
    onPickAccount: () -> Unit,
    onPickToAccount: () -> Unit,
    onPickCategory: (AddCategoryCell) -> Unit,
) {
    Box(Modifier.flipContent(kindFlip)) {
        AmountCard(kind = state.kind, amountText = state.amountText)
    }
    CategoryPanel(
        state = state,
        onPickCategory = onPickCategory,
        // A lazy grid needs a finite viewport inside a scrolling form.
        modifier = (if (scrollable) Modifier.height(160.dp) else Modifier.weight(1f))
            .flipContent(kindFlip),
    )
    Box(Modifier.flipContent(kindFlip)) {
        InfoBar(state, onPickDate, onEditNote, onPickAccount)
    }
    if (state.kind == AddKindTab.TRANSFER) {
        Box(Modifier.flipContent(kindFlip)) {
            ToAccountChip(text = state.toAccountText, onClick = onPickToAccount)
        }
    }
}

/** 关闭、类型切换、删除按钮在 Compose 内避让状态栏；背景保持全屏。 */
@Composable
private fun TopBar(
    kind: AddKindTab,
    editing: Boolean,
    statusBarHeight: Dp,
    onClose: () -> Unit,
    onPickKind: (AddKindTab) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight)
            .padding(start = themeDp(R.attr.jizhangScreenPadding), top = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonBox(
            res = R.drawable.ic_close,
            desc = R.string.action_close,
            tint = themeColor(ATTR_ON_SURFACE),
            onClick = onClose,
        )

        // 分段切换与左边的关闭键拉开距离，避免误触。
        KindSwitcher(
            selected = kind,
            onPick = onPickKind,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )

        if (editing) {
            IconButtonBox(
                res = R.drawable.ic_delete,
                desc = R.string.action_delete,
                tint = themeColor(ATTR_ON_SURFACE_VARIANT),
                onClick = onDelete,
            )
        } else {
            // 占位，保证分段按钮在三段居中。
            Spacer(Modifier.size(48.dp))
        }
    }
}

/**
 * 分段切换的一格。
 *
 * v1.59：底色交给 [KindSwitcher] 里那个会滑动的指示器，这一格只负责文字与点击 ——
 * 一格一格各自淡入淡出时，看不出「选中态从哪移到哪」，有了滑动指示器就清楚了。
 */
@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onSurface
        } else {
            MiuixTheme.colorScheme.onSurfaceContainerVariant
        },
        animationSpec = tween(SEGMENT_MS, easing = FastOutSlowInEasing),
        label = "segmentText",
    )
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button.copy(fontSize = 14.sp),
            color = textColor,
            maxLines = 1,
        )
    }
}

/** 分段切换的动画时长：与月份切换、底部页签切页同一档。 */
private const val SEGMENT_MS = 260

/**
 * 支出 / 收入 / 转账 三段切换（v1.59）。
 *
 * · **滑动指示器**：选中的那格是填充的，切换时指示器滑过去 —— 以前是两格各自
 *   淡入淡出，看不出「选中态从哪儿移到哪儿」；
 * · **可拖动（v1.62 统一成一种模型：跟手 + 拖到哪落哪）**：
 *   指示器跟着手指 1:1 走（超出轨道就夹住不动），松手落到**指示器停住的那一格**。
 *   `roundToInt` 天然带来半格吸附 —— 拖过半格就切，拖回原格就不切。
 *   ★ 为什么不做 v1.59~1.61.1 那种「快扫方向式」（手指往左 = 去右边那格）：
 *     指示器是**跟着手指走**的，方向式却让它在松手瞬间**弹向反方向**
 *     （往左拖 60px，指示器先左移、松手跳到右边那格），视觉与结果自相矛盾；
 *     而且从「支出」往右拖会算成 `index - 1` 被 `getOrNull` 夹成 null ⇒
 *     手指明明推向「收入/转账」却完全没反应。这正是「拖不到另一个栏目」的成因。
 *   · **长按**（约 500ms，**不需要手指移动**）：同样是「抓起指示器」，但它额外
 *     解除方向限制 —— 斜着拖、纵向拖都算拖动，不会被判给页面滚动。
 *   · **纵向占优**（未长按）：让位给页面滚动（不拦、不消费）。
 *   · **没拖动过**：按**点击**处理，落到按下位置那一格。
 *
 * · **松手不再回跳**（v1.62 补）：松开手指时**不把草稿偏移清零**，而是让它
 *   动画归零，与滑格动画同时跑 —— 见 [KindSwitcher] 里 `dragDraft` 那段注释。
 *
 * ★ 指示器定位与拖动位移都按「格宽」算：用 [BoxWithConstraints] 拿真实宽度，不写死。
 * ★ 手势在**独立的一层、盖在最上面**（后声明 = 上层）里判定。为什么要自己算点击：
 *   这一层盖在按钮之上，下层的 clickable 根本收不到事件（Compose 命中测试命中
 *   最上面的兄弟后就停止），所以点击也由这一层负责。
 * ★ `pointerInput` 的 key 必须带上 `index`：lambda 捕获的是组合时的值，
 *   只写 `tabs` 的话切换到「转账」之后 lambda 里仍是旧的 index=0，拖动会跳错格。
 * ★ [onPick] 只负责切换，方向由调用方（AddTransactionScreen）用 FlipState.goTo 记。
 */
@Composable
private fun KindSwitcher(
    selected: AddKindTab,
    onPick: (AddKindTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clearGlass = LocalLiquidScene.current?.style == GlassStyle.CLEAR
    val tabs = AddKindTab.entries.toList()
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val lastIndex = tabs.size - 1

    // 「已定格」的那一格，滑格动画就是它。
    val targetIndex by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = tween(SEGMENT_MS, easing = FastOutSlowInEasing),
        label = "segmentIndex",
    )
    // 拖动中的临时偏移（单位「格」）。指示器位置 = targetIndex + dragDraft。
    //
    // ★★ 为什么松手时不是把它**清零**，而是**动画写回 0**（v1.62 修「松手回跳一下」）：
    //    原来松手那一帧直接把草稿偏移清零，而 targetIndex 的滑格动画下一帧才起步 ——
    //    指示器会先「啪」地弹回原来那格，再往前滑过去，肉眼就是回跳。
    //    现在松手只把 [dragActive] 落下，由下面的 LaunchedEffect 从**当前值**平滑写回 0；
    //    它与 targetIndex 的滑格动画同曲线（FastOutSlowIn）、同时长（260ms）一起跑，
    //    两者相加正好就是「从手指松开的位置落格」：
    //      · 跨一格：0.97 + 0.03·e  —— 几乎原地落定
    //      · 跨两格：2·e + 2·(1−e) = 2 —— 恰好不动（手指本来就在目标格上）
    //      · 弹回原格：0.4·(1−e)  —— 平滑退回，不是瞬移
    //
    // ★ 为什么不用 Animatable：它的 snapTo 是挂起函数，而 awaitPointerEventScope 是
    //   RestrictedSuspending 作用域，在手势循环里调不了（编译器直接报
    //   "Restricted suspending functions can invoke … only on their restricted
    //   coroutine scope"）。所以用「普通 State + 组合侧手写 animate」——
    //   好处是**指示器只有 dragDraft 一个数据源**，不会出现两套值对不齐的闪帧。
    var dragDraft by remember { mutableFloatStateOf(0f) }
    // 手指是否正抓着指示器。它从 true 落回 false 的那一帧就是「松手」，触发归零动画。
    var dragActive by remember { mutableStateOf(false) }
    LaunchedEffect(dragActive) {
        // 手指还按着：不许动画碰 draft（新一轮拖动会走到这里，顺带把上一段归零动画取消掉）
        if (dragActive) return@LaunchedEffect
        animate(
            initialValue = dragDraft,
            targetValue = 0f,
            animationSpec = tween(SEGMENT_MS, easing = FastOutSlowInEasing),
        ) { value, _ -> dragDraft = value }
    }

    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .liquidSurface(shape, MiuixTheme.colorScheme.secondaryVariant, compact = true),
    ) {
        val cellWidth = maxWidth / tabs.size.coerceAtLeast(1)
        // 首帧宽度可能是 0，除以 0 会算出 Infinity，先把分母兜住。
        val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }.coerceAtLeast(1f)

        Box(modifier = Modifier.fillMaxSize()) {
            // 会滑动的选中指示器：拖动时叠加上手指的偏移。
            Box(
                modifier = Modifier
                    .offset(x = cellWidth * (targetIndex + dragDraft))
                    .width(cellWidth)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (clearGlass) Modifier.liquidSurface(
                            RoundedCornerShape(18.dp),
                            MiuixTheme.colorScheme.surfaceContainer,
                            compact = true,
                        ) else Modifier.background(MiuixTheme.colorScheme.surfaceContainer),
                    ),
            )

            Row(modifier = Modifier.matchParentSize()) {
                tabs.forEach { tab ->
                    SegmentButton(
                        label = kindLabel(tab),
                        selected = tab == selected,
                        onClick = { onPick(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        // 手势层（最上层，见函数头注释）：拖动跟手（拖到哪落哪）+ 点击，一个统一循环。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabs, index) {
                    val slop = viewConfiguration.touchSlop
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    // 指示器能滑的范围（单位「格」，相对当前格）—— 夹住它不越出轨道。
                    val minCells = -index.toFloat()
                    val maxCells = (lastIndex - index).toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val downTime = down.uptimeMillis
                        var elapsed = 0L
                        var dragging = false     // 已进入拖动，指示器跟手
                        var bailOut = false      // 判给纵向滚动，什么都不做
                        while (true) {
                            // 还没定性时挂一个「长按」定时器：手指按住不动也要能抓起指示器
                            //（光靠事件时间戳判断的话，一动不动就没有事件、判不出来）。
                            val event = if (dragging) {
                                awaitPointerEvent()
                            } else {
                                withTimeoutOrNull((longPressMs - elapsed).coerceAtLeast(1L)) {
                                    awaitPointerEvent()
                                }
                            }
                            if (event == null) { dragging = true; continue }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            elapsed = change.uptimeMillis - downTime
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!dragging) {
                                when {
                                    // 长按到点：抓起指示器，此后斜着拖/纵向拖都算拖动
                                    elapsed >= longPressMs -> dragging = true
                                    // 纯横向越过触摸斜率：普通拖动
                                    abs(dx) > slop && abs(dx) > abs(dy) -> dragging = true
                                    // 纵向占优：让位给页面滚动（不拦、不消费）
                                    abs(dy) > slop -> { bailOut = true; break }
                                }
                            }
                            if (dragging) {
                                // 抓着的时候 draft 直接跟手（普通 State 写入，无插值、不拖泥带水）。
                                // dragActive 置位会掐掉上一段归零动画 —— 手指说了算。
                                if (!dragActive) dragActive = true
                                dragDraft = (dx / cellWidthPx).coerceIn(minCells, maxCells)
                                change.consume()
                            }
                        }

                        // 收尾：先按指示器停住的位置算落点，再撒手。
                        val moved = dragDraft
                        val landing = when {
                            // 拖动过 = 落到指示器停住的那一格（roundToInt 就是半格吸附：
                            // 拖过半格才切，拖回原格不切）。
                            dragging -> tabs.getOrNull(
                                (index + moved.roundToInt()).coerceIn(0, lastIndex),
                            )
                            // 判给纵向滚动：什么都不做。尤其不能顺手当成点击 ——
                            // 否则在未选中那格上往下滑会把类型也切过去。
                            bailOut -> null
                            // 没动过 = 点击，按按下位置算格号。
                            else -> tabs.getOrNull((startX / cellWidthPx).toInt())
                        }
                        // 落回原格不回调：省掉一次无意义的翻转动画重放。
                        if (landing != null && landing != selected) onPick(landing)
                        // ★ 最后才撒手：dragActive 落回 false 触发 LaunchedEffect，
                        //   把 dragDraft 动画归零 —— 与上面 onPick 引起的滑格动画同时进行，
                        //   两个位移相加就是平滑落格，不会先弹回原格（见 dragDraft 处注释）。
                        dragActive = false
                    }
                },
        )
    }
}


/**
 * 金额卡：整页视觉焦点。
 * 与分类面板之间必须留 10dp 页面底色 —— 两块圆角面宽度/圆角都不同，
 * 直接相接时面板顶角会从卡片底角两侧探出来（原 XML 注释里的设计约定）。
 */
@Composable
private fun AmountCard(kind: AddKindTab, amountText: String) {
    val screenPadding = themeDp(R.attr.jizhangScreenPadding)
    val appBarPadding = themeDp(R.attr.jizhangAppBarPadding)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenPadding)
            .padding(top = 4.dp, bottom = 10.dp),
        cornerRadius = themeDp(R.attr.jizhangListItemCornerRadius),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangListItemBackground),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = appBarPadding)
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = kindLabel(kind),
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(R.string.add_amount_symbol),
                    style = MiuixTheme.textStyles.title2.copy(fontSize = 26.sp),
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = amountText,
                    style = MiuixTheme.textStyles.title1.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 40.sp,
                    ),
                    color = themeColor(ATTR_ON_SURFACE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                // 输入光标（3dp x 44dp 主色竖条）。
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(width = 3.dp, height = 44.dp)
                        .background(themeColor(ATTR_PRIMARY)),
                )
            }
        }
    }
}

/** 分类面板：比页面底色高一档的容器色 + 20dp 圆角，内部 4 列网格。 */
@Composable
private fun ColumnScope.CategoryPanel(
    state: AddUiState,
    onPickCategory: (AddCategoryCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = themeColor(com.google.android.material.R.attr.colorSurfaceContainer)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .liquidSurface(RoundedCornerShape(24.dp), panelColor),
    ) {
        when {
            state.kind == AddKindTab.TRANSFER -> {
                HintText(stringResource(R.string.add_transfer_hint))
            }

            state.categoryEmpty -> {
                HintText(stringResource(R.string.empty_no_category))
            }

            else -> {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.scrollBoundaryHaptics(gridState)
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    items(state.categories, key = { it.id }) { cell ->
                        CategoryCell(
                            cell = cell,
                            selected = cell.id == state.selectedCategoryId && cell.id >= 0,
                            onClick = { onPickCategory(cell) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 分类网格的一格：36dp 圆形图标（选中=主色底+onPrimary 图标，未选=高容器色底）
 * + 11sp 名称。选中态**只落在圆形图标上**，整格不再出现圆角底色
 * （原 PickerAdapter 里把 selectedBg 永远设为透明，就是这个意思）。
 */
@Composable
private fun CategoryCell(
    cell: AddCategoryCell,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .defaultMinSize(minHeight = 64.dp)
            .clickable { onClick() }
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        themeColor(ATTR_PRIMARY)
                    } else {
                        themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(cell.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(
                    if (selected) themeColor(ATTR_ON_PRIMARY) else themeColor(ATTR_ON_SURFACE_VARIANT),
                ),
            )
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = cell.name,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
            color = if (selected) {
                themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
            } else {
                themeColor(ATTR_ON_SURFACE_VARIANT)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 信息栏：日期 / 备注 / 账户三个等宽 chip。 */
@Composable
private fun InfoBar(
    state: AddUiState,
    onPickDate: () -> Unit,
    onEditNote: () -> Unit,
    onPickAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoChip(
            iconRes = R.drawable.ic_calendar_today,
            label = state.dateText,
            onClick = onPickDate,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        InfoChip(
            iconRes = R.drawable.ic_edit,
            label = state.noteText,
            muted = state.noteIsHint,
            onClick = onEditNote,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        InfoChip(
            iconRes = R.drawable.ic_account_balance_wallet,
            label = state.accountText,
            onClick = onPickAccount,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单枚信息 chip：36dp 高、18dp 圆角、1dp outlineVariant 描边。 */
@Composable
private fun InfoChip(iconRes: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
    val content = if (muted) MiuixTheme.colorScheme.onSurfaceContainerVariant else MiuixTheme.colorScheme.onSurface
    Button(onClick = onClick, modifier = modifier.height(40.dp), minHeight = 40.dp, minWidth = 0.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Image(painterResource(iconRes), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(content))
        Spacer(Modifier.width(5.dp))
        Text(text = label, style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp), color = content,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 转账时的转入账户 chip（铺满、居中）。 */
@Composable
private fun ToAccountChip(text: String, onClick: () -> Unit) {
    Button(onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 6.dp), minHeight = 40.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Image(painterResource(R.drawable.ic_account_balance_wallet), null, Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSecondaryVariant))
        Spacer(Modifier.width(5.dp))
        Text(text = text, style = MiuixTheme.textStyles.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val KEY_HEIGHT = 50.dp
private val KEY_INSET = 2.dp
private val KEY_ROW_PADDING = 2.dp
private val KEYPAD_TOP_PADDING = 4.dp
private val KEYPAD_BOTTOM_PADDING = 8.dp
private val KEYPAD_CONTENT_HEIGHT =
    (KEY_HEIGHT + KEY_INSET * 2 + KEY_ROW_PADDING * 2) * 4 + KEYPAD_TOP_PADDING + KEYPAD_BOTTOM_PADDING

/**
 * 键盘：顶部两角 24dp 圆角、底部直角；键高 50dp、列间 2dp 内衬。
 *
 * 底部在原有 8dp 内衬上补实际尚未避让的安全区，避免重复叠加 View 根已处理的 IME。
 */
@Composable
private fun Keypad(
    state: AddUiState,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onPickDate: () -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .liquidSurface(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), themeColor(R.attr.jizhangKeypadBackground))
            .padding(horizontal = 8.dp)
            .padding(top = KEYPAD_TOP_PADDING, bottom = KEYPAD_BOTTOM_PADDING + bottomInset),
    ) {
        KeyRow {
            DigitKey("7", onKey)
            DigitKey("8", onKey)
            DigitKey("9", onKey)
            BackspaceKey(onKey)
        }
        KeyRow {
            DigitKey("4", onKey)
            DigitKey("5", onKey)
            DigitKey("6", onKey)
            FunctionKey(label = stringResource(R.string.action_reset), onClick = onClear)
        }
        KeyRow {
            DigitKey("1", onKey)
            DigitKey("2", onKey)
            DigitKey("3", onKey)
            FunctionKey(label = state.dateText, onClick = onPickDate)
        }
        KeyRow {
            // 0 键横跨两列。
            WideDigitKey("0", onKey)
            DigitKey(".", onKey)
            PrimaryKey(stringResource(R.string.action_save), enabled = state.canSave, onClick = onSave)
        }
    }
}

/** 一行键：四个等宽槽位，槽间 2dp 内衬。 */
@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KEY_ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(label: String, onKey: (String) -> Unit) {
    KeySurface(
        onClick = { onKey(label) },
        background = themeColor(R.attr.jizhangKeyBackgroundTint),
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Medium, fontSize = 24.sp),
            color = themeColor(ATTR_ON_SURFACE),
        )
    }
}

@Composable
private fun RowScope.WideDigitKey(label: String, onKey: (String) -> Unit) {
    KeySurface(
        onClick = { onKey(label) },
        background = themeColor(R.attr.jizhangKeyBackgroundTint),
        modifier = Modifier.weight(2f),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Medium, fontSize = 24.sp),
            color = themeColor(ATTR_ON_SURFACE),
        )
    }
}

@Composable
private fun RowScope.FunctionKey(label: String, onClick: () -> Unit) {
    KeySurface(
        onClick = onClick,
        background = themeColor(com.google.android.material.R.attr.colorSurfaceContainerLow),
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.BackspaceKey(onKey: (String) -> Unit) {
    KeySurface(
        onClick = { onKey("del") },
        background = themeColor(com.google.android.material.R.attr.colorSurfaceContainerLow),
        modifier = Modifier.weight(1f),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_backspace),
            contentDescription = stringResource(R.string.action_delete),
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(themeColor(ATTR_ON_SURFACE_VARIANT)),
        )
    }
}

@Composable
private fun RowScope.PrimaryKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    KeySurface(
        onClick = onClick,
        enabled = enabled,
        // 禁用态回落容器浅灰、可用态用主色 —— 对齐原 XML 的 key_primary_background
        // 状态列表语义（v1.54.1：早期用 Modifier.alpha 表达禁用，会把已画好的
        // 主色背景一起降透明、在部分 GL 路径下直接丢失背景层，保存键看起来是灰白一片）。
        background = if (enabled) themeColor(ATTR_PRIMARY) else themeColor(R.attr.jizhangKeyBackgroundTint),
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button.copy(fontSize = 17.sp),
            color = if (enabled) themeColor(ATTR_ON_PRIMARY) else themeColor(ATTR_ON_SURFACE_VARIANT),
        )
    }
}

/** 一枚键：50dp 高、圆角走 jizhangKeyCornerRadius、按下有轻微容器色提升。 */
@Composable
private fun KeySurface(
    onClick: () -> Unit, background: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.padding(horizontal = KEY_INSET, vertical = KEY_INSET).height(KEY_HEIGHT),
        minWidth = 0.dp, minHeight = KEY_HEIGHT, insideMargin = PaddingValues(0.dp),
        cornerRadius = 16.dp,
        colors = ButtonDefaults.buttonColors(color = background, disabledColor = background),
    ) { content() }
}

/** 顶栏圆形图标按钮：48dp 触达区。 */
@Composable
private fun IconButtonBox(res: Int, desc: Int, tint: Color, onClick: () -> Unit) {
    com.jizhang.app.ui.glass.LiquidIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Image(painter = painterResource(res), contentDescription = stringResource(desc),
            modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(tint))
    }
}

/** 分段按钮的文案（= TxKind.label 的等价物，由 Activity 传入 kind 时已对齐）。 */
@Composable
private fun kindLabel(kind: AddKindTab): String = when (kind) {
    AddKindTab.EXPENSE -> stringResource(R.string.kind_expense)
    AddKindTab.INCOME -> stringResource(R.string.kind_income)
    AddKindTab.TRANSFER -> stringResource(R.string.kind_transfer)
}
