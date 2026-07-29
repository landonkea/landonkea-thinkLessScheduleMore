// ───────────────────────────────────────────────────────────────────
// MainActivity — the app's only screen
// ───────────────────────────────────────────────────────────────────
// This is where the user:
//   1. Enters their partner's phone number
//   2. Writes messages for the pool (add/remove)
//   3. Sets the time window (9 AM → 9 PM)
//   4. Sets messages per day and minimum interval
//   5. Flips the master ON/OFF switch
//   6. Views send history
//
// Simple layout: one scrollable screen with sections.
// No tabs, no navigation — everything visible at once.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // ── Our data store ────────────────────────────────────────────
    private lateinit var store: MessageStore

    // ── UI references (so we can update them when data changes) ───
    private lateinit var messageListText: TextView
    private lateinit var historyText: TextView
    private lateinit var recipientInput: EditText
    private lateinit var masterSwitch: Switch
    private lateinit var hourStartLabel: TextView
    private lateinit var hourEndLabel: TextView
    private lateinit var hourStartSeek: SeekBar
    private lateinit var hourEndSeek: SeekBar
    private lateinit var maxPerDayLabel: TextView
    private lateinit var maxPerDaySeek: SeekBar
    private lateinit var intervalLabel: TextView
    private lateinit var intervalSeek: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MessageStore(this)

        // ── Build the UI programmatically (no XML needed) ─────────
        // Using code instead of XML keeps everything in one place.
        // For a 2-screen app, this is cleaner than a layout file.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Wrap in ScrollView so it works on small screens.
        val scrollView = ScrollView(this)
        scrollView.addView(root)
        setContentView(scrollView)

        // ── Section: Recipient ──────────────────────────────────
        root.addView(TextView(this).apply {
            text = "👤 Recipient Phone Number"
            textSize = 18f
        })

        recipientInput = EditText(this).apply {
            hint = "+14155551234"
            setText(store.getRecipient())
        }
        root.addView(recipientInput)

        // Save button for recipient.
        root.addView(Button(this).apply {
            text = "Save Number"
            setOnClickListener {
                store.saveRecipient(recipientInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "Number saved", Toast.LENGTH_SHORT).show()
            }
        })

        // ── Section: Master Switch ──────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n⚡ Scheduling"
            textSize = 18f
        })

        masterSwitch = Switch(this).apply {
            text = "Enabled"
            isChecked = store.isEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                store.setEnabled(isChecked)
                if (isChecked) {
                    // Start the background service.
                    val intent = Intent(this@MainActivity, SchedulerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Toast.makeText(this@MainActivity,
                        "Scheduling started 💕", Toast.LENGTH_SHORT).show()
                } else {
                    // Stop the service.
                    val intent = Intent(this@MainActivity, SchedulerService::class.java)
                    stopService(intent)
                    Toast.makeText(this@MainActivity,
                        "Scheduling paused", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(masterSwitch)

        // ── Section: Time Window ────────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n⏰ Time Window"
            textSize = 18f
        })

        hourStartLabel = TextView(this).apply {
            text = "Start: ${store.getHourStart()}:00"
        }
        root.addView(hourStartLabel)

        hourStartSeek = SeekBar(this).apply {
            max = 23  // 0-23 hours
            progress = store.getHourStart()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    hourStartLabel.text = "Start: $value:00"
                    store.saveHourStart(value)
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }
        root.addView(hourStartSeek)

        hourEndLabel = TextView(this).apply {
            text = "End: ${store.getHourEnd()}:00"
        }
        root.addView(hourEndLabel)

        hourEndSeek = SeekBar(this).apply {
            max = 23
            progress = store.getHourEnd()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    hourEndLabel.text = "End: $value:00"
                    store.saveHourEnd(value)
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }
        root.addView(hourEndSeek)

        // ── Section: Limits ─────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n📊 Limits"
            textSize = 18f
        })

        maxPerDayLabel = TextView(this).apply {
            text = "Max per day: ${store.getMaxPerDay()}"
        }
        root.addView(maxPerDayLabel)

        maxPerDaySeek = SeekBar(this).apply {
            max = 20  // 0-20 messages per day
            progress = store.getMaxPerDay()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    maxPerDayLabel.text = "Max per day: $value"
                    store.saveMaxPerDay(value)
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }
        root.addView(maxPerDaySeek)

        intervalLabel = TextView(this).apply {
            text = "Min interval: ${store.getMinInterval()} minutes"
        }
        root.addView(intervalLabel)

        intervalSeek = SeekBar(this).apply {
            max = 240  // Up to 4 hours
            progress = store.getMinInterval()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    intervalLabel.text = "Min interval: $value minutes"
                    store.saveMinInterval(value)
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }
        root.addView(intervalSeek)

        // ── Section: Message Pool ───────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n💬 Message Pool"
            textSize = 18f
        })

        messageListText = TextView(this).apply {
            text = formatMessages(store.getMessages())
        }
        root.addView(messageListText)

        // Add message button.
        root.addView(Button(this).apply {
            text = "➕ Add Message"
            setOnClickListener {
                showAddMessageDialog()
            }
        })

        // ── Section: Send History ───────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n📋 Send History"
            textSize = 18f
        })

        historyText = TextView(this).apply {
            text = formatHistory(store.getSentLog())
        }
        root.addView(historyText)

        // Refresh button (re-reads data from SharedPreferences).
        root.addView(Button(this).apply {
            text = "🔄 Refresh"
            setOnClickListener {
                refreshUI()
            }
        })
    }

    // ── Refresh all UI elements from SharedPreferences ────────────
    private fun refreshUI() {
        recipientInput.setText(store.getRecipient())
        masterSwitch.isChecked = store.isEnabled()
        hourStartSeek.progress = store.getHourStart()
        hourEndSeek.progress = store.getHourEnd()
        maxPerDaySeek.progress = store.getMaxPerDay()
        intervalSeek.progress = store.getMinInterval()
        messageListText.text = formatMessages(store.getMessages())
        historyText.text = formatHistory(store.getSentLog())

        hourStartLabel.text = "Start: ${store.getHourStart()}:00"
        hourEndLabel.text = "End: ${store.getHourEnd()}:00"
        maxPerDayLabel.text = "Max per day: ${store.getMaxPerDay()}"
        intervalLabel.text = "Min interval: ${store.getMinInterval()} minutes"
    }

    // ── Show dialog to add a new message ──────────────────────────
    private fun showAddMessageDialog() {
        val input = EditText(this)
        input.hint = "Type your message here..."

        AlertDialog.Builder(this)
            .setTitle("New Message")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    store.addMessage(text)
                    refreshUI()
                    Toast.makeText(this, "Message added!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Format messages for display ───────────────────────────────
    private fun formatMessages(messages: List<String>): String {
        if (messages.isEmpty()) return "(No messages yet — tap Add Message)"
        return messages.mapIndexed { i, msg -> "${i + 1}. ❝$msg❞" }
            .joinToString("\n")
    }

    // ── Format history for display ────────────────────────────────
    private fun formatHistory(log: List<String>): String {
        if (log.isEmpty()) return "(No messages sent yet)"
        return log.take(10).joinToString("\n") { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3) {
                val time = java.text.SimpleDateFormat("MMM d h:mm a",
                    java.util.Locale.getDefault()).format(java.util.Date(parts[0].toLong()))
                val status = if (parts[1] == "sent") "✅" else "❌"
                "$status $time — ${parts[2].take(50)}"
            } else {
                entry
            }
        }
    }
}
