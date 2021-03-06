/*
 * Copyright (C) 2020 Jeffrey Thomas Piercy
 *
 * This file is part of Deuce-Android.
 *
 * Deuce-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Deuce-Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Deuce-Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.mqduck.deuce

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.support.wearable.input.WearableButtons
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.widget.drawer.WearableNavigationDrawerView
import com.google.android.gms.wearable.*
import kotlinx.android.synthetic.main.activity_main.*
import net.mqduck.deuce.common.*
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.File
import java.lang.Exception


internal lateinit var mainActivity: MainActivity

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider, DataClient.OnDataChangedListener {
    private enum class FragmentEnum {
        SETUP, ADVANCED_SETUP, SCORE;

        companion object {
            fun fromOrdinal(ordinal: Int) = when (ordinal) {
                SETUP.ordinal -> SETUP
                ADVANCED_SETUP.ordinal -> ADVANCED_SETUP
                SCORE.ordinal -> SCORE
                else -> SETUP
            }
        }
    }

    //TODO: Find a way to disable anti-aliasing on ambient vector images
    private enum class NavigationItemList(val list: Array<NavigationItem>) {
        NAVIGATION_ITEMS_WITHOUT_MATCH(
            arrayOf(
                NavigationItem.NAVIGATION_ITEM_SETUP,
                NavigationItem.NAVIGATION_ITEM_ADVANCED_SETUP
            )
        ),

        NAVIGATION_ITEMS_WITH_MATCH(
            arrayOf(
                NavigationItem.NAVIGATION_ITEM_MATCH,
                NavigationItem.NAVIGATION_ITEM_SETUP,
                NavigationItem.NAVIGATION_ITEM_ADVANCED_SETUP
            )
        ),

        NAVIGATION_ITEMS_WITHOUT_MATCH_AMBIENT(
            arrayOf(
                NavigationItem.NAVIGATION_ITEM_SETUP_AMBIENT,
                NavigationItem.NAVIGATION_ITEM_ADVANCED_SETUP_AMBIENT
            )
        ),

        NAVIGATION_ITEMS_WITH_MATCH_AMBIENT(
            arrayOf(
                NavigationItem.NAVIGATION_ITEM_MATCH_AMBIENT,
                NavigationItem.NAVIGATION_ITEM_SETUP_AMBIENT,
                NavigationItem.NAVIGATION_ITEM_ADVANCED_SETUP_AMBIENT
            )
        );

        enum class NavigationItem(val text: CharSequence, val drawableId: Int, val enum: FragmentEnum) {
            NAVIGATION_ITEM_MATCH(
                "Match",
                R.drawable.match,
                FragmentEnum.SCORE
            ),

            NAVIGATION_ITEM_SETUP(
                "Match Setup",
                R.drawable.setup,
                FragmentEnum.SETUP
            ),

            NAVIGATION_ITEM_ADVANCED_SETUP(
                "Advanced Setup",
                R.drawable.advanced_setup,
                FragmentEnum.ADVANCED_SETUP
            ),

            NAVIGATION_ITEM_MATCH_AMBIENT(
                "Match",
                R.drawable.match_ambient,
                FragmentEnum.SCORE
            ),

            NAVIGATION_ITEM_SETUP_AMBIENT(
                "Match Setup",
                R.drawable.setup_ambient,
                FragmentEnum.SETUP
            ),

            NAVIGATION_ITEM_ADVANCED_SETUP_AMBIENT(
                "Advanced Setup",
                R.drawable.advanced_setup_ambient,
                FragmentEnum.ADVANCED_SETUP
            )
        }
    }

    private inner class DeuceAmbientCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            inAmbientMode = true
            switchFragment(currentFragment)

            navigationDrawer.background.setTint(getColor(R.color.black))
            navigationAdapter.update()
        }

        override fun onExitAmbient() {
            inAmbientMode = false
            switchFragment(currentFragment)

            navigationDrawer.background.setTint(getColor(R.color.lighter_bg_1))
            navigationAdapter.update()
        }

        override fun onUpdateAmbient() {}
    }

    init {
        mainActivity = this
    }

    internal var currentMatch = DeuceMatch()
    internal lateinit var preferences: DeuceWearPreferences
    lateinit var dataClient: DataClient
    internal lateinit var matchList: WearMatchList

    private var setupFragment = SetupFragment()
    private var advancedSetupFragment = AdvancedSetupFragment()
    private var scoreFragment = ScoreFragment()

    private val fragmentManager = supportFragmentManager
    private var currentFragment = FragmentEnum.SETUP
    private var matchAdded = false
    internal var inAmbientMode = false
        private set

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private var gestureDetector: GestureDetectorCompat? = null
    private var undoButton: Int? = null

    lateinit var navigationDrawer: WearableNavigationDrawerView


    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = DeuceAmbientCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        // https://github.com/Functional-Organization/Deuce-Android/issues/21
        savedInstanceState?.remove("android:support:fragments")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Game.init(this)
        DeuceMatch.init(this)

        navigationDrawer = navigation_drawer

        preferences = DeuceWearPreferences(PreferenceManager.getDefaultSharedPreferences(this))
        dataClient = Wearable.getDataClient(this)
        matchList = WearMatchList(
            File(filesDir, MATCH_LIST_FILE_NAME),
            File(filesDir, MATCH_LIST_FILE_BACKUP_NAME),
            0,
            0
        ) {}

        var fragment = FragmentEnum.SETUP

        val savedStateFile = File(filesDir, SAVED_STATE_FILE_NAME)
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_CURRENT_FRAGMENT)) {
                fragment = savedInstanceState.getSerializable(KEY_CURRENT_FRAGMENT) as FragmentEnum
            }
            if (savedInstanceState.containsKey(KEY_CURRENT_MATCH)) {
                currentMatch = savedInstanceState.getParcelable(KEY_CURRENT_MATCH)!!
            }
            if (savedInstanceState.containsKey(KEY_MATCH_ADDED)) {
                matchAdded = savedInstanceState.getBoolean(KEY_MATCH_ADDED)
                if (matchAdded) {
                    navigationAdapter.enableMatch()
                }
            }
            navigationAdapter.notifyDataSetChanged()
        } else if (savedStateFile.exists()) {
            try {
                val json = JSONParser().parse(savedStateFile.readText()) as JSONObject
                val savedMatch = DeuceMatch(json[KEY_CURRENT_MATCH] as JSONObject)
                val savedFragment = FragmentEnum.fromOrdinal((json[KEY_CURRENT_FRAGMENT] as Long).toInt())
                val savedMatchAdded = json[KEY_MATCH_ADDED] as Boolean

                fragment = savedFragment
                currentMatch = savedMatch
                matchAdded = savedMatchAdded
                if (matchAdded) {
                    navigationAdapter.enableMatch()
                }
                navigationAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading saved state file $savedStateFile: $e")
            }
        }
        if (savedStateFile.exists()) {
            savedStateFile.delete()
        }

        navigation_drawer.setAdapter(navigationAdapter)
        navigation_drawer.addOnItemSelectedListener {
            switchFragment(navigationAdapter.getItemEnum(it))
        }

        ambientController = AmbientModeSupport.attach(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && WearableButtons.getButtonCount(this) > 0) {
            undoButton = KeyEvent.KEYCODE_STEM_1
        } else {
            gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    event1: MotionEvent,
                    event2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (currentFragment == FragmentEnum.SCORE && event1.x - event2.x >= 100 && velocityX <= -100) {
                        undo()
                        return true
                    }
                    return false
                }
            })
        }

        if (fragment == FragmentEnum.SCORE && currentMatch.winner != TeamOrNone.NONE) {
            fragment = FragmentEnum.SETUP
        }
        setNavigationItem(fragment)
        switchFragment(fragment)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        syncMatches()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(KEY_CURRENT_MATCH, currentMatch)
        outState.putSerializable(KEY_CURRENT_FRAGMENT, currentFragment)
        outState.putBoolean(KEY_MATCH_ADDED, matchAdded)
    }

    override fun onStop() {
        val json = JSONObject()
        json[KEY_CURRENT_MATCH] = currentMatch.toJSONObject()
        json[KEY_CURRENT_FRAGMENT] = currentFragment.ordinal
        json[KEY_MATCH_ADDED] = matchAdded
        File(filesDir, SAVED_STATE_FILE_NAME).writeText(json.toJSONString())

        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == undoButton && currentFragment == FragmentEnum.SCORE) {
            undo()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        /*val detector = gestureDetector
        return if (detector == null)
            super.dispatchTouchEvent(ev)
        else
            detector.onTouchEvent(ev) || super.dispatchTouchEvent(ev)*/
        return (gestureDetector != null && gestureDetector!!.onTouchEvent(ev)) || super.dispatchTouchEvent(ev)
    }

    fun newMatch() {
        matchAdded = true
        navigationAdapter.enableMatch()
        setNavigationItem(FragmentEnum.SCORE)
        switchFragment(FragmentEnum.SCORE)

        currentMatch = DeuceMatch(
            preferences.numSets,
            preferences.startingServer,
            preferences.overtime,
            preferences.matchType,
            System.currentTimeMillis(),
            ArrayList(),
            ScoreStack(),
            "",
            ""
        )

        syncData(dataClient, PATH_CURRENT_MATCH, true) { dataMap ->
            writeMatchToDataMap(currentMatch, dataMap)
        }
    }

    private fun writeMatchToDataMap(match: DeuceMatch, dataMap: DataMap) {
        dataMap.putInt(KEY_MATCH_STATE, MatchState.NEW.ordinal)
        dataMap.putInt(KEY_NUM_SETS, match.numSets.ordinal)
        dataMap.putInt(KEY_SERVER, match.startingServer.ordinal)
        dataMap.putInt(KEY_OVERTIME_RULE, match.overtimeRule.ordinal)
        dataMap.putInt(KEY_MATCH_TYPE, match.matchType.ordinal)
        dataMap.putLong(KEY_MATCH_START_TIME, match.startTime)
        dataMap.putLongArray(KEY_SET_END_TIMES, match.setEndTimes.toLongArray())
        dataMap.putLongArray(KEY_SCORE_ARRAY, match.scoreLog.bitsetLongArray())
        dataMap.putString(KEY_NAME_TEAM1, match.nameTeam1)
        dataMap.putString(KEY_NAME_TEAM2, match.nameTeam2)
        dataMap.putLong(KEY_DUMMY, System.currentTimeMillis())
    }

    fun undo() {
        // Because the ScoreFragment may no longer exist after the undo animation completes, it *must* be performed
        // in MainActivity.
        if (currentMatch.undo()) {
            image_undo.visibility = View.VISIBLE
            val fadeout = AlphaAnimation(1F, 0F)
            fadeout.duration = ScoreFragment.UNDO_ANIMATION_DURATION
            image_undo.startAnimation(fadeout)
            image_undo.postDelayed({
                image_undo.visibility = View.GONE
            }, ScoreFragment.UNDO_ANIMATION_DURATION)
            scoreFragment.updateDisplay(false)
            activity_main.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private val navigationAdapter =
        object : WearableNavigationDrawerView.WearableNavigationDrawerAdapter() {
            private var items = NavigationItemList.NAVIGATION_ITEMS_WITHOUT_MATCH

            override fun getItemText(pos: Int) = items.list[pos].text

            override fun getItemDrawable(pos: Int) =
                ContextCompat.getDrawable(this@MainActivity, items.list[pos].drawableId)

            override fun getCount() = items.list.size

            fun enableMatch() {
                // This condition will probably never be true
                items = if (inAmbientMode)
                    NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH_AMBIENT
                else
                    NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH
                navigation_drawer.setCurrentItem(getEnumPos(currentFragment), false)
                update()
            }

            fun getItemEnum(pos: Int) = items.list[pos].enum

            fun getEnumPos(enum: FragmentEnum): Int {
                for (i in items.list.indices) {
                    if (items.list[i].enum == enum) {
                        return i
                    }
                }
                throw IllegalArgumentException("$enum is not currently in the navigation drawer.")
            }

            fun update() {
                items = when (items) {
                    NavigationItemList.NAVIGATION_ITEMS_WITHOUT_MATCH,
                    NavigationItemList.NAVIGATION_ITEMS_WITHOUT_MATCH_AMBIENT ->
                        if (inAmbientMode)
                            NavigationItemList.NAVIGATION_ITEMS_WITHOUT_MATCH_AMBIENT
                        else
                            NavigationItemList.NAVIGATION_ITEMS_WITHOUT_MATCH
                    NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH,
                    NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH_AMBIENT ->
                        if (inAmbientMode)
                            NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH_AMBIENT
                        else
                            NavigationItemList.NAVIGATION_ITEMS_WITH_MATCH
                }
                notifyDataSetChanged()
            }
        }

    override fun getTheme(): Resources.Theme {
        //return super.getTheme()
        val theme = super.getTheme()
        if (inAmbientMode) {
            theme.applyStyle(R.style.DeuceWear_Ambient, true)
        } else {
            theme.applyStyle(R.style.DeuceWear, true)
        }
        return theme
    }

    private fun setNavigationItem(fragment: FragmentEnum) =
        navigation_drawer.setCurrentItem(navigationAdapter.getEnumPos(fragment), false)

    private fun switchFragment(fragment: FragmentEnum) {
        currentFragment = fragment

        when (fragment) {
            FragmentEnum.SETUP -> {
                if (setupFragment.inAmbientMode != inAmbientMode) {
                    setupFragment = SetupFragment()
                }
                fragmentManager.beginTransaction().replace(R.id.fragment_container, setupFragment).commit()
            }
            FragmentEnum.ADVANCED_SETUP -> {
                if (advancedSetupFragment.inAmbientMode != inAmbientMode) {
                    advancedSetupFragment = AdvancedSetupFragment()
                }
                fragmentManager.beginTransaction().replace(R.id.fragment_container, advancedSetupFragment).commit()
            }
            FragmentEnum.SCORE -> {
                if (scoreFragment.inAmbientMode != inAmbientMode) {
                    scoreFragment = ScoreFragment()
                }
                fragmentManager.beginTransaction().replace(R.id.fragment_container, scoreFragment).commit()
            }
        }
    }

    internal fun syncMatches() {
        var deleteCurrentMatch = true

        if (matchAdded && currentMatch.winner == TeamOrNone.NONE) {
            deleteCurrentMatch = false
            syncData(dataClient, PATH_CURRENT_MATCH, true) { dataMap ->
                writeMatchToDataMap(currentMatch, dataMap)
            }
        }

        if (matchList.isNotEmpty()) {
            syncData(dataClient, PATH_FINISHED_MATCHES, true) { dataMap ->
                dataMap.putDataMapArrayList(KEY_MATCH_LIST, ArrayList(matchList.map {
                    val matchDataMap = DataMap()
                    writeMatchToDataMap(it, matchDataMap)
                    matchDataMap
                }))
                dataMap.putBoolean(KEY_MATCH_LIST_STATE, true)
                dataMap.putBoolean(KEY_DELETE_CURRENT_MATCH, deleteCurrentMatch)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("foo", "data changed")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo(PATH_TRANSMISSION_SIGNAL) == 0) {
                        Log.d("foo", "clearing match list")
                        matchList.clear()
                        matchList.writeToFile()
                    } else if (item.uri.path?.compareTo(PATH_REQUEST_MATCHES_SIGNAL) == 0) {
                        Log.d("foo", "received match request")
                        syncData(dataClient, PATH_CURRENT_MATCH, true) { dataMap ->
                            writeMatchToDataMap(currentMatch, dataMap)
                        }
                        syncMatches()
                    } else if (item.uri.path?.compareTo(PATH_UPDATE_NAMES) == 0) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        currentMatch.nameTeam1 = dataMap.getString(KEY_NAME_TEAM1)
                        currentMatch.nameTeam2 = dataMap.getString(KEY_NAME_TEAM2)
                        scoreFragment.updateTeamNames()
                    }
                }
            }
        }
    }
}
