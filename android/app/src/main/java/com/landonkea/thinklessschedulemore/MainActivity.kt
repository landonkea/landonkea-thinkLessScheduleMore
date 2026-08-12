// ───────────────────────────────────────────────────────────────────
// MainActivity, the app's only screen
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
// No tabs, no navigation, everything visible at once.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // ── Our data stores ───────────────────────────────────────────
    private lateinit var store: MessageStore
    private lateinit var recurringStore: RecurringMessageStore
    private lateinit var noSendDayStore: NoSendDayStore
    private lateinit var priorityStore: MessagePriorityStore

    // ── UI references (so we can update them when data changes) ───
    private lateinit var messageListText: TextView
    private lateinit var historyText: TextView
    private lateinit var recipientInput: EditText
    private lateinit var recipientNameInput: EditText
    private lateinit var masterSwitch: Switch
    private lateinit var hourStartLabel: TextView
    private lateinit var hourEndLabel: TextView
    private lateinit var hourStartSeek: SeekBar
    private lateinit var hourEndSeek: SeekBar
    private lateinit var maxPerDayLabel: TextView
    private lateinit var maxPerDaySeek: SeekBar
    private lateinit var intervalLabel: TextView
    private lateinit var intervalSeek: SeekBar
    private lateinit var nextSendLabel: TextView
    private lateinit var recurringListText: TextView
    private lateinit var noSendDaysText: TextView

    // ── Contact picker plumbing ─────────────────────────────────────
    // Two-step flow: request READ_CONTACTS (if not already granted),
    // then, only on grant, launch the system contact picker. Both
    // are registered as activity-result launchers up front (required
    // before onStart), not created lazily on click.
    private lateinit var pickContactLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var requestContactsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    // ── Runtime permission request codes ─────────────────────────
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100

        // Standard single-segment SMS length. Longer messages still send
        // (multi-part), but we surface this as the design guideline.
        private const val SMS_MAX_LENGTH = 160

        // Loose E.164-ish check: optional leading +, 8-15 digits total.
        // Deliberately permissive, real validation happens at the
        // carrier, this is just a "did you fat-finger this" guard.
        private val PHONE_REGEX = Regex("^\\+?[0-9]{8,15}$")

        // Display names for Calendar.SUNDAY(1)..Calendar.SATURDAY(7),
        // index 0 unused so the Calendar constant can index straight in.
        private val WEEKDAY_NAMES = arrayOf(
            "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MessageStore(this)
        recurringStore = RecurringMessageStore(this)
        noSendDayStore = NoSendDayStore(this)
        priorityStore = MessagePriorityStore(this)

        // ── Contact picker launchers ──────────────────────────────
        // Must be registered unconditionally in onCreate (before the
        // activity reaches STARTED), registering lazily inside a click
        // handler would throw.
        pickContactLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { contactUri -> loadPickedContact(contactUri) }
            }
        }
        requestContactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchContactPicker()
            } else {
                // Graceful fallback, the manual EditText still works fine,
                // so just let the user know instead of crashing/looping.
                Toast.makeText(
                    this,
                    "Contacts permission denied, you can still type the number manually",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ── Request dangerous permissions at runtime ─────────────
        // On Android 6+ (API 23+), SEND_SMS is a "dangerous" permission.
        // On Android 13+ (API 33+), POST_NOTIFICATIONS is also dangerous.
        // Android only shows the dialog once; if denied permanently,
        // the user must enable in Settings.
        requestRequiredPermissions()

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

        // ── Pick from Contacts, an alternative to typing the number
        // by hand. Requests READ_CONTACTS at runtime (if not already
        // granted) before launching the system contact picker; denial
        // just falls back to the manual EditText above, no crash.
        root.addView(Button(this).apply {
            text = "📇 Pick Contact"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    launchContactPicker()
                } else {
                    requestContactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            }
        })

        // ── Recipient display name, feeds the {name} template
        // placeholder (see MessageTemplate). Purely cosmetic: the
        // phone number is what actually gets texted either way.
        root.addView(TextView(this).apply {
            text = "Recipient's name (for {name} in messages)"
        })
        recipientNameInput = EditText(this).apply {
            hint = "e.g. Sam"
            setText(store.getRecipientName())
        }
        root.addView(recipientNameInput)

        // Save button for recipient.
        root.addView(Button(this).apply {
            text = "Save Number"
            setOnClickListener {
                val number = recipientInput.text.toString().trim()
                if (number.isNotEmpty() && !PHONE_REGEX.matches(number)) {
                    Toast.makeText(
                        this@MainActivity,
                        "That doesn't look like a valid phone number (e.g. +14155551234)",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                store.saveRecipient(number)
                store.saveRecipientName(recipientNameInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "Number saved", Toast.LENGTH_SHORT).show()
            }
        })

        // ── Section: Master Switch ──────────────────────────────
        root.addView(TextView(this).apply {
            text = "\n⚡ Scheduling"
            textSize = 18f
        })

        nextSendLabel = TextView(this).apply {
            text = formatNextSend(store.getNextSendTime())
        }
        root.addView(nextSendLabel)

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
                    store.clearNextSendTime()
                    Toast.makeText(this@MainActivity,
                        "Scheduling paused", Toast.LENGTH_SHORT).show()
                }
                nextSendLabel.text = formatNextSend(store.getNextSendTime())
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
        root.addView(TextView(this).apply {
            text = "Tip: use {name} and {time-of-day} for variety, e.g. \"Good {time-of-day}, {name}!\""
            textSize = 12f
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

        // Edit message button.
        root.addView(Button(this).apply {
            text = "✏️ Edit Message"
            setOnClickListener {
                showEditMessageDialog()
            }
        })

        // Remove message button.
        root.addView(Button(this).apply {
            text = "❌ Remove Message"
            setOnClickListener {
                showRemoveMessageDialog()
            }
        })

        // ── Section: Recurring Messages ─────────────────────────
        // Yearly date-based messages (birthdays, anniversaries, etc.)
        //, additive to the random message pool above. See
        // RecurringMessageStore/RecurringMessageMatcher/SchedulerService.
        root.addView(TextView(this).apply {
            text = "\n🎂 Recurring Messages"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Guaranteed to send every year on this date (e.g. a birthday)."
            textSize = 12f
        })

        recurringListText = TextView(this).apply {
            text = formatRecurringMessages(recurringStore.getRecurringMessages())
        }
        root.addView(recurringListText)

        root.addView(Button(this).apply {
            text = "➕ Add Recurring Message"
            setOnClickListener {
                showAddRecurringMessageDialog()
            }
        })

        root.addView(Button(this).apply {
            text = "❌ Remove Recurring Message"
            setOnClickListener {
                showRemoveRecurringMessageDialog()
            }
        })

        // ── Section: No-Send Days ───────────────────────────────
        // Pauses the random pool schedule (only, see NoSendDayChecker's
        // doc comment: recurring birthday/anniversary messages above
        // still fire) on chosen weekdays and/or specific one-off dates.
        root.addView(TextView(this).apply {
            text = "\n🚫 No-Send Days"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Pauses random messages (recurring ones above still send)."
            textSize = 12f
        })

        noSendDaysText = TextView(this).apply {
            text = formatNoSendDays()
        }
        root.addView(noSendDaysText)

        root.addView(Button(this).apply {
            text = "📅 Choose Weekly No-Send Days"
            setOnClickListener {
                showNoSendWeekdaysDialog()
            }
        })

        root.addView(Button(this).apply {
            text = "➕ Add One-Off No-Send Date"
            setOnClickListener {
                showAddNoSendDateDialog()
            }
        })

        root.addView(Button(this).apply {
            text = "❌ Remove One-Off No-Send Date"
            setOnClickListener {
                showRemoveNoSendDateDialog()
            }
        })

        // ── Section: Message Priority ───────────────────────────
        // Weights a message's odds of being picked next by the random
        // pool schedule (see WeightedMessageSelector). Everything
        // defaults to normal priority until the user bumps one up.
        root.addView(TextView(this).apply {
            text = "\n⭐ Message Priority"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Higher priority messages send more often (1=normal, 10=favorite)."
            textSize = 12f
        })

        root.addView(Button(this).apply {
            text = "⭐ Set Message Priority"
            setOnClickListener {
                showSetPriorityDialog()
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

        // Stats dashboard button (success rate, sends/day, top messages).
        root.addView(Button(this).apply {
            text = "📊 Stats"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, StatsActivity::class.java))
            }
        })
    }

    // ── Request SMS + notification permissions at runtime ─────────
    // Without these, the app crashes or the foreground service fails.
    private fun requestRequiredPermissions() {
        // Collect all permissions we haven't been granted yet.
        val permissionsToRequest = mutableListOf<String>()

        // SEND_SMS is dangerous on ALL versions of Android.
        // Without it, SmsManager.sendTextMessage() throws SecurityException.
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.SEND_SMS)
        }

        // POST_NOTIFICATIONS is only dangerous on Android 13+.
        // Without it, the foreground service notification is silently blocked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // If there are any ungranted permissions, show the system dialog.
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    // ── Handle the result of the permission request ───────────────
    // Shows a Toast explaining what was denied, so the user knows.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        "Permission denied: ${permissions[i]}. " +
                                "Some features may not work.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Refresh all UI elements from SharedPreferences ────────────
    private fun refreshUI() {
        recipientInput.setText(store.getRecipient())
        recipientNameInput.setText(store.getRecipientName())
        masterSwitch.isChecked = store.isEnabled()
        hourStartSeek.progress = store.getHourStart()
        hourEndSeek.progress = store.getHourEnd()
        maxPerDaySeek.progress = store.getMaxPerDay()
        intervalSeek.progress = store.getMinInterval()
        messageListText.text = formatMessages(store.getMessages())
        recurringListText.text = formatRecurringMessages(recurringStore.getRecurringMessages())
        noSendDaysText.text = formatNoSendDays()
        historyText.text = formatHistory(store.getSentLog())
        nextSendLabel.text = formatNextSend(store.getNextSendTime())

        hourStartLabel.text = "Start: ${store.getHourStart()}:00"
        hourEndLabel.text = "End: ${store.getHourEnd()}:00"
        maxPerDayLabel.text = "Max per day: ${store.getMaxPerDay()}"
        intervalLabel.text = "Min interval: ${store.getMinInterval()} minutes"
    }

    // ── Build a message-editing view: a text box + live "N/160" counter ──
    // Shared by both the Add and Edit dialogs so the character-count
    // behavior (and the 160-char SMS design guideline) stays in one place.
    private fun buildMessageEditView(initialText: String): Pair<LinearLayout, EditText> {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val input = EditText(this).apply {
            hint = "Type your message here..."
            setText(initialText)
            setSelection(initialText.length)
        }

        val counter = TextView(this).apply {
            text = "${initialText.length}/$SMS_MAX_LENGTH"
        }

        fun updateCounter(length: Int) {
            counter.text = "$length/$SMS_MAX_LENGTH"
            counter.setTextColor(
                if (length > SMS_MAX_LENGTH)
                    android.graphics.Color.RED
                else
                    android.graphics.Color.GRAY
            )
        }
        updateCounter(initialText.length)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCounter(s?.length ?: 0)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        container.addView(input)
        container.addView(counter)
        return Pair(container, input)
    }

    // ── Show dialog to add a new message ──────────────────────────
    private fun showAddMessageDialog() {
        val (view, input) = buildMessageEditView("")

        AlertDialog.Builder(this)
            .setTitle("New Message")
            .setView(view)
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

    // ── Show dialog to pick, then edit, an existing message ────────
    private fun showEditMessageDialog() {
        val messages = store.getMessages()
        if (messages.isEmpty()) {
            Toast.makeText(this, "No messages to edit. Add one first!", Toast.LENGTH_SHORT).show()
            return
        }

        val messageItems = messages.mapIndexed { index, msg ->
            "${index + 1}. ${msg.take(50)}${if (msg.length > 50) "…" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Edit Which Message?")
            .setItems(messageItems) { _, which ->
                showEditMessageTextDialog(which, messages[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Dialog to actually edit the text of message at `index` ─────
    private fun showEditMessageTextDialog(index: Int, currentText: String) {
        val (view, input) = buildMessageEditView(currentText)

        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    store.updateMessage(index, text)
                    refreshUI()
                    Toast.makeText(this, "Message updated!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Show dialog to remove a message ─────────────────────────
    private fun showRemoveMessageDialog() {
        val messages = store.getMessages()
        if (messages.isEmpty()) {
            // No messages to remove, show a brief hint instead of an empty dialog.
            Toast.makeText(this, "No messages to remove. Add one first!", Toast.LENGTH_SHORT).show()
            return
        }

        // Build a list of message previews for the user to pick from.
        val messageItems = messages.mapIndexed { index, msg ->
            "${index + 1}. ${msg.take(50)}${if (msg.length > 50) "…" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Remove a Message")
            .setItems(messageItems) { _, which ->
                // which = the index in the array the user tapped.
                // Confirm before deleting, there's no undo once it's gone.
                confirmRemoveMessage(which, messages[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Confirmation step before actually deleting a message ───────
    private fun confirmRemoveMessage(index: Int, text: String) {
        val preview = if (text.length > 80) text.take(80) + "…" else text
        AlertDialog.Builder(this)
            .setTitle("Delete this message?")
            .setMessage("❝$preview❞\n\nThis can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                store.removeMessage(index)
                refreshUI()
                Toast.makeText(this, "Message removed!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Format messages for display ───────────────────────────────
    private fun formatMessages(messages: List<String>): String {
        if (messages.isEmpty()) return "(No messages yet, tap Add Message)"
        return messages.mapIndexed { i, msg -> "${i + 1}. ❝$msg❞" }
            .joinToString("\n")
    }

    // ── Format history for display ────────────────────────────────
    private fun formatHistory(log: List<SentLogEntry>): String {
        if (log.isEmpty()) return "(No messages sent yet)"
        val formatter = java.text.SimpleDateFormat("MMM d h:mm a", java.util.Locale.getDefault())
        return log.take(10).joinToString("\n") { entry ->
            val time = formatter.format(java.util.Date(entry.timestamp))
            val status = if (entry.status == SendStatus.SENT || entry.status == SendStatus.DELIVERED) "✅" else "❌"
            "$status $time, ${entry.message.take(50)}"
        }
    }

    // ── Format the "next scheduled send" line ───────────────────────
    private fun formatNextSend(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Next message: not scheduled"
        val formatter = java.text.SimpleDateFormat("MMM d h:mm a", java.util.Locale.getDefault())
        return "Next message: ${formatter.format(java.util.Date(timestampMs))}"
    }

    // ── Contact picker ───────────────────────────────────────────
    // Launches the system contact picker, filtered to contacts that
    // have a phone number (ACTION_PICK on the Phone content URI already
    // implies "has at least one phone number").
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    // Reads the phone number (and display name, if present) off the
    // contact URI returned by the picker and populates the recipient
    // fields, same two fields the manual EditText / Save Number flow
    // already writes to.
    private fun loadPickedContact(contactUri: Uri) {
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                if (numberIndex != -1) {
                    // Strip formatting (spaces/dashes/parens) so it lines up
                    // with PHONE_REGEX's expectations, but keep a leading '+'.
                    val rawNumber = cursor.getString(numberIndex) ?: ""
                    val cleaned = rawNumber.replace(Regex("[^0-9+]"), "")
                    recipientInput.setText(cleaned)
                }
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        recipientNameInput.setText(name)
                    }
                }
            }
        }
    }

    // ── Recurring messages: add/remove dialogs ───────────────────
    // Mirrors the message-pool add/remove pattern above, kept simpler
    // (no edit-in-place, remove and re-add covers the rare edit case).
    private fun showAddRecurringMessageDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val monthInput = EditText(this).apply {
            hint = "Month (1-12)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val dayInput = EditText(this).apply {
            hint = "Day (1-31)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val messageInput = EditText(this).apply {
            hint = "e.g. Happy Birthday, {name}! 🎉"
        }

        container.addView(TextView(this).apply { text = "Month" })
        container.addView(monthInput)
        container.addView(TextView(this).apply { text = "Day" })
        container.addView(dayInput)
        container.addView(TextView(this).apply { text = "Message" })
        container.addView(messageInput)

        AlertDialog.Builder(this)
            .setTitle("New Recurring Message")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val month = monthInput.text.toString().trim().toIntOrNull()
                val day = dayInput.text.toString().trim().toIntOrNull()
                val text = messageInput.text.toString().trim()

                if (month == null || month !in 1..12 || day == null || day !in 1..31 || text.isEmpty()) {
                    Toast.makeText(this, "Enter a valid month (1-12), day (1-31), and message", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                recurringStore.addRecurringMessage(month, day, text)
                refreshUI()
                Toast.makeText(this, "Recurring message added!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveRecurringMessageDialog() {
        val entries = recurringStore.getRecurringMessages()
        if (entries.isEmpty()) {
            Toast.makeText(this, "No recurring messages to remove. Add one first!", Toast.LENGTH_SHORT).show()
            return
        }

        val items = entries.map { entry ->
            "${entry.month}/${entry.day}, ${entry.message.take(40)}${if (entry.message.length > 40) "…" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Remove a Recurring Message")
            .setItems(items) { _, which ->
                recurringStore.removeRecurringMessage(entries[which].id)
                refreshUI()
                Toast.makeText(this, "Recurring message removed!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Format recurring messages for display ───────────────────────
    private fun formatRecurringMessages(entries: List<RecurringMessage>): String {
        if (entries.isEmpty()) return "(No recurring messages yet, tap Add Recurring Message)"
        return entries.joinToString("\n") { entry ->
            "${entry.month}/${entry.day}, ❝${entry.message}❞"
        }
    }

    // ── No-Send Days ─────────────────────────────────────────────

    private fun formatNoSendDays(): String {
        val weekdays = noSendDayStore.getNoSendWeekdays()
        val dates = noSendDayStore.getNoSendDates()
        if (weekdays.isEmpty() && dates.isEmpty()) return "(No no-send days set)"

        val lines = mutableListOf<String>()
        if (weekdays.isNotEmpty()) {
            lines.add(weekdays.sorted().joinToString(", ") { WEEKDAY_NAMES[it] })
        }
        dates.forEach { lines.add(it) }
        return lines.joinToString("\n")
    }

    // Multi-choice dialog, one row per weekday, pre-checked from
    // whatever's already blocked. Applies on OK, same as the recurring
    // message dialogs use Add/Cancel rather than writing on every tap.
    private fun showNoSendWeekdaysDialog() {
        val calendarWeekdays = intArrayOf(
            java.util.Calendar.SUNDAY, java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY,
            java.util.Calendar.SATURDAY
        )
        val labels = calendarWeekdays.map { WEEKDAY_NAMES[it] }.toTypedArray()
        val current = noSendDayStore.getNoSendWeekdays()
        val checked = calendarWeekdays.map { it in current }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("No-Send Weekdays")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val selected = calendarWeekdays.filterIndexed { i, _ -> checked[i] }.toSet()
                noSendDayStore.setNoSendWeekdays(selected)
                refreshUI()
                Toast.makeText(this, "No-send weekdays saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // One-off date entry via three number fields, matching the
    // month/day EditText style the recurring-message dialog already uses.
    private fun showAddNoSendDateDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val yearInput = EditText(this).apply {
            hint = "Year (e.g. 2026)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val monthInput = EditText(this).apply {
            hint = "Month (1-12)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val dayInput = EditText(this).apply {
            hint = "Day (1-31)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        container.addView(TextView(this).apply { text = "Year" })
        container.addView(yearInput)
        container.addView(TextView(this).apply { text = "Month" })
        container.addView(monthInput)
        container.addView(TextView(this).apply { text = "Day" })
        container.addView(dayInput)

        AlertDialog.Builder(this)
            .setTitle("New No-Send Date")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val year = yearInput.text.toString().trim().toIntOrNull()
                val month = monthInput.text.toString().trim().toIntOrNull()
                val day = dayInput.text.toString().trim().toIntOrNull()

                if (year == null || month == null || month !in 1..12 || day == null || day !in 1..31) {
                    Toast.makeText(this, "Enter a valid year, month (1-12), and day (1-31)", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val dateKey = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day)
                noSendDayStore.addNoSendDate(dateKey)
                refreshUI()
                Toast.makeText(this, "No-send date added!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveNoSendDateDialog() {
        val dates = noSendDayStore.getNoSendDates()
        if (dates.isEmpty()) {
            Toast.makeText(this, "No one-off no-send dates to remove.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Remove a No-Send Date")
            .setItems(dates.toTypedArray()) { _, which ->
                noSendDayStore.removeNoSendDate(dates[which])
                refreshUI()
                Toast.makeText(this, "No-send date removed!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Message Priority ─────────────────────────────────────────

    // Pick which message, same list-of-previews pattern as
    // showEditMessageDialog, then a SeekBar to set its weight.
    private fun showSetPriorityDialog() {
        val messages = store.getMessages()
        if (messages.isEmpty()) {
            Toast.makeText(this, "No messages yet. Add one first!", Toast.LENGTH_SHORT).show()
            return
        }

        val messageItems = messages.mapIndexed { index, msg ->
            "${index + 1}. ${msg.take(50)}${if (msg.length > 50) "…" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Set Priority For Which Message?")
            .setItems(messageItems) { _, which ->
                showPriorityWeightDialog(messages[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPriorityWeightDialog(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val currentWeight = priorityStore.getWeight(message)
        val weightLabel = TextView(this).apply {
            text = "Priority: $currentWeight"
        }
        val weightSeek = SeekBar(this).apply {
            max = MessagePriorityStore.PRIORITY_MAX - MessagePriorityStore.PRIORITY_MIN
            progress = currentWeight - MessagePriorityStore.PRIORITY_MIN
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    weightLabel.text = "Priority: ${value + MessagePriorityStore.PRIORITY_MIN}"
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }

        container.addView(TextView(this).apply {
            text = "❝${message.take(80)}${if (message.length > 80) "…" else ""}❞"
        })
        container.addView(weightLabel)
        container.addView(weightSeek)

        AlertDialog.Builder(this)
            .setTitle("Message Priority")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                priorityStore.setWeight(message, weightSeek.progress + MessagePriorityStore.PRIORITY_MIN)
                Toast.makeText(this, "Priority saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
