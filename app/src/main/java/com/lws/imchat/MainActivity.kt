package com.lws.imchat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lws.imchat.bean.SingleMessage
import com.lws.imchat.event.CEventCenter
import com.lws.imchat.event.Events
import com.lws.imchat.event.ICEventListener
import com.lws.imchat.im.IMSClientBootstrap
import com.lws.imchat.im.MessageProcessor
import com.lws.imchat.im.MessageType
import com.lws.imchat.utils.CThreadPoolExecutor
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), ICEventListener {

    private var mEditText: EditText? = null
    private var mTextView: TextView? = null

    private var userId = "100002"
    private var token = "token_$userId"
    private var hosts = "[{\"host\":\"192.168.32.130\", \"port\":8855}]"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mEditText = findViewById(R.id.etContent)
        mTextView = findViewById(R.id.tvMsg)

        IMSClientBootstrap.instance.init(userId, token, hosts, 1)

        CEventCenter.registerEventListener(this, EVENTS)
    }

    fun sendMsg(view: View) {
        val message = SingleMessage()
        message.msgId = (UUID.randomUUID().toString())
        message.msgType = (MessageType.SINGLE_CHAT.msgType)
        message.msgContentType = (MessageType.MessageContentType.TEXT.msgContentType)
        message.fromId = (userId)
        message.toId = ("100001")
        message.timestamp = (System.currentTimeMillis())
        message.content = (etContent.text.toString())

        MessageProcessor.instance.sendMsg(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        CEventCenter.unregisterEventListener(this, EVENTS)
    }

    override fun onCEvent(topic: String, msgCode: Int, resultCode: Int, obj: Any) {
        when (topic) {
            Events.CHAT_SINGLE_MESSAGE -> {
                val message = obj as SingleMessage
                CThreadPoolExecutor.runOnMainThread(Runnable { tvMsg.text = message.content })
            }
            else -> {
            }
        }
    }

    companion object {
        private val EVENTS = arrayOf(Events.CHAT_SINGLE_MESSAGE)
    }
}
