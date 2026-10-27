import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // AGP 9 自带内置 Kotlin 支持（android.builtInKotlin，默认开启），
    // 因此这里不能再显式应用 org.jetbrains.kotlin.android，否则 Gradle 会报
    // "plugin is already on the classpath with an unknown version"。
    // Kotlin 版本由 kotlin-serialization 插件（2.3.21）拉齐，与 KSP 2.3.6 匹配。
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * 发布签名凭据从工程根的 keystore.properties 读取，**不再写在这个脚本里**。
 *
 * 原因：这个仓库是公开的。脚本里的字符串字面量会永久留在提交历史里，
 * 等于把签名私钥的密码连同 keystore 一起公开 —— 任何人都能用它签出
 * 冒充本应用的包。keystore.properties 与 keystore/ 都已列入 .gitignore，
 * 只存在于本机。
 *
 * 文件缺失时（别人 clone 下来、或 CI 未注入）不挂签名配置，
 * release 产物是 unsigned APK，但**构建本身照常跑通**，不会因为缺密钥而失败。
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.jizhang.app"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.jizhang.app"
        minSdk = 26
        // v1.27：所有页面改为边到边 + 状态栏玻璃，targetSdk 同步提到 36。
        targetSdk = 36
        // 版本号必须大于已发出的 2.9.8(29)，否则装机会被判为降级而失败。
        // v1.28：去掉底栏外那圈常驻投影。
        // v1.29：底栏玻璃底色改回 2.9.8 的写法（混主题色 + 更高不透明度）。
        // v1.30：底栏阴影也照搬 2.9.8（恢复 Shadow.Default）。
        // v1.32：顶栏改 HyperOS 式常驻白磨砂（偏重，v1.33 已撤销）。
        // v1.33：回到滚动加浓契约，修采样区未裁剪 / 遮罩每帧重建 /
        //        尾巴零余量；半径改分档（方向错了，v1.34 撤销）。
        // v1.34：**模糊半径恒定** —— 滚动时抖动的真正根因是半径跨度(13dp)/阈值(56dp)
        //        太大，惯性滚动一帧就要跳 7~12px。半径不再参与滚动，只留 alpha 加浓。
        // v1.35：底栏阴影 8dp -> 2dp（并修掉 offset 不跟着半径算的失真），
        //        重新加回高光描边（0.5dp / blurRadius 0.5dp / alpha 0.45）。
        // v1.36：底栏重构（对齐 SleepDown 课程表截图）：迷你胶囊居中（不再近全宽）、
        //        页签图标+文字常驻、去掉 vibrancy、"记一笔"与页签同构；
        //        代码拆成 GlassBarTokens（视觉常量）/ GlassTabLayer（内容）/
        //        LiquidBottomBar（玻璃三层）/ GlassBottomBarHost（接线）四块。
        // v1.37：胶囊底栏改回近全宽 68dp（撤销 v1.36 迷你化的尺寸部分）；
        //        玻璃质感对齐官方 LiquidBottomTabs：底色 0.56/0.62 → 0.4、
        //        vibrancy 回归、blur 8dp、lens 24dp —— 截图里「背景被透镜
        //        弯折」的效果配方。
        // v1.38：修「选中胶囊没对齐」—— v1.37 恢复近全宽时，第 3 层（拖拽胶囊）
        //        漏加 `.padding(horizontal = ContentInset)`，导致它比 Row 内容区
        //        起点偏左 4dp、宽度还多 2×4dp/槽数。折射层也补上 addLabel。
        // v1.39：backdrop 升级到 2.0.1；修复 v1.39 首版引入的闪退（回退 Bitmap 缓存方案）；
        //        去掉 animateFloatAsState，alpha 直接同步 progress（修慢速滑动抖动）。
        // v1.40：顶栏模糊用 Compose 标准用法重写 —— rememberBackdrop + drawBackdrop
        //        的 onDrawBackdrop（对齐 SleepDown / 官方 LiquidBottomTabs）；
        //        effects 加回 vibrancy（不垫底色，不会变成白纱）。
        //        采样仍需手动 contentView.draw（页面主体是 View/Fragment，非 Compose）。
        //        【已证伪】v1.40 顶栏"上端模糊消失"的真正原因不是采样架构，
        //        而是 vibrancy() 写在 blur() 前面 —— 见 GlassStatusBarHost 的 effects 注释。
        // v1.41：撤销 v1.40 的 vibrancy()（effects 回到「只有 blur」），修复
        //        「顶栏上端模糊消失」。采样保持 rememberCanvasBackdrop + contentView.draw，
        //        这是页面为 View/Fragment 时的唯一可行采样路径（已写进注释）。
        // v1.42：修「抖动」+「模糊时有时无」——二者同根因：
        //        drawBackdrop 内部的 ShapeProvider 没有 equals（引用相等），
        //        而 progress 在组合体里读 ⇒ 每帧重组 ⇒ DrawBackdropNode.update()
        //        ⇒ updateEffects() ⇒ 每帧重建一次 BlurEffect / RenderEffect。
        //        修法：把静态 modifier（drawBackdrop / drawWithContent）与采样 lambda
        //        全部 remember 住，滚动只留下 graphicsLayer 的 alpha 一个变量。
        // v1.43：**明细页（主页）改成纯 Compose**（LedgerScreen.kt）。
        //        页面内容成为 Compose 节点后，顶栏玻璃改用
        //        rememberLayerBackdrop + Modifier.layerBackdrop 采样
        //        （对齐 Kyant0/AndroidLiquidGlass 的 BackdropDemoScaffold）——
        //        同一帧、同一次绘制遍历，不再由玻璃自己去重画整棵 View 树。
        //        玻璃抽成共享组件 StatusBarGlass.kt；View 页面仍用旧的
        //        GlassStatusBarHost（canvas 采样）兜底，由页面声明谁接管。
        // v1.43.1：加崩溃自捕获（util/CrashLog.kt + ui/CrashReportActivity.kt）——
        //        本机没连设备拿不到 logcat，只能让 App 自己把堆栈留下来；
        //        下次启动优先跳崩溃页，用户可一键复制发回来。
        //        另加 util/SafeMode.kt：关掉主页顶部玻璃的排障开关
        //        （关了就崩 = 页面本身的问题；关了不崩 = 玻璃采样的问题）。
        // v1.43.2：**修 v1.43.1 的真机闪退**。
        //        根因不是玻璃，是 `themeColor()` 无条件 `getColor(value.resourceId)`：
        //        `jizhangMonthSwitcherBackground` 是 `format="reference"`，
        //        解析出来是 `@drawable/bg_month_switcher_md3`，
        //        getColor 读 drawable ⇒ Resources$NotFoundException（measure 阶段整页崩）。
        //        修法两条：① 该属性改走 `themeDrawable()` + `drawBehind` 按 drawable 画；
        //        ② `themeColor()` 先看 TypedValue 的 type，是颜色整数就直接用 data，
        //        其余情况 getColor 包 runCatching，绝不因一个颜色炸掉整页。
        // v1.45：**消掉顶栏抖动的最后一根因 —— 让顶栏零随帧变量**。
        //        顶栏玻璃显形靠的是「内容钻进状态栏底下」带来的纹理，
        //        不需要强度跟着滚动变；而 v1.42 已证明「每帧重组 = 每帧重建
        //        GPU 效果链」，所以只要有一个量随滚动变，抖动就必然会回来。
        //        ⇒ 删掉 progress：alpha 恒定 0.85、blur 恒定 20dp、仍不垫底色。
        //        同时删掉 GlassStatusBarHost.scrimProgress 与 ThemedActivity 的
        //        SCRIM_THRESHOLD_DP（没有消费者就不留旋钮），
        //        ScrollProgressTracker 的 onProgress 改为可空、默认不建进度链。
        //        另修 LedgerFragment.statusBarHeightDp() 的静默 bug：
        //        它在组合体里现读 inset，首帧 view 尚未 attach ⇒ 恒为 0.dp，
        //        玻璃几何塌成一条且内容顶到系统图标底下，还不报错。
        //        改成由 inset 回调推进 state，首帧即使为 0 也会随后修正。
        // versionName 回到 1.x 序列（1.30）。versionCode 只增不减 ——
        // 它是系统判断「能否覆盖安装」的唯一依据，一旦回退就会
        // INSTALL_FAILED_VERSION_DOWNGRADE，老用户必须卸载重装。
        // v1.46：顶栏模糊改成自写 AGSL（照 SleepDown 的 nexioProgressiveBlur 路子）。
        //        · 变半径：状态栏段满半径，进入渐隐带按 smoothstep 收到 0；
        //        · 收边做在 shader 里 ⇒ 不再有下硬边 ⇒ 32dp 透明尾巴（MASK_TAIL）只在
        //          API<33 的兜底路径里保留；
        //        · 半径 20dp -> 14dp（SleepDown 用 7dp，但它底下有壁纸可糊）；
        //        · 敢设 padding = radius 的根据：shader 里跳过 c.a<=0.02 的样本，
        //          透明边框不再稀释 alpha（v1.40 那个坑的另一种解法）；
        //        · 变半径是**沿 y** 的函数，不随滚动变 ⇒ 仍然零随帧变量、不抖。
        //        View 页面那条路径（GlassStatusBarHost）共用同一份 shader 与常量。
        // v1.47：明细页日期头（「今天 / 支出 xx」那条）从通栏直角色带改成
        //        与条目卡同几何的圆角栏（marginH + itemCornerRadius + 描边），
        //        「本月共 N 笔」左缘同步对齐到卡片文字起点。
        //        全部走主题 token：经典风格下这两个值是 0dp/0dp，
        //        自动回到原通栏直角，旧观感不变。
        // v1.48：明细页日期头圆角栏减薄 —— 把「上方留白」从栏内挪到栏外
        //        （原来 28dp 全压在栏里把栏撑厚），栏内改对称 10dp。
        //        栏高 55dp → 39dp，分组间距由栏外的 14dp 负责，不受影响。
        // v1.49：页签切页加「翻页」动画，参数照 SleepDown 的 depth 页面动画
        //        （res/anim/schedule_depth_*.xml：进 alpha 0→1 + scale 1.05→1，
        //         出 alpha 淡出 + scale 1→0.92）。
        //        为什么没用 FragmentTransaction 自定义动画：那套只对 add/remove/replace
        //        生效，对 hide/show 不生效（不改生命周期状态就不触发转场），
        //        而我们的页签恰好靠 hide/show 复用 Fragment 保滚动位置
        //        ⇒ 新增 ui/common/PageFlip.kt 自己驱动 ViewPropertyAnimator。
        // v1.50：删掉「界面风格」整个概念（经典 CLASSIC / MD3 ELEGANT 两套源码+资源全删），
        //        基于单一 miuix/HyperOS 观感重构：
        //        · ThemeOverlay.Jizhang.Classic / .Elegant / .HyperOS 三份结构层合并进
        //          Theme.Jizhang，外观 token 只剩一份 —— 代码里不再有「按风格取形状」的分支；
        //        · UiStyle 枚举、界面风格设置行与弹窗、Canvas/Keypad 等风格专属 drawable 一并删除；
        //        · **保留原 MD3 配色方案**（ThemeOverlay.Jizhang.Palette.* 八套 + HyperOS），
        //          并解除「仅 MD3 风格可用」的限制 —— 现在它们全部作用于唯一的基础主题，
        //          设置里的「配色方案」对所有用户可见可用；
        //        · 老安装的 ui_style 记录在启动时清掉，配色选择保留。
        // v1.51：待确认页、统计页改成 Compose（miuix）—— View/XML 只剩 Activity 层的页面。
        //        · ui/pending：PendingScreen + PendingFragment（保留业务），删 PendingAdapter、
        //          fragment_pending.xml、item_pending.xml；
        //        · ui/stats：StatsScreen + StatsFragment（保留取数），删 fragment_stats.xml、
        //          item_category_rank.xml；两张图表是手写 Canvas 的自定义 View，
        //          用 AndroidView 原样承载，观感逐像素不变（详见 StatsScreen 文件头）；
        //        · ui/common：抽出 ComposeTokens（原 LedgerScreen 私有的 token 读取器）与
        //          GlassBarInsets（悬浮底栏高度订阅，Compose 页面自己避让底部）。
        // v1.51.1：修统计页「白卡片贴白底」—— miuix 默认配色（background 纯白）
        //          与应用卡片色（surfaceContainerLowest 纯白）撞色，整页没有栏框。
        //          · JizhangMiuixTheme 把 miuix background/primary/surfaceContainer
        //            映射到应用 View 主题调色板（页面底 = colorSurface 淡紫灰）；
        //          · 统计页卡片改用 miuix Card，月份胶囊恢复「白卡包 drawable」
        //            双层结构（jizhangMonthSwitcherBackground 是 drawable reference，
        //            不能按颜色解析 —— v1.43.1 闪退同坑）；
        //          · 待确认页底色随之修复（同一处映射生效）。
        // v1.52：清理死代码 —— Compose 化后遗留的 View/XML 资产。
        //        · LedgerAdapter.kt → 拆出仍在用的 LedgerRow 到 LedgerRow.kt，
        //          Adapter 本体（ListAdapter + 两个 Binding）删除；
        //        · UiUtil.kt 删 TxColors / tintIcon / applyEmptyStateStyle
        //          （后者依赖只存在于已删 XML 里的 R.id.emptyIcon/emptyEmoji）；
        //        · 删 layout：fragment_ledger / item_transaction / item_day_header /
        //          item_category_cell / item_setting_row；
        //        · 删 drawable：bg_category_icon / bg_hyperos_app_icon / ic_check。
        //        原文件已备份到 output/_deleted_v1512/。
        // v1.55：Compose 化收尾 —— 清掉最后一批 View/XML。
        //        · 明细页筛选面板从「sheet_filter.xml + Material ChipGroup +
        //          MaterialAlertDialog」改成 Compose（新 ui/ledger/LedgerFilterSheet.kt，
        //          弹层用 miuix WindowBottomSheet）。LedgerFragment 的 showFilterSheet()
        //          与 SheetFilterBinding / Chip / MaterialAlertDialog 依赖一并删除；
        //        · 删死代码 ui/common/PickerAdapter.kt（v1.53 起无人引用，
        //          连同 item_picker_cell.xml）；
        //        · 删 11 个无引用 layout（activity_* 六个 + item_* 五个）。
        //          判定法：反查 `XxxBinding` 生成类是否被引用 —— 只查 `R.layout.x`
        //          会漏报，ViewBinding 不出现该字面量；
        //        · layout 目录从 14 个降到 2 个：activity_main（主页面宿主壳）
        //          与 fragment_settings（ScrollView 包 ComposeView 的壳）。
        //        备份在 output/_deleted_v155/。
        // v1.56：统一对话框 —— 全项目 20 多条 MaterialAlertDialogBuilder 收敛到
        //        ui/common/JizhangDialog.kt。
        //        · 「spec + 命令式 Host」：View 侧调 dialogs.confirm/pick/input/custom，
        //          渲染交给挂在页面 Compose 树里的 JizhangDialogHostView（miuix WindowDialog）。
        //          调用点全在 View 侧命令式代码里，而 Compose 对话框必须挂在组合树上，
        //          这个 Host 就是那座的桥（本身只是 state holder）。
        //        · Host 内部是**对话框栈**：账户编辑器的「账户类型」、分类编辑器的 emoji
        //          网格都是压在表单之上的第二层，栈顶弹出后下层自动回来。
        //          配套要求：下层表单状态必须提升到 View 侧（否则退出组合时被 remember 清掉）。
        //        · 新增 ui/common/EmojiGrid.kt 取代 EmojiPicker（RecyclerView + Material 对话框）。
        //        · 账户 / 分类编辑表单改成 Compose（AccountEditorForm / CategoryEditorForm）。
        //        · EmojiPicker.kt 已删，备份在 output/_deleted_v156/。
        // v1.57：记账页返回手势修复 + 全应用系统级触感反馈。
        //        · AddTransactionActivity 原来注册了一个 `BackHandler { }`（空 lambda）——
        //          BackHandler 一旦注册就会**消费**返回手势与返回键，空实现等于把退出动作
        //          吞掉：新建账目时侧滑返回完全没反应（原注释写的是「不拦返回，交给系统」，
        //          但 enabled = true 恰恰就是拦住了）。已删除，交给系统默认返回
        //          （= finish()，与右上角关闭按钮 onClose 完全等价）。
        //        · 新增 ui/common/Haptics.kt：统一震动入口。走系统 Vibrator +
        //          VibrationEffect.EFFECT_*（API 29+，26~28 降级 createOneShot），
        //          受设置项「触感反馈」控制（SettingsStore.hapticEnabled，默认开）。
        //          不用 Compose 的 LocalHapticFeedback —— 那个在多数 ROM 上普通点击不震。
        //        · 接入点：记账键盘每次按键（最轻档）、保存成功、对话框主按钮、
        //          筛选面板「应用」、设置里的触感开关（打开时立刻给一下反馈）。
        //        · Manifest 新增 android.permission.VIBRATE（normal 级，无需动态申请）。
        // v1.58：月份切换动画（明细页 + 统计页共用一套）。
        //        · 新增 ui/common/MonthFlip.kt：`MonthFlipState`（记方向）+ `MonthFlipLabel`
        //          （AnimatedContent 包月份文字）。参数照 SleepDown 的 depth 页面动画
        //          （github.com/xiaomanjun233/SleepDown-Schedule 的 schedule_depth_*.xml），
        //          与已有的底部页签切页（PageFlip）同一条曲线：进 260ms / 出 220ms、
        //          scale 1.05→1 进、1→0.92 出、fast_out_slow_in = cubic-bezier(0.4,0,0.2,1)。
        //        · 在 depth 之上多叠一层 40dp 横向位移 —— depth 只表达「换了一层」，
        //          说不清前后；而月份切换有方向：上一月从左侧进、下一月从右侧进。
        //        · 刻意不给下方列表 / 图表套进场动画：明细页的 LazyColumn 上挂着玻璃层
        //          （backdrop 采样的是它下面那棵子树），套 graphicsLayer 有搞坏采样的
        //          风险；长列表每帧重绘也不划算。动画只落在「月份」这个真正被切换的东西上。
        // v1.58.1：记一笔的键盘抬高，不吃掉手势导航那条小白条。
        //          · 根因：基类 ThemedActivity.applyWindowInsets 只按 `ime.bottom` 给
        //            内容根补底部 padding —— 没弹软键盘时它恒为 0，键盘就贴死在屏幕
        //            底边，正好压住手势导航的小白条。
        //          · 修法：Keypad 底部额外补「导航栏比软键盘高出来的那部分」
        //            （AddTransactionScreen.keypadBottomExtra）。无键盘时补满导航栏
        //            高度，有键盘时补 0 —— 两种情况都不重复留白。
        //          ★ 用 Compose 的 WindowInsets 而不是自己注册 OnApplyWindowInsetsListener：
        //            基类已经在 content 上注册了一个，而它是 setter，再设一次会把基类的
        //            顶掉（状态栏避让跟着失效 —— v1.54.1 踩过同一条）。
        // v1.59：记一笔 —— 顶栏右移、类型切换动画 + 可拖动、栏目动画；顺带清死代码。
        //        · 顶栏分段切换整体右移 14dp，与左边的关闭键拉开距离（原来几乎贴着叉号）。
        //        · 类型切换改成**会滑动的指示器**（以前是两格各自淡入淡出，看不出
        //          选中态往哪移），并支持横向拖动切换：拖动时指示器跟着手指走，
        //          松手按位移方向决定去哪一格（半格阈值）。
        //        · 下方栏目（金额卡 / 分类面板 / 信息栏 / 转入账户）跟着类型切换做一次
        //          方向性 depth 进场 —— 复用月份切换那套（MonthFlip 已泛化成 Flip）。
        //        · 死代码清理：7 个孤儿 drawable + 39 个孤儿 string，多是 Compose 化
        //          之后遗留的 XML 时代 hint / 标题。备份在 output/_deleted_v159/。
        // v1.60：所有界面 miuix 化（第一批）—— 提示条 + 崩溃日志页。
        //        · Material Snackbar → miuix Snackbar：新增 ui/common/JizhangSnackbar.kt
        //          （命令式 Host + 渲染它的 composable，与 JizhangDialog 同一个套路 ——
        //          showSnackbar 是 suspend，只能在 LaunchedEffect 里调）。
        //          7 个页面共 19 处提示条全部接上；顺带删掉已无调用方的 UiUtil.kt。
        //        · CrashReportActivity：从「平台主题 + 手建 LinearLayout」改成 Compose + miuix。
        //          ★ 仍不套项目主题、不碰 AppCompat（见该文件头注释）—— 守住
        //          「应用一启动就崩时这个页也要能显示」的底线。
        //        · 剩余非 miuix：下拉刷新（SwipeRefreshLayout）、日期选择器（原生
        //          DatePickerDialog，miuix 没有对应组件）、统计页图表（AndroidView，刻意保留）。
        // v1.61：miuix 化第二批 —— 明细页下拉刷新。
        //        · View 侧的 `SwipeRefreshLayout` 整个删掉，换成 miuix `PullToRefresh`
        //          （top.yukonga.miuix.kmp.basic：isRefreshing + onRefresh 直通 ViewModel
        //          的 StateFlow）。LedgerFragment 从此只剩一个 ComposeView：
        //          手动建筑的 layout / measure 那套全没了，难点全部移进 Compose。
        //        · ★ 顺带删掉 v1.45 那条 workaround：`SwipeRefreshLayout.canChildScrollUp()`
        //          对 ComposeView 恒返回 false，会把「列表顶部的向下滑」全吃掉，
        //          结果列表滚下去之后**再也滚不回顶部**。当时的补丁是「从 Compose 注册一个
        //          canScrollUp 查询」（registerCanScrollUpQuery）交给 View 层覆写；
        //          现在手势同步由 Compose nestedScroll 天然承担，这个参数连同整套
        //          查询代码一起删除 —— 那个 bug 连根拔掉，不再是「补得住」而是「不存在」。
        //        · ★ PullToRefresh 的指示器是 content 的**兄弟节点**（内部 Column 里包在
        //          content 上方），空闲态 headerHeight = 0.dp 不占任何空间，下拉时才把
        //          content 推下去 —— 和 SwipeRefreshLayout 那个「悬浮转圈盖在上面」
        //          不是一回事。所以它可以安全地包住 LedgerScreen 的根 Box，
        //          玻璃作为最后子节点仍然画在最上层。
        //        · 剩余非 miuix：日期选择器（原生 DatePickerDialog，miuix 确实没有
        //          对应组件，只有 NumberPicker）、统计页图表（AndroidView，刻意保留）。
        // v1.61.1：记一笔类型切换「拖一点就切」。
        //        · 阈值从半格降到 0.05 格 —— detectHorizontalDragGestures 已保证
        //          越过触摸斜率的纯横向拖动才回调，半格阈值让轻拖毫无反应，
        //          体感就是「拖动切换没了、只能点击」。
        // v1.62：记一笔类型切换 —— 手势重写为「跟手拖动，拖到哪落哪」。
        //        · 病根：v1.59 起的 detectHorizontalDragGestures 是 **ViewPager 式**规则
        //          （手指往左 = 去右边那格），而指示器是**跟着手指走**的 ——
        //          往左拖 60px，指示器先左移、松手却跳到右边那格，视觉与结果自相矛盾；
        //          更糟的是从「支出」往右拖会算成 `index - 1`，被 getOrNull 夹成 null
        //          ⇒ 手指明明推向「收入/转账」却完全没反应。用户描述的
        //          「不能（长按）拖动到另一个栏目」正是这两件事。
        //        · 新模型（awaitEachGesture 手写判定，只留两种手势形态）：
        //          ① **拖动** = 指示器跟手指 1:1 走（超出轨道就夹住不动），
        //             松手落到指示器停住的那一格。落点用 `index + dragCells.roundToInt()`，
        //             半格吸附天然实现了「拖一点就切、拖回原格不切」，
        //             也天然支持「一次跨过中间那栏」；
        //          ② **纵向占优** = 直接 break 不消费，让位给页面滚动（且绝不能顺手
        //             当成点击 —— 否则在未选中那格上往下滑会把类型也切过去）；
        //          ③ 其余 = 点击，按按下位置算格号（下层 SegmentButton 的 clickable
        //             收不到事件：Compose 命中测试命中上层兄弟就停了）。
        //        · ★ 长按（viewConfiguration.longPressTimeoutMillis）用
        //          `withTimeoutOrNull { awaitPointerEvent() }` 判定，**不依赖手指移动** ——
        //          按住不动时根本没有事件，只看事件时间戳是判不出长按的。
        //          长按成立的额外意义：解除方向限制，斜着拖/纵向拖也算拖动。
        //        · 删掉 v1.61.1 的 SEGMENT_DRAG_CELLS 阈值常量（新模型不再需要）。
        //        · ★ 时间基准取 `change.uptimeMillis`，不是 `event.uptimeMillis`
        //          （本工程 Compose 版本里 PointerEvent 没暴露该属性，编译期报
        //          Unresolved reference；PointerInputChange 上有）。
        //        · 实测（Pixel7 API35）：点击「转账→收入」✓；长按从「支出」拖到「转账」
        //          跨过中间那栏 ✓；反向拖回 ✓；纵向拖动不误切 ✓。
        //        · ★ 补：修掉「松手回跳一下」—— 原来松手是在同一帧把草稿偏移**清零**，
        //          而滑格动画下一帧才起步 ⇒ 指示器先弹回原格再往前滑，肉眼就是回跳。
        //          改成草稿偏移用 Animatable：拖动中 snapTo（直接跟手，无插值），
        //          松手后 animateTo(0f)，与 targetIndex 的滑格动画**同曲线同时长**一起跑。
        //          两者相加：跨一格 ≈ 原地落定 / 跨两格 = 恰好不动 / 弹回原格 = 平滑退回。
        //          触发点用 `dragActive` 这个布尔（true→false 那一帧即「松手」），
        //          因为动效要跑在组合体的 LaunchedEffect 里，不能堵在手势协程里等 260ms。
        // v1.63：依赖瘦身（不改任何 UI 行为）。
        //        · 删 `androidx.swiperefresh` —— v1.61 下拉刷新换成 miuix PullToRefresh 后，
        //          源码里对它只剩注释；APK 里连类都没打包（dex 中只剩 androidx.startup
        //          元数据里的一个 $SavedState 字符串，是假阳性）。
        //        · 删 `androidx.constraintlayout` —— 源码零引用，且 material 本来就传递带它。
        //        · 删 `androidx.documentfile` —— 导入走的是 SAF Intent，不碰 DocumentFile。
        //        · 删 `androidx.lifecycle.livedata` —— 全项目用 StateFlow，不是 LiveData。
        //        · 删 `kotlinx-serialization.json` —— 源码里没有任何 @Serializable / Json。
        //          （kotlin-serialization **插件**留着，它只影响编译期、不进 APK。）
        //        · gradle/libs.versions.toml 里对应的 5 个库条目 + 4 个孤立版本号一并清掉。
        //        · 两条「看着没用、其实要留」：appcompat（ThemedActivity 的父类 +
        //          深色模式 AppCompatDelegate）、recyclerview（ScrollProgressTracker）。
        //        · compose.material.ripple 同理：自己不写 ripple()，但 miuix 按压反馈
        //          运行时要用，删了 R8 会报缺类。
        // v1.64：修「设置页顶栏没有模糊」+「设置页向下滑动底栏不消失」（两处同一个根因）。
        //        · 根因：v1.60 把设置页从「ScrollView + 行 XML」换成「FrameLayout + ComposeView」，
        //          并在**根容器上补了 paddingTop=48dp**。这一改动同时坏了两件事：
        //          ① padding 把 ComposeView 的顶边推到 126px，而子 View 画不到自己上边界之外
        //             ⇒ 内容永远进不了状态栏那条，顶栏玻璃无内容可糊（糊出来只是一块
        //             「比周围更平滑的区域」，正是用户投诉的「顶栏模糊没了」）；
        //          ② View 滚动容器随之消失，ScrollProgressTracker 扫不到任何东西
        //             ⇒ 底栏判据恒为「停在顶部」。A/B 实测：v1.59 滚到底底栏会收起，
        //             v1.60 之后不会 —— 不是错觉，是回归。
        //        · 修法：根容器去掉 paddingTop，顶部留白挪进 Compose
        //          （`top = statusBarHeight + 32.dp`），它是滚动内容的一部分，
        //          滚动时会穿到状态栏底下 ⇒ 玻璃终于有东西可糊。
        //        · 底栏：新增 ui/common/PageScrollReporter.kt —— 累计 nestedScroll 的
        //          consumed.y（真实像素、严格线性），四个主页签统一走这条链。
        //          为什么不用读滚动状态：ScrollState 只在 verticalScroll 那条路有，
        //          LazyColumn 没有；用 firstVisibleItemIndex × 平均 item 高是**估算**，
        //          item 高度不一时会跳变（ScrollProgressTracker 里踩过同一个坑）。
        //        · ★ 累积值挂在 **Fragment** 上而不是组合体里：切页时 MainActivity 要用
        //          「这一页当前的偏移」同步方向判据的基准，否则「切到一页已经滚下去
        //          800px 的页面后往回滚」会因基准为 0 被算成 delta>0 ⇒ **往上滚反而收起底栏**。
        //          新接口 ui/common/ScrollOffsetPage 就是给 MainActivity 问这个值的。
        //        · MainActivity.onLedgerScroll 泛化成 onPageScroll（四个页面共用）。
        //        · 明细页原来自己写的 NestedScrollConnection 删掉，改用共用实现（行为等价）。
        // v1.65：底部提示条显示期间隐藏悬浮底栏，退场完成后按滚动状态恢复。
        // v1.66：确认提示缩短、支持轻点关闭、底栏缓出恢复；设置页底部内容连续绘制。
        // v1.67：统一 miuix 控件；日期滚轮与 Compose 图表；移除底栏拖动高光边界。
        // v1.68：滚动边界触感、中文刷新提示、待确认标题避让与设置页对齐。
        // v1.73：统一二级页状态栏玻璃、修复首帧与弹窗退场闪动、精简主题与滚动链。
        // v1.74：刷新层避让、轻字重标题、圆角浅模糊顶栏与悬浮玻璃按钮。
        // v1.75：标题中线渐隐玻璃、悬浮操作区、记一笔对齐与渲染缓存。
        // v1.76：待确认全屏滚动与悬浮模糊标题、清理旧资源并复用启动图标。
        versionCode = 95
        versionName = "1.84"
        resourceConfigurations += listOf("zh", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 凭据全部来自本机 keystore.properties（见上方说明，不入库）。
            if (hasReleaseSigning) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源收缩：dex 从 ~24MB 压缩，未引用的资源一并裁掉。
            // 之前为了排查方便一直关着，现在正式收尾打开（含字体瘦身后的包体）。
            isMinifyEnabled = true
            isShrinkResources = true
            // 没拿到凭据就不挂签名：产出 unsigned APK，比让构建直接失败更合适。
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // ↓ AppCompat 不是「UI 组件」而是 Activity 基建：ThemedActivity 继承 AppCompatActivity，
    //   深色模式走 AppCompatDelegate。
    implementation(libs.androidx.appcompat)
    // ↓ Material 只当「主题 token 来源」用（`R.attr.colorSurface` 等 + 父主题
    //   Theme.Material3.DayNight），组件层一个都没用。它会传递带进 constraintlayout /
    //   recyclerview，所以那两条不用再单独声明。
    implementation(libs.google.material)
    constraints {
        implementation(libs.androidx.recyclerview) {
            because("Keep Material's transitive dependency on the validated version")
        }
    }
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    // material.ripple 不写 `ripple()`，但 miuix 的按压反馈运行时要用它 —— R8 会因
    // 缺类报错，所以这条是「看着没用、其实要留」的。
    implementation(libs.compose.material.ripple)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.miuix.preference.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zip4j)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
