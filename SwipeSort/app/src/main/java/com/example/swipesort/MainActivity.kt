package com.example.swipesort

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Typeface
import android.util.TypedValue

data class Photo(val uri: Uri, val id: Long, val name: String)

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var image: ImageView
    private lateinit var counter: TextView
    private lateinit var hint: TextView
    private lateinit var keepButton: Button
    private lateinit var deleteButton: Button
    private lateinit var undoButton: Button
    private lateinit var commitButton: Button

    private val photos = mutableListOf<Photo>()
    private val deleteQueue = linkedSetOf<Long>()
    private var position = 0
    private var downX = 0f
    private var downY = 0f
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it } || hasPhotoPermission()) loadPhotos()
        else showEmpty("Photo access is needed to view your gallery.")
    }
    private val deletePermission = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT == 29 && pendingDeleteUris.isNotEmpty()) {
                val remaining = pendingDeleteUris
                pendingDeleteUris = emptyList()
                deleteLegacyUris(remaining, 0)
                return@registerForActivityResult
            }
            deleteQueue.clear()
            RustCore.clear()
            position = 0
            Toast.makeText(this, "Selected photos deleted", Toast.LENGTH_SHORT).show()
            loadPhotos()
        } else {
            pendingDeleteUris = emptyList()
            Toast.makeText(this, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (hasPhotoPermission()) {
            loadPhotos()
        } else {
            val permissions = when {
                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
                Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions.launch(permissions)
        }
    }

    private fun hasPhotoPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= 33 ->
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else ->
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            setBackgroundColor(Color.rgb(247, 247, 250))
        }
        setContentView(root)

        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "SwipeSort"
            setTextColor(Color.rgb(25, 28, 35))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitle = TextView(this).apply {
            text = "Review your photos, one at a time"
            setTextColor(Color.rgb(105, 110, 120))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        top.addView(title)
        top.addView(subtitle)
        root.addView(top)

        counter = TextView(this).apply {
            setTextColor(Color.rgb(80, 85, 96))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(18), 0, dp(10))
        }
        root.addView(counter)

        val card = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
        }
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(235, 237, 241))
            contentDescription = "Current photo. Swipe right to keep or left to queue for deletion."
        }
        card.addView(image, FrameLayout.LayoutParams(-1, -1))
        image.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (kotlin.math.abs(dx) > dp(70) && kotlin.math.abs(dx) > kotlin.math.abs(event.y - downY)) {
                        if (dx > 0) decideKeep() else decideDelete()
                    }
                    true
                }
                else -> true
            }
        }
        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(2)
            bottomMargin = dp(14)
        })

        hint = TextView(this).apply {
            text = "Swipe right to keep  ·  Swipe left to mark for deletion"
            setTextColor(Color.rgb(105, 110, 120))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(hint)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        keepButton = makeButton("Keep", Color.rgb(30, 115, 82), Color.WHITE)
        deleteButton = makeButton("Mark delete", Color.rgb(190, 55, 64), Color.WHITE)
        row1.addView(keepButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        row1.addView(deleteButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        undoButton = makeButton("Undo", Color.rgb(231, 233, 238), Color.rgb(35, 39, 48))
        commitButton = makeButton("Delete selected (0)", Color.rgb(35, 39, 48), Color.WHITE)
        row2.addView(undoButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        row2.addView(commitButton, LinearLayout.LayoutParams(0, dp(48), 1.5f).apply { leftMargin = dp(6) })
        root.addView(row2)

        keepButton.setOnClickListener { decideKeep() }
        deleteButton.setOnClickListener { decideDelete() }
        undoButton.setOnClickListener { undo() }
        commitButton.setOnClickListener { deleteSelected() }
    }

    private fun makeButton(label: String, bg: Int, fg: Int) = Button(this).apply {
        text = label
        setTextColor(fg)
        setBackgroundColor(bg)
        textSize = 13f
        isAllCaps = false
    }

    private fun loadPhotos() {
        photos.clear()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
        try {
            contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    photos.add(Photo(ContentUris.withAppendedId(collection, id), id, cursor.getString(nameCol) ?: "Photo"))
                }
            }
        } catch (_: SecurityException) {
            showEmpty("Photo access changed. Allow access to continue.")
            return
        } catch (_: Exception) {
            showEmpty("Could not read photos. Please try again.")
            return
        }
        position = position.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        render()
    }

    private fun decideKeep() {
        if (position >= photos.size) return
        RustCore.push(position.toLong(), 1)
        position++
        render()
    }

    private fun decideDelete() {
        if (position >= photos.size) return
        val p = photos[position]
        deleteQueue.add(p.id)
        RustCore.push(position.toLong(), 2)
        position++
        render()
    }

    private fun undo() {
        val packed = RustCore.undo()
        if (packed == -1L) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        val index = (packed ushr 2).toInt()
        val action = (packed and 3L).toInt()
        val photoIndex = index.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        if (action == 2 && photoIndex < photos.size) deleteQueue.remove(photos[photoIndex].id)
        position = photoIndex
        render()
    }

    private fun deleteSelected() {
        if (deleteQueue.isEmpty()) {
            Toast.makeText(this, "No photos marked for deletion", Toast.LENGTH_SHORT).show()
            return
        }
        val uris = photos.filter { deleteQueue.contains(it.id) }.map { it.uri }
        if (uris.isEmpty()) {
            deleteQueue.clear()
            render()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pending = MediaStore.createDeleteRequest(contentResolver, uris)
                deletePermission.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } else {
                contentResolver.delete(uris.first(), null, null)
                deleteQueue.remove(photos.firstOrNull { it.uri == uris.first() }?.id)
                RustCore.clear()
                position = 0
                loadPhotos()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Android could not start the delete confirmation: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEmpty(message: String) {
        counter.text = message
        image.setImageDrawable(null)
        image.setBackgroundColor(Color.rgb(235, 237, 241))
    }

    private fun render() {
        commitButton.text = "Delete selected (${deleteQueue.size})"
        undoButton.isEnabled = RustCore.count() > 0
        if (photos.isEmpty()) {
            showEmpty("No photos found on this device.")
            keepButton.isEnabled = false
            deleteButton.isEnabled = false
            hint.text = "Allow photo access to begin"
            return
        }
        if (position >= photos.size) {
            showEmpty("You're all caught up.")
            counter.text = "${photos.size} photos reviewed"
            keepButton.isEnabled = false
            deleteButton.isEnabled = false
            hint.text = "Undo a decision or finish deleting selected photos"
            return
        }
        val p = photos[position]
        counter.text = "Photo ${position + 1} of ${photos.size}  ·  ${deleteQueue.size} marked"
        image.setImageURI(p.uri)
        keepButton.isEnabled = true
        deleteButton.isEnabled = true
        hint.text = p.name
    }
}
