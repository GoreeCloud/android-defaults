package com.goreecloud.contacts

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Native, permission-gated address book. Provider data remains in memory.
 * All inserts/edits go to Android's user-confirmed Contacts editor.
 */
class ContactsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var tab = "people"
    private var query = ""
    private var selectedGroup: Long? = null
    private var selectedContact: Long? = null
    private var snapshot: DirectorySnapshot? = null
    private var loading = false
    private var failure = false
    private var generation = 0
    private var signal: CancellationSignal? = null
    private var destroyed = false
    private var pendingExport: List<ContactEntry>? = null
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(2), ThreadPoolExecutor.AbortPolicy())
    private val favorites by lazy { getSharedPreferences("favorites_local", MODE_PRIVATE) }
    private val permissionCode = 41
    private val exportCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getString("tab")?.takeIf {
            it in listOf("people", "favorites", "groups", "tools", "detail")
        } ?: "people"
        query = savedInstanceState?.getString("query").orEmpty()
        selectedGroup = savedInstanceState?.getLong("group", -1L)?.takeIf { it >= 0L }
        selectedContact = savedInstanceState?.getLong("contact", -1L)?.takeIf { it >= 0L }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tint(R.color.canvas))
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(0, safe.top, 0, safe.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(0, insets.systemWindowInsetTop, 0,
                        insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        setContentView(root)
        drawShell()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) {
            if (hasAccess()) refresh() else {
                invalidate()
                snapshot = null
                drawBody()
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("tab", tab)
        state.putString("query", query)
        state.putLong("group", selectedGroup ?: -1L)
        state.putLong("contact", selectedContact ?: -1L)
        super.onSaveInstanceState(state)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (hasAccess()) refresh() else {
                invalidate()
                snapshot = null
                drawBody()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        invalidate()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun hasAccess() = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

    private fun invalidate() {
        generation++
        signal?.cancel()
        signal = null
        loading = false
    }

    private fun refresh() {
        if (!hasAccess()) return
        invalidate()
        val myGeneration = generation
        val mySignal = CancellationSignal()
        signal = mySignal
        loading = true
        failure = false
        drawBody()
        try {
            worker.execute {
                val result = try {
                    Result.success(ContactsRepository(contentResolver).load(mySignal))
                } catch (_: Exception) {
                    Result.failure<DirectorySnapshot>(IllegalStateException("Unable to read contacts"))
                }
                runOnUiThread {
                    if (destroyed || myGeneration != generation || !hasAccess()) return@runOnUiThread
                    signal = null
                    loading = false
                    if (result.isSuccess) {
                        snapshot = result.getOrNull()
                        failure = false
                    } else {
                        snapshot = null
                        failure = true
                    }
                    drawBody()
                }
            }
        } catch (_: RejectedExecutionException) {
            loading = false
            failure = true
            drawBody()
        }
    }

    private fun tint(id: Int): Int = resources.getColor(id, theme)
    private fun px(dp: Int) = (resources.displayMetrics.density * dp + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 18, border: Int? = null) =
        GradientDrawable().apply {
            setColor(tint(color))
            cornerRadius = px(radius).toFloat()
            border?.let { setStroke(px(1), tint(it)) }
        }

    private fun label(message: String, size: Float = 16f,
        color: Int = R.color.ink, bold: Boolean = false) =
        TextView(this).apply {
            text = message
            textSize = size
            setTextColor(tint(color))
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun action(message: String, primary: Boolean = false, onClick: () -> Unit) =
        label(message, 14f, if (primary) R.color.surface else R.color.accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = px(48)
            setPadding(px(16), px(10), px(16), px(10))
            background = shape(if (primary) R.color.accent else R.color.accent_soft, 16)
            contentDescription = message
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(18), px(18), px(18), px(18))
        background = shape(R.color.surface, 22, R.color.outline)
    }

    private fun column(spacing: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (spacing > 0) setPadding(px(spacing), 0, px(spacing), 0)
    }

    private fun fillWidth(view: View, bottom: Int = 12) {
        body.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(bottom) })
    }

    private fun drawShell() {
        root.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(24), px(20), px(20), px(12))
        }
        val titles = column()
        titles.addView(label("GOREECLOUD  /  CONTACTS", 11f, R.color.accent, true))
        titles.addView(label(when (tab) {
            "favorites" -> "Your favorites"
            "groups" -> "Your circles"
            "tools" -> "Control center"
            "detail" -> "Contact profile"
            else -> "Your people"
        }, 28f, R.color.ink, true))
        header.addView(titles, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (tab == "people" || tab == "favorites") {
            header.addView(action("+ Add", true) { createContact() })
        }
        root.addView(header)

        if (tab == "people" || tab == "favorites") {
            val field = EditText(this).apply {
                setSingleLine(true)
                hint = "Search name, number, email..."
                inputType = InputType.TYPE_CLASS_TEXT
                setTextColor(tint(R.color.ink))
                setHintTextColor(tint(R.color.muted))
                textSize = 15f
                background = shape(R.color.surface, 18, R.color.outline)
                setPadding(px(18), px(14), px(18), px(14))
                setText(query)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        query = s?.toString().orEmpty()
                        drawBody()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            root.addView(field, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(55)
            ).apply {
                leftMargin = px(20); rightMargin = px(20); bottomMargin = px(10)
            })
        }
        body = column(18)
        root.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        drawDock()
        drawBody()
    }

    private fun drawDock() {
        val outer = LinearLayout(this).apply {
            setPadding(px(6), px(6), px(6), px(6))
            gravity = Gravity.CENTER_VERTICAL
            background = shape(R.color.surface, 26, R.color.outline)
            elevation = px(8).toFloat()
        }
        val names = listOf("people" to "People", "favorites" to "Favorites",
            "groups" to "Groups", "tools" to "Tools")
        names.forEach { (id, title) ->
            val selected = tab == id || (tab == "detail" && id == "people")
            val item = label(title, 12f,
                if (selected) R.color.accent else R.color.muted, selected).apply {
                gravity = Gravity.CENTER
                minHeight = px(52)
                background = if (selected) shape(R.color.accent_soft, 20) else null
                contentDescription = title + if (selected) ", selected" else ""
                isFocusable = true
                setOnClickListener {
                    tab = id
                    selectedContact = null
                    if (id == "people") selectedGroup = null
                    drawShell()
                }
            }
            outer.addView(item, LinearLayout.LayoutParams(0, px(52), 1f))
        }
        root.addView(outer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = px(18); rightMargin = px(18); bottomMargin = px(12)
            topMargin = px(6)
        })
    }

    private fun drawBody() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        if (!hasAccess()) {
            messageState("Your contacts stay yours",
                "Allow read-only access to show contacts already stored on this device. " +
                    "No names or numbers are uploaded. Adding a new contact uses Android's editor.",
                "Allow contact access") {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), permissionCode)
            }
            fillWidth(action("Add contact with Android's editor") { createContact() })
            return
        }
        if (loading && snapshot == null) {
            messageState("Opening your directory", "Reading Android contacts on a background thread.")
            return
        }
        if (failure) {
            messageState("Directory unavailable",
                "The Android contacts provider could not be read. Check access and try again.",
                "Retry") { refresh() }
            return
        }
        val data = snapshot ?: run {
            messageState("Getting ready", "Refresh to load contacts.", "Refresh") { refresh() }
            return
        }
        when (tab) {
            "people", "favorites" -> showPeople(data)
            "groups" -> showGroups(data)
            "tools" -> showTools(data)
            "detail" -> showDetail(data)
        }
    }

    private fun messageState(title: String, description: String,
        button: String? = null, click: (() -> Unit)? = null) {
        val panel = card().apply {
            setPadding(px(22), px(28), px(22), px(28))
            addView(label(title, 23f, R.color.ink, true))
            addView(label(description, 15f, R.color.muted).apply {
                setPadding(0, px(10), 0, px(20))
            })
            if (button != null && click != null) addView(action(button, true, click))
        }
        fillWidth(panel)
    }

    private fun filtered(data: DirectorySnapshot): List<ContactEntry> =
        data.contacts.filter { c ->
            (tab != "favorites" || favoriteKey(c) in favoriteKeys()) &&
                (selectedGroup == null || selectedGroup in c.groupIds) &&
                ContactLogic.matches(c, query)
        }

    private fun showPeople(data: DirectorySnapshot) {
        val items = filtered(data)
        val section = label(
            if (selectedGroup != null) "GROUP  ·  " + (data.groups.firstOrNull {
                it.id == selectedGroup
            }?.name ?: "Contacts") else
                if (tab == "favorites") "FAVORITE PEOPLE" else "DIRECTORY",
            12f, R.color.accent, true
        )
        section.setPadding(px(6), px(8), 0, px(8))
        fillWidth(section, 3)
        fillWidth(label(items.size.toString() + " contacts", 14f, R.color.muted)
            .apply { setPadding(px(6), 0, 0, px(6)) }, 4)
        if (selectedGroup != null) {
            fillWidth(action("Show all contacts") {
                selectedGroup = null
                drawBody()
            })
        }
        if (items.isEmpty()) {
            messageState("No matching contacts", "Try another search or add a contact.")
            return
        }
        val list = ListView(this).apply {
            divider = null
            dividerHeight = px(8)
            setBackgroundColor(tint(R.color.canvas))
            clipToPadding = false
            adapter = ContactAdapter(items)
            setOnItemClickListener { _, _, position, _ ->
                selectedContact = items[position].id
                tab = "detail"
                drawShell()
            }
        }
        body.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private inner class ContactAdapter(private val items: List<ContactEntry>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = items[position]
            val row = LinearLayout(this@ContactsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(15), px(12), px(15), px(12))
                background = shape(R.color.surface, 18, R.color.outline)
                minimumHeight = px(78)
            }
            val avatar = label(entry.name.take(1).uppercase(Locale.getDefault()), 20f,
                R.color.accent, true).apply {
                gravity = Gravity.CENTER
                background = shape(R.color.avatar, 18)
            }
            row.addView(avatar, LinearLayout.LayoutParams(px(46), px(46)))
            val texts = column().apply {
                setPadding(px(14), 0, 0, 0)
                addView(label(entry.name, 16f, R.color.ink, true).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                val description = entry.organization.ifBlank {
                    entry.phones.firstOrNull() ?: entry.emails.firstOrNull() ?: "Contact"
                }
                addView(label(description, 13f, R.color.muted).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
            row.addView(texts, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(label(if (favoriteKey(entry) in favoriteKeys()) "★" else "›",
                21f, R.color.accent, true))
            row.contentDescription = entry.name + ", open contact details"
            return row
        }
    }

    private fun showGroups(data: DirectorySnapshot) {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val container = column()
        container.addView(label("PEOPLE, ORGANIZED", 12f, R.color.accent, true))
        container.addView(label("Groups from your Android address book", 15f,
            R.color.muted).apply { setPadding(0, px(6), 0, px(18)) })
        if (data.groups.isEmpty()) {
            container.addView(label("No contact groups are available.", 15f, R.color.muted))
        }
        data.groups.forEach { group ->
            val count = data.contacts.count { group.id in it.groupIds }
            val item = card().apply {
                addView(label(group.name, 18f, R.color.ink, true))
                addView(label(count.toString() + " members", 13f, R.color.muted))
                isFocusable = true
                contentDescription = group.name + ", view " + count + " contacts"
                setOnClickListener {
                    selectedGroup = group.id
                    tab = "people"
                    query = ""
                    drawShell()
                }
            }
            container.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = px(10) })
        }
        scroll.addView(container)
        body.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun showTools(data: DirectorySnapshot) {
        val scroll = ScrollView(this)
        val container = column()
        val top = card()
        top.addView(label("Your local directory", 20f, R.color.ink, true))
        top.addView(label(data.contacts.size.toString() + " contacts · " +
            data.groups.size + " groups", 15f, R.color.muted).apply {
            setPadding(0, px(7), 0, px(16))
        })
        top.addView(action("Refresh from Android", true) { refresh() })
        container.addView(top, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(12) })

        val exportCard = card()
        exportCard.addView(label("Export a copy", 19f, R.color.ink, true))
        exportCard.addView(label("Save a vCard containing names, phones, emails and " +
            "organizations. Other contact fields are not included in this preview.",
            14f, R.color.muted).apply { setPadding(0, px(8), 0, px(14)) })
        exportCard.addView(action("Choose export destination") { confirmExport(data.contacts) })
        container.addView(exportCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(12) })

        val clusters = ContactLogic.duplicateClusters(data.contacts)
        val duplicateCard = card()
        duplicateCard.addView(label("Potential duplicates", 19f, R.color.ink, true))
        duplicateCard.addView(label(clusters.size.toString() +
            " possible matches · Suggestions only; no records will be merged.",
            14f, R.color.muted).apply { setPadding(0, px(8), 0, px(10)) })
        clusters.take(15).forEach { cluster ->
            duplicateCard.addView(action(cluster.joinToString(" / ") { it.name }) {
                selectedContact = cluster.first().id
                tab = "detail"
                drawShell()
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = px(7) })
        }
        if (clusters.size > 15) duplicateCard.addView(label(
            "Additional suggestions omitted from this overview.", 12f, R.color.muted))
        container.addView(duplicateCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(12) })

        val privacy = card()
        privacy.addView(label("Privacy by design", 19f, R.color.ink, true))
        privacy.addView(label("Read-only contact access. No Internet permission, " +
            "analytics or background cloud synchronization. Favorites remain " +
            "in private app storage and are excluded from Android backups.",
            14f, R.color.muted).apply { setPadding(0, px(8), 0, px(4)) })
        container.addView(privacy)
        scroll.addView(container)
        body.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun showDetail(data: DirectorySnapshot) {
        val c = data.contacts.firstOrNull { it.id == selectedContact }
        if (c == null) {
            messageState("Contact unavailable", "This contact may have moved or been removed.",
                "Back to directory") {
                tab = "people"; selectedContact = null; drawShell()
            }
            return
        }
        val scroll = ScrollView(this)
        val content = column()
        content.addView(action("‹ Back to people") {
            tab = "people"; selectedContact = null; drawShell()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(12) })
        val identity = card()
        identity.addView(label(c.name.take(1).uppercase(Locale.getDefault()), 38f,
            R.color.accent, true).apply {
            gravity = Gravity.CENTER
            background = shape(R.color.avatar, 28)
            minHeight = px(92)
        })
        identity.addView(label(c.name, 25f, R.color.ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(16), 0, px(4))
        })
        if (c.organization.isNotBlank()) identity.addView(
            label(c.organization, 14f, R.color.muted).apply { gravity = Gravity.CENTER })
        identity.addView(action(
            if (favoriteKey(c) in favoriteKeys()) "★ Remove favorite" else "☆ Add favorite"
        ) {
            toggleFavorite(c)
            drawBody()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14) })
        content.addView(identity, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(12) })

        val details = card()
        details.addView(label("Contact details", 19f, R.color.ink, true).apply {
            setPadding(0, 0, 0, px(12))
        })
        c.phones.forEach { number ->
            details.addView(label(number, 16f, R.color.ink))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(6), 0, px(12))
            }
            actions.addView(action("Call") {
                launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(action("Message") {
                launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = px(8) })
            details.addView(actions)
        }
        c.emails.forEach { email ->
            details.addView(label(email, 15f, R.color.ink).apply {
                setPadding(0, px(5), 0, px(4))
            })
            details.addView(action("Compose email") {
                launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(10) })
        }
        if (c.phones.isEmpty() && c.emails.isEmpty()) details.addView(
            label("No phone numbers or email addresses", 14f, R.color.muted))
        content.addView(details, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(12) })

        val management = card()
        management.addView(label("Manage this contact", 19f, R.color.ink, true))
        management.addView(label("Android's editor confirms any changes before saving.",
            13f, R.color.muted).apply { setPadding(0, px(6), 0, px(12)) })
        management.addView(action("Edit in Android Contacts", true) {
            val uri = if (c.lookupKey.isNotBlank())
                ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
            else android.content.ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, c.id)
            launch(Intent(Intent.ACTION_EDIT, uri))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(8) })
        management.addView(action("Share selected details") { confirmShare(c) })
        content.addView(management)
        scroll.addView(content)
        body.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun favoriteKeys(): Set<String> =
        favorites.getStringSet("keys", emptySet())?.toSet().orEmpty()
    private fun favoriteKey(c: ContactEntry) = c.lookupKey.ifBlank { "id:" + c.id }
    private fun toggleFavorite(c: ContactEntry) {
        val keys = favoriteKeys().toMutableSet()
        if (!keys.add(favoriteKey(c))) keys.remove(favoriteKey(c))
        favorites.edit().putStringSet("keys", keys).apply()
    }

    private fun createContact() {
        launch(Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
        })
    }

    private fun confirmShare(c: ContactEntry) {
        val text = buildString {
            append(c.name)
            c.phones.forEach { append("\nPhone: ").append(it) }
            c.emails.forEach { append("\nEmail: ").append(it) }
        }
        AlertDialog.Builder(this)
            .setTitle("Share this contact?")
            .setMessage("The receiving application will get these details:\n\n" + text)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose app") { _, _ ->
                launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Share contact"))
            }.show()
    }

    private fun confirmExport(contacts: List<ContactEntry>) {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts to export", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Export " + contacts.size + " contacts?")
            .setMessage("A copy containing names, phones, emails and organizations " +
                "will be saved to the location you select. Other fields are not included.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose location") { _, _ ->
                pendingExport = contacts.toList()
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/x-vcard"
                    putExtra(Intent.EXTRA_TITLE, "GoreeCloud-Contacts.vcf")
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, exportCode)
                } catch (_: ActivityNotFoundException) {
                    pendingExport = null
                    Toast.makeText(this, "No document picker available", Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    pendingExport = null
                    Toast.makeText(this, "Document access unavailable", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    @Deprecated("Android callback retained for API 26–35, no extra library required")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportCode) return
        val requested = pendingExport
        pendingExport = null
        val destination = data?.data
        if (resultCode != RESULT_OK || destination == null || requested == null) return
        try {
            worker.execute {
                val success = try {
                    val stream = contentResolver.openOutputStream(destination, "wt")
                        ?: throw IllegalStateException("Document cannot be opened")
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        requested.forEach { writer.write(ContactLogic.vCard(it)) }
                    }
                    true
                } catch (_: Exception) { false }
                runOnUiThread {
                    if (!destroyed) Toast.makeText(this,
                        if (success) "vCard exported" else "Export failed; check the destination",
                        Toast.LENGTH_LONG).show()
                }
            }
        } catch (_: RejectedExecutionException) {
            Toast.makeText(this, "Export worker busy; retry", Toast.LENGTH_LONG).show()
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No compatible application found", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Action not permitted on this device", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Back navigation supported on API 26+")
    override fun onBackPressed() {
        if (tab == "detail") {
            tab = "people"
            selectedContact = null
            drawShell()
        } else super.onBackPressed()
    }
}
