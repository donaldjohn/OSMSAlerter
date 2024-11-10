package com.donaldjohn.smsalerter.receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import com.donaldjohn.smsalerter.MainActivity
import com.donaldjohn.smsalerter.alert.AlertManager
import com.donaldjohn.smsalerter.data.ContactManager

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            try {
                val bundle = intent.extras
                if (bundle != null) {
                    val pdus = bundle.get("pdus") as Array<*>
                    for (pdu in pdus) {
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                        val sender = smsMessage.originatingAddress ?: continue
                        MainActivity.appendLogStatic("=======================")
                        // 添加日志到 MainActivity
                        MainActivity.appendLogStatic("收到短信，发送者: $sender")
                        
                        // 规范化电话号码格式
                        val normalizedSender = normalizePhoneNumber(sender)
                        val contactManager = ContactManager.getInstance(context)
                        val monitoredContacts = contactManager.getContacts()
                        
                        MainActivity.appendLogStatic("监控的联系人列表: ${monitoredContacts.joinToString()}")
                        
                        if (monitoredContacts.any { normalizedSender.endsWith(normalizePhoneNumber(it)) }) {
                            MainActivity.appendLogStatic("匹配到监控号码，开始报警：$sender")
                            Toast.makeText(context, "收到监控号码的短信，开始报警", Toast.LENGTH_LONG).show()
                            
                            val alertManager = AlertManager(context)
                            alertManager.startAlert()
                            MainActivity.setAlertManager(alertManager)
                        } else {
                            MainActivity.appendLogStatic("未匹配到监控号码")
                        }
                    }
                }
            } catch (e: Exception) {
                MainActivity.appendLogStatic("处理短信时出错: ${e.message}")
                Log.e("SmsReceiver", "处理短信时出错", e)
                Toast.makeText(context, "处理短信时出错: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun normalizePhoneNumber(number: String): String {
        // 移除所有非数字字符
        return number.replace(Regex("[^0-9+]"), "")
    }
} 