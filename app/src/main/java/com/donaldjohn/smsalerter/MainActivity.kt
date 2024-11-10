package com.donaldjohn.smsalerter
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.TextView
import kotlinx.coroutines.coroutineScope
import com.donaldjohn.smsalerter.R
import com.donaldjohn.smsalerter.data.ContactManager
import com.donaldjohn.smsalerter.service.MonitorService
import com.donaldjohn.smsalerter.util.MiuiUtils
import android.content.pm.ServiceInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.donaldjohn.smsalerter.adapter.ContactsAdapter
import com.donaldjohn.smsalerter.alert.AlertManager
import java.util.*
import android.view.View
import android.widget.ScrollView
import android.graphics.Color
import android.graphics.Typeface
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private lateinit var contactManager: ContactManager
    private lateinit var contactsAdapter: ContactsAdapter
    private var alertManager: AlertManager? = null
    private lateinit var tvLog: TextView
    
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val PICK_CONTACT_REQUEST = 101
        
        @Volatile
        private var staticAlertManager: AlertManager? = null
        private var staticTvLog: TextView? = null
        
        private const val MAX_LOG_LINES = 500  // 添加最大日志行数常量
        
        fun getAlertManager(): AlertManager? = staticAlertManager
        fun setAlertManager(manager: AlertManager?) {
            staticAlertManager = manager
        }
        
        fun setLogTextView(textView: TextView) {
            staticTvLog = textView
        }
        
        fun appendLogStatic(message: String) {
            staticTvLog?.post {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val logMessage = "[$timestamp] $message\n"
                
                // 获取当前日志内容并按行分割
                val currentLog = staticTvLog?.text?.toString() ?: ""
                val lines = currentLog.split("\n").toMutableList()
                
                // 如果行数超过限制，移除旧的日志
                if (lines.size >= MAX_LOG_LINES) {
                    val linesToRemove = lines.size - MAX_LOG_LINES + 1
                    lines.subList(0, linesToRemove).clear()
                }
                
                // 添加新日志
                lines.add(logMessage.trim())
                
                // 更新文本视图
                staticTvLog?.text = lines.joinToString("\n")
                
                // 自动滚动到底部
                val scrollView = staticTvLog?.parent as? ScrollView
                scrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }

        private const val PREFS_NAME = "AppPreferences"
        private const val KEY_PERMISSIONS_CHECKED = "permissions_checked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        contactManager = ContactManager.getInstance(this)
        
        // 检查并请求权限
        checkAndRequestPermissions()
        
        // 其他初始化操作移到权限获取成功后
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.VIBRATE,
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).toMutableList()

        // 在 Android 13 及以上版本添加通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notGrantedPermissions = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (notGrantedPermissions.isNotEmpty()) {
                requestPermissions(notGrantedPermissions, PERMISSIONS_REQUEST_CODE)
            } else {
                initializeApp()
            }
        } else {
            initializeApp()
        }
    }

private fun initializeApp() {
    lifecycleScope.launch(Dispatchers.Default) {
        // 并行执行初始化任务
        coroutineScope {
            launch { contactManager.getContacts() }
            launch { startMonitorService() }
        }
        
        // UI操作放在最后
         withContext(Dispatchers.Main) {
             setupUI()
             if (MiuiUtils.isMiui()) {
                 checkMiuiPermissions()
             }
         }
//        setupUI()
//        if (MiuiUtils.isMiui()) {
//            checkMiuiPermissions()
//        }
    }
}

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeApp()
            } else {
                Toast.makeText(this, "需要相关权限才能正常运行", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startMonitorService() {
        lifecycleScope.launch(Dispatchers.Default) {
            val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serviceIntent.putExtra("foregroundServiceType", ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun setupUI() {
        // 初始化按钮和文本视图
        val btnAddContact = findViewById<Button>(R.id.btnAddContact)
        val btnStartTest = findViewById<Button>(R.id.btnStartTest)
        val btnStopTest = findViewById<Button>(R.id.btnStopTest)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val rvContacts = findViewById<RecyclerView>(R.id.rvContacts)
        
        // 设置RecyclerView
        rvContacts.layoutManager = LinearLayoutManager(this)
        contactsAdapter = ContactsAdapter(contactManager.getContacts().toList()) { contact ->
            contactManager.removeContact(contact)
            updateContactsList()
        }
        rvContacts.adapter = contactsAdapter
        
        // 设置按钮点击事件
        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }
        
        btnStartTest.setOnClickListener {
            alertManager = AlertManager(this)
            setAlertManager(alertManager)
            alertManager?.startAlert()
            appendLog("开始测试警报")
        }
        
        btnStopTest.setOnClickListener {
            alertManager?.stopAlert()
            getAlertManager()?.stopAlert()
            alertManager = null
            setAlertManager(null)
            appendLog("停止警报")
        }
        
        // 更新状态文本
        tvStatus.text = "服务正在运行中..."
        
        // 更新联系人列表
        updateContactsList()
        
        // 初始化日志控件
        tvLog = findViewById(R.id.tvLog)
        setLogTextView(tvLog)
        
        // 设置控制台风格
        tvLog.apply {
            setBackgroundColor(Color.BLACK)  // 黑色背景
            setTextColor(Color.GREEN)        // 绿色文字
            typeface = Typeface.MONOSPACE    // 等宽字体
            textSize = 12f                   // 字体大小
            setPadding(16, 16, 16, 16)      // 内边距
        }
        
        // 添加初始日志
        appendLog("应用启动完成")
    }

    // 添加日志追加方法
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        runOnUiThread {
            tvLog.append(logMessage)
            // 自动滚动到底部
            val scrollView = tvLog.parent as ScrollView
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // 添加更新联系人列表的方法
    private fun updateContactsList() {
        contactsAdapter.updateContacts(contactManager.getContacts().toList())
    }

    // 在onActivityResult中添加处理联系人选择的结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            val contactUri = data?.data ?: return
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val number = cursor.getString(numberIndex)
                    contactManager.addContact(number)
                    updateContactsList()
                }
            }
        }
    }
    
    private fun checkMiuiPermissions() {
        if (!MiuiUtils.isMiui()) return
        
        lifecycleScope.launch {
            val permissionChecks = listOf(
                PermissionCheck("通知类短信权限", ::isNotificationSmsAllowed, ::showNotificationSmsDialog),
                PermissionCheck("自启动权限", ::isAutoStartAllowed, ::showAutoStartDialog),
                PermissionCheck("后台运行权限", ::isBackgroundRunningAllowed, ::showBackgroundRunningDialog),
                PermissionCheck("电池优化权限", ::isBatteryOptimizationDisabled, ::requestBatteryOptimization)
            )

            var hasShownDialog = false
            
            for (check in permissionChecks) {
                if (!check.isGranted()) {
                    hasShownDialog = true
                    appendLog("缺少${check.name}，请授予权限")
                    withContext(Dispatchers.Main) {
                        check.showPermissionDialog()
                    }
                    // 等待一段时间再显示下一个权限对话框
                    delay(1000)
                }
            }

            if (!hasShownDialog) {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PERMISSIONS_CHECKED, true)
                    .apply()
                appendLog("MIUI权限检查完成")
            }
        }
    }

    private data class PermissionCheck(
        val name: String,
        val isGranted: () -> Boolean,
        val showPermissionDialog: () -> Unit
    )

    private fun isNotificationSmsAllowed(): Boolean {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAutoStartAllowed(): Boolean {
        try {
            // 尝试检查MIUI自启动管理器中的状态
            val manager = packageManager
            val intent = Intent()
            intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            
            // 如果能够解析这个Intent，说明是MIUI系统
            val activities = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (activities.isEmpty()) {
                // 非MIUI系统，默认允许
                return true
            }
            
            // 检查SharedPreferences中记录的状态
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean("autostart_enabled", false)
        } catch (e: Exception) {
            // 发生异常时返回true，避免反复提示
            return true
        }
    }

    private fun isBackgroundRunningAllowed(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun showAutoStartDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要自启动权限")
            .setMessage("请在接下来的界面中允许应用自启动，否则可能无法正常接收短信提醒")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent()
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                    startActivity(intent)
                    
                    // 记录用户已设置自启动权限
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("autostart_enabled", true)
                        .apply()
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开自启动设置，请手动设置", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBackgroundRunningDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要后台运行权限")
            .setMessage("请在接下来的界面中允许应用后台运行，并关闭省电限制")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent()
                    intent.component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开后台运行设置，请手动设置", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestBatteryOptimization() {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun showAddContactDialog() {
        val options = arrayOf("从通讯录选择", "手动输入号码")
        
        AlertDialog.Builder(this)
            .setTitle("添加联系人")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickContactFromAddressBook()
                    1 -> showManualInputDialog()
                }
            }
            .show()
    }

    private fun pickContactFromAddressBook() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        startActivityForResult(intent, PICK_CONTACT_REQUEST)
    }

    private fun showManualInputDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "请输入手机号码"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        
        AlertDialog.Builder(this)
            .setTitle("输入手机号码")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotEmpty()) {
                    contactManager.addContact(number)
                    updateContactsList()
                    appendLog("添加联系人: $number")
                } else {
                    Toast.makeText(this, "号码不能为空", Toast.LENGTH_SHORT).show()
                    appendLog("添加联系人失败: 号码为空")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNotificationSmsDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要通知类短信权限")
            .setMessage("请在接下来的界面中允许应用接收通知类短信，否则可能无法正常接收短信提醒")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter", 
                        "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkgname", packageName)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开权限设置，请手动设置", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}