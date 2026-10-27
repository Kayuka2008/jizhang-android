# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# ==== Room（KSP 生成的实现依赖实体字段名与构造函数签名） ====
# Room 的 consumer rule 只 keep 了 RoomDatabase 子类构造函数；这里额外保证
# 实体类（@Entity）及其字段名不被改名 —— DAO 生成的 SQL 里硬编码了这些字段名，
# 一旦被混淆成 a/b/c 会跟数据库列名对不上，导致运行时查询崩溃。
-keep class com.jizhang.app.data.db.entity.** { *; }
-keep class com.jizhang.app.data.db.dao.** { *; }
-keep class com.jizhang.app.data.db.AppDatabase { *; }

# Parcelable 的 CREATOR 由 proguard-android-optimize.txt 保留；其余成员按实际使用收缩。
# 应用入口由 Manifest 规则保留，直接调用的容器、设置存储和种子数据交给 R8 优化。

# ==== kyant backdrop / shapes（纯 Compose 绘制，无反射，理论上可安全混淆；保留注解） ====
-dontwarn com.kyant.**

# ==== Compose 常见坑 ====
# 保留 @Composable 相关注解，避免 compose 编译器生成代码被剥离。
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**

# ==== miuix（HyperOS 风格 Compose 库）====
# Compose 组件通过静态调用引用，交由 R8 删除未使用的控件和默认参数分支。
# 消费者规则仍由库自身提供，不再保留整个库。
-dontwarn top.yukonga.miuix.kmp.**
