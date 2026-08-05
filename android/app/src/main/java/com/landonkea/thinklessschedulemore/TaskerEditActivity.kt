// ───────────────────────────────────────────────────────────────────
// TaskerEditActivity — Tasker opens this when the user adds/edits
// one of our actions inside their own task editor
// ───────────────────────────────────────────────────────────────────
// This is the "EDIT_SETTING" half of the Locale/Tasker plugin
// contract (see TaskerFireReceiver's class comment for the other
// half). Tasker launches this Activity for a result; it must finish
// with RESULT_OK and two extras set: EXTRA_BUNDLE (opaque data Tasker
// will store and hand back verbatim at fire time — see
// TaskerBundleCodec) and EXTRA_BLURB (a short human-readable summary
// Tasker shows in the user's task list, e.g. "Send SMS").
//
// The action picker and its param form are both built from
// AutomationRegistry.allActions() / an action's paramSchema — this
// screen has ZERO SMS-specific code in it. Adding a second
// AutomationAction later makes it selectable here automatically,
// with a correct param form, with no changes to this file.
//
// Re-editing an existing task: Tasker passes the previously-saved
// Bundle back in as this Activity's own EXTRA_BUNDLE, which this
// class decodes to pre-select the action and pre-fill its fields.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class TaskerEditActivity : Activity() {

    private lateinit var paramFieldsContainer: LinearLayout
    private lateinit var actionSpinner: Spinner

    // Rebuilt every time the spinner selection changes — key is the
    // action's declared param name, value is the EditText currently
    // holding it.
    private var currentFields: Map<String, EditText> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = AutomationRegistry.allActions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scrollView = ScrollView(this)
        scrollView.addView(root)
        setContentView(scrollView)

        root.addView(TextView(this).apply {
            text = "⚙️ Configure Automation Action"
            textSize = 18f
        })

        if (actions.isEmpty()) {
            // Defensive — ThinkLessApplication.onCreate registers
            // send_sms unconditionally, so this shouldn't happen in
            // practice, but a plugin screen that silently does
            // nothing when there's genuinely no action to configure
            // is worse than one that says so.
            root.addView(TextView(this).apply {
                text = "No automation actions are available."
                setPadding(0, 24, 0, 0)
            })
            return
        }

        actionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@TaskerEditActivity,
                android.R.layout.simple_spinner_dropdown_item,
                actions.map { it.displayName }
            )
        }
        root.addView(actionSpinner)

        paramFieldsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        root.addView(paramFieldsContainer)

        root.addView(Button(this).apply {
            text = "Save"
            setPadding(0, 24, 0, 0)
            setOnClickListener { saveAndFinish(actions) }
        })

        // Pre-fill from a previously-saved bundle, if Tasker is
        // re-opening this screen to edit an existing task action.
        val existingBundle = intent.getBundleExtra(TaskerFireReceiver.EXTRA_BUNDLE)
        val existing = existingBundle?.let { TaskerBundleCodec.decode(it) }
        val initialIndex = existing?.let { (actionId, _) -> actions.indexOfFirst { it.id == actionId } }
            ?.takeIf { it >= 0 } ?: 0

        actionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val prefill = if (position == initialIndex) existing?.second ?: emptyMap() else emptyMap()
                rebuildParamFields(actions[position], prefill)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        actionSpinner.setSelection(initialIndex)
        // setSelection doesn't always fire onItemSelected when the
        // index is already 0 on first layout — build the initial
        // field set explicitly so the very first action isn't left
        // with an empty form.
        rebuildParamFields(actions[initialIndex], existing?.second ?: emptyMap())
    }

    private fun rebuildParamFields(action: AutomationAction, prefill: Map<String, String>) {
        paramFieldsContainer.removeAllViews()
        val fields = mutableMapOf<String, EditText>()
        for ((paramName, description) in action.paramSchema) {
            paramFieldsContainer.addView(TextView(this).apply {
                text = description
                setPadding(0, 16, 0, 0)
            })
            val field = EditText(this).apply {
                hint = paramName
                setText(prefill[paramName] ?: "")
            }
            paramFieldsContainer.addView(field)
            fields[paramName] = field
        }
        currentFields = fields
    }

    private fun saveAndFinish(actions: List<AutomationAction>) {
        val action = actions[actionSpinner.selectedItemPosition]
        val params = currentFields.mapValues { (_, field) -> field.text.toString() }

        val missing = action.paramSchema.keys.filter { params[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "Fill in: ${missing.joinToString(", ")}", Toast.LENGTH_SHORT).show()
            return
        }

        val resultBundle = TaskerBundleCodec.encode(action.id, params)
        val blurb = buildBlurb(action, params)

        val resultIntent = Intent().apply {
            putExtra(TaskerFireReceiver.EXTRA_BUNDLE, resultBundle)
            putExtra(EXTRA_BLURB, blurb)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // A short summary Tasker shows in the user's task list — e.g.
    // "Send SMS: hello there" — truncated so one long message field
    // doesn't blow out Tasker's UI.
    private fun buildBlurb(action: AutomationAction, params: Map<String, String>): String {
        val firstValue = params.values.firstOrNull()?.take(40) ?: ""
        return if (firstValue.isEmpty()) action.displayName else "${action.displayName}: $firstValue"
    }

    companion object {
        // Standard Locale/Tasker plugin intent constant.
        const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    }
}
