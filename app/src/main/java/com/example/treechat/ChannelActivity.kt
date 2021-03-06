package com.example.treechat

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_channel.*
import kotlinx.android.synthetic.main.activity_channel_list.*
import java.lang.Math.abs
import java.lang.String.format
import java.text.MessageFormat.format
import java.time.*
import java.time.format.DateTimeFormatter

class ChannelActivity : AppCompatActivity() {
    var channelname: String=""
    var description: String=""
    private val fb = FirebaseDatabase.getInstance().reference
    private var members: MutableList<String> = ArrayList()
    private var messagelist: MutableList<String> = ArrayList()
    private var messagemap: HashMap<String, HashMap<String, String>> = HashMap()
    private var typeindicator1 = object : GenericTypeIndicator<HashMap<String, HashMap<String, String>>>(){}
    private var typeindicator2 = object : GenericTypeIndicator<HashMap<String, String>>(){}
    @RequiresApi(Build.VERSION_CODES.O)
    private var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a zzz")
    private lateinit var myAdapter1 : ArrayAdapter<String>
    private lateinit var myAdapter2 : ArrayAdapter<String>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel)
        channelname = intent.getStringExtra("chan_name")!!
        Log.d("chan_name", channelname.toString())
        channeltitle2.text = channelname

        supportActionBar?.setTitle(channelname)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Retrieve data in channel node
        val listOfChannels = fb.child("channel")
        Log.d("p3", "in child channel: " + listOfChannels.toString())
        val currchanneltree = listOfChannels.orderByChild("name").equalTo(channelname)
        Log.d("currchanneltree", currchanneltree.toString())
        // This event listener is meant to keep listening to query or database reference it is
        // attached to
        currchanneltree.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                // do something with data
                Log.d("p5 currchanneltree", data.key + ": " + data.value)
                processData(data, channelname)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // report/log the error
                Log.d("p6", "Data didn't arrive")
            }
        })
    }

    fun processData(arr: DataSnapshot, channelname: String) {
        if (!arr.hasChildren()) {
            Toast.makeText(this,
                "No such channel $channelname", Toast.LENGTH_SHORT).show()
            return
        }

        val data = arr.children.iterator().next()
        val channelinfo = data.getValue(Channel::class.java)!!

//        val channelinfo = arr.getValue(Channel::class.java)!!

        // Currently there is no description attribute, removed it from Channel data class as well
//        description = channelinfo.description

        val membersmap = channelinfo.members
        for (key in membersmap) {
            if (key.value !in members) {
                members.add(key.value)
            }
        }
        Log.d("members", members.toString())
        setupMembers()

        val currChannelMsgIDs = ArrayList<String>()
        val messagesmap = channelinfo.messages
        for (key in messagesmap) {
            currChannelMsgIDs.add(key.key)
        }

        //TODO: retrieve data from message section in tree, not in the same place as channelinfo (DONE)
        // TODO: Fix bug where messagemap is not retrieving all the messages (DONE)
        // This bug was because of the persistence state saving old messages, added code to keep
        // all three main nodes of the Firebase database synchronized
        val messagetree = fb.child("message")
        messagetree.addListenerForSingleValueEvent(object: ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(data: DataSnapshot) {
                setupMessages(data, currChannelMsgIDs)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // report/log the error
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setupMessages(data: DataSnapshot, currChannelMsgIDs: MutableList<String>) {

        Log.d("currChannelMsgIDs", currChannelMsgIDs.toString())
        if (currChannelMsgIDs.isEmpty()) {
            myAdapter1 = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagelist)
            channelchat.adapter = myAdapter1
            myAdapter1.notifyDataSetChanged()
            return
        }
        messagemap = data.getValue(typeindicator1)!!
        Log.d("Messagemap", messagemap.toString())
        val messagemapfroms = ArrayList<String>()
        val messagemapmsgtexts = ArrayList<String>()
        val messagemaptimestamps = ArrayList<String>()
        val messagemapZDTs = ArrayList<ZonedDateTime>()
        val listOfMsgObjs = ArrayList<Message>()

        // Populate message attribute arraylists with data
        for (key in currChannelMsgIDs) {
            val from = messagemap[key]!!["from"]!!
            messagemapfroms.add(from)
            val msgtext = messagemap[key]!!["text"]!!
            messagemapmsgtexts.add(msgtext)
            val rawTimestampZDT = messagemap[key]!!["rawTimestampZDT"]!!
            messagemaptimestamps.add(rawTimestampZDT)
        }

        Log.d("currChannelMsgIDs", currChannelMsgIDs.toString())
        Log.d("messagemapfroms", messagemapfroms.toString())
        Log.d("messagemapmsgtexts", messagemapmsgtexts.toString())
        Log.d("messagemaptimestamps", messagemaptimestamps.toString())

//         Convert timestamps to ZonedDateTime objects
        for (timestamp in messagemaptimestamps) {
            val ZDT = ZonedDateTime.parse(timestamp)
            messagemapZDTs.add(ZDT)
        }

        Log.d("messagemapZDTs", messagemapZDTs.toString())

        // Create Message objects and put in list to be sorted
        for (i in 0 until currChannelMsgIDs.size) {
            val Messageobj = Message(currChannelMsgIDs.get(i), messagemapfroms.get(i),
                messagemapmsgtexts.get(i), messagemapZDTs.get(i))
            listOfMsgObjs.add(Messageobj)
        }
        Log.d("listOfMsgObjs", listOfMsgObjs.toString())

        // .sort() sorts in-place, needs mutable list. .sorted() returns new list
        listOfMsgObjs.sort()

        for (msg in listOfMsgObjs) {
            val finalmessage = msg.toString()
            if (messagelist.contains(finalmessage)) {
                continue
            } else {
                messagelist.add(finalmessage)
            }
        }

        Log.d("messagelist", messagelist.toString())
        myAdapter1 = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagelist)
        channelchat.adapter = myAdapter1
        myAdapter1.notifyDataSetChanged()
    }

    fun setupMembers() {
        myAdapter2 = ArrayAdapter(this, android.R.layout.simple_list_item_1, members)
        memberlist.adapter = myAdapter2
        myAdapter2.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendMessageClick (view: View) {
        val sharedPref = getSharedPreferences("test", Context.MODE_PRIVATE)
        val from = sharedPref.getString(WelcomeActivity.currentUserKey, "default").toString()
        Log.d("ca1-username", from)
        val msgtext = messagetext.text.toString()
        Log.d("ca2-msgtext", msgtext)
        var DateTimeZone = ZonedDateTime.now()
        val timestamp = DateTimeZone.format(formatter)
        Log.d("ca2-timestamp", timestamp)

        val channelmessagenode = fb.child("channel/$channelname/messages")
        val channelmessagekey = channelmessagenode.push().key.toString()
        channelmessagenode.child(channelmessagekey).setValue(channelmessagekey)

        val messagenode = fb.child("message").child(channelmessagekey)
        messagenode.child("from").setValue(from)
        messagenode.child("text").setValue(msgtext)
        messagenode.child("timestamp").setValue(timestamp)
        messagenode.child("rawTimestampZDT").setValue(DateTimeZone.toString())
        messagetext.text.clear()
    }

}
