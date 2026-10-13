package com.milkywaze.weatherwidget

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lets the user pick the location and which forecast models to compare.
 * Also used as the widget's configuration screen.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var currentLocation: TextView
    private lateinit var resultsAdapter: ArrayAdapter<String>
    private var searchResults: List<Location> = emptyList()
    private var configWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation() else toast(R.string.location_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        configWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // If the user backs out of configuration the widget should not be added.
            setResult(Activity.RESULT_CANCELED, resultIntent())
        }

        currentLocation = findViewById(R.id.current_location)
        showLocation()

        val query = findViewById<EditText>(R.id.search_query)
        val searchButton = findViewById<Button>(R.id.search_button)
        searchButton.setOnClickListener { search(query.text.toString()) }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(query.text.toString()); true } else false
        }

        resultsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        findViewById<ListView>(R.id.search_results).apply {
            adapter = resultsAdapter
            setOnItemClickListener { _, _, position, _ ->
                searchResults.getOrNull(position)?.let(::selectLocation)
            }
        }

        findViewById<Button>(R.id.use_device_location).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                useDeviceLocation()
            } else {
                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        buildModelCheckboxes()

        findViewById<Button>(R.id.done_button).setOnClickListener { finishAndRefresh() }
    }

    private fun buildModelCheckboxes() {
        val container = findViewById<LinearLayout>(R.id.model_list)
        val selected = prefs.selectedModels.toMutableSet()
        WeatherModel.entries.forEach { model ->
            val box = CheckBox(this).apply {
                text = getString(R.string.model_checkbox, model.shortName, model.provider)
                isChecked = model in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += model else selected -= model
                    prefs.selectedModels = WeatherModel.entries.filter { it in selected }
                }
            }
            container.addView(box)
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) return
        val progress = findViewById<View>(R.id.search_progress)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { OpenMeteoClient.searchLocations(query.trim()) }
            progress.visibility = View.GONE
            result.onSuccess { locations ->
                searchResults = locations
                resultsAdapter.clear()
                resultsAdapter.addAll(locations.map { it.name })
                if (locations.isEmpty()) toast(R.string.search_no_results)
            }.onFailure {
                toast(R.string.search_failed)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun useDeviceLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) }
        if (provider == null) {
            toast(R.string.location_unavailable)
            return
        }
        toast(R.string.location_finding)
        LocationManagerCompat.getCurrentLocation(lm, provider, null, ContextCompat.getMainExecutor(this)) { loc ->
            val fix = loc ?: lm.getLastKnownLocation(provider)
            if (fix == null) {
                toast(R.string.location_unavailable)
                return@getCurrentLocation
            }
            lifecycleScope.launch {
                val name = reverseGeocode(fix.latitude, fix.longitude)
                    ?: String.format(Locale.US, "%.3f, %.3f", fix.latitude, fix.longitude)
                selectLocation(Location(name, fix.latitude, fix.longitude))
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(this@SettingsActivity, Locale.KOREAN).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                listOfNotNull(a.locality ?: a.subAdminArea ?: a.adminArea, a.subLocality ?: a.thoroughfare)
                    .distinct().joinToString(" ").ifBlank { null }
            }
        }.getOrNull()
    }

    private fun selectLocation(location: Location) {
        prefs.location = location
        prefs.clearCache()
        RefreshWorker.refreshNow(this)
        showLocation()
        resultsAdapter.clear()
        searchResults = emptyList()
    }

    private fun showLocation() {
        val loc = prefs.location
        currentLocation.text = getString(R.string.current_location, loc.name, loc.latitude, loc.longitude)
    }

    private fun finishAndRefresh() {
        if (prefs.selectedModels.isEmpty()) {
            toast(R.string.widget_no_models)
            return
        }
        RefreshWorker.schedulePeriodic(this)
        RefreshWorker.refreshNow(this)
        CompareWidgetProvider.updateAll(this)
        if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, resultIntent())
        }
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
