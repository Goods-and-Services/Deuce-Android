/*
 * Copyright 2017 Jeffrey Thomas Piercy
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

package org.subhipstercollective.deucelibrary

import android.view.View

/**
 * Created by mqduck on 11/6/17.
 */
class ControllerMain(val activityMain: ActivityMain) {

    private var matches = ArrayList<Match>()

    //var winMinimumMatch = 3
    var winMinimumSet = 6
    var winMarginSet = 2
    var winMinimumGame = 4
    var winMarginGame = 2

    var serving = Serving.PLAYER1_LEFT

    private val currentMatch get() = matches.last()
    private val currentSet get() = currentMatch.currentSet
    private val currentGame get() = currentMatch.currentGame

    val matchNumber get() = matches.size
    val setNumber get() = currentMatch.setNumber
    val gameNumber get() = currentSet.gameNumber

    fun getMatchScoreStrs() = currentMatch.getScoreStrs()
    fun getSetScoreStrs() = currentSet.getScoreStrs()
    fun getGameScoreStrs() = currentGame.getScoreStrs()

    fun addMatch(winMinimumMatch: Int) {
        if (matches.size != 0 && matches.last().winner == Player.NONE)
            matches.removeAt(matches.size - 1)
        matches.add(Match(winMinimumMatch, winMinimumSet, winMarginSet, winMinimumGame, winMarginGame, this))
        activityMain.buttonScoreP1.isEnabled = true
        activityMain.buttonScoreP2.isEnabled = true

        updateDisplay()
    }

    fun updateDisplay() {
        if (matches.size != 0) {
            val scores = getGameScoreStrs()
            activityMain.textScoreP1.text = scores.player1
            activityMain.textScoreP2.text = scores.player2

            activityMain.imageBallP2Left.visibility = if (serving === Serving.PLAYER2_LEFT) View.VISIBLE else View.INVISIBLE
            activityMain.imageBallP2Right.visibility = if (serving === Serving.PLAYER2_RIGHT) View.VISIBLE else View.INVISIBLE
            activityMain.imageBallP1Left.visibility = if (serving === Serving.PLAYER1_LEFT) View.VISIBLE else View.INVISIBLE
            activityMain.imageBallP1Right.visibility = if (serving === Serving.PLAYER1_RIGHT) View.VISIBLE else View.INVISIBLE

            var textScoresMatchP1 = ""
            var textScoreMatchP2 = ""
            for (set in currentMatch.sets) {
                textScoresMatchP1 += set.scoreP1.toString() + "  "
                textScoreMatchP2 += set.scoreP2.toString() + "  "
            }
            activityMain.textScoresMatchP1.text = textScoresMatchP1.trim()
            activityMain.textScoresMatchP2.text = textScoreMatchP2.trim()
        }
    }

    fun score(player: Player) {
        val winnerGame = currentGame.score(player)
        if (winnerGame != Player.NONE) {
            val winnerSet = currentSet.score(winnerGame)
            if (winnerSet != Player.NONE) {
                val winnerMatch = currentMatch.score(winnerGame)
                if (winnerMatch != Player.NONE) {
//                    val winnerStr = if (winnerMatch == Player.PLAYER1) "Player 1" else "Player 2"
//                    displayLog?.text = String.format(
//                            "%s\n%s wins the match.",
//                            displayLog!!.text,
//                            winnerStr,
//                            setNumber
//                    )
                    activityMain.buttonScoreP1.isEnabled = false
                    activityMain.buttonScoreP2.isEnabled = false
                } else {
//                    val winnerStr = if (winnerSet == Player.PLAYER1) "Player 1" else "Player 2"
//                    displayLog?.text = String.format(
//                            "%s\n%s wins set %d.",
//                            displayLog!!.text,
//                            winnerStr,
//                            setNumber
//                    )
                    currentMatch.addNewSet()
                }
            } else {
//                val winnerStr = if (winnerGame == Player.PLAYER1) "Player 1" else "Player 2"
//                displayLog?.text = String.format(
//                        "%s\n%s wins game %d.",
//                        displayLog!!.text,
//                        winnerStr,
//                        currentMatch.gameNumber
//                )
                currentSet.addNewGame()
            }

            serving = when (serving) {
                Serving.PLAYER1_LEFT, Serving.PLAYER1_RIGHT -> Serving.PLAYER2_RIGHT
                Serving.PLAYER2_LEFT, Serving.PLAYER2_RIGHT -> Serving.PLAYER1_RIGHT
            }
        } else {
            serving = when (serving) {
                Serving.PLAYER1_LEFT -> Serving.PLAYER1_RIGHT
                Serving.PLAYER1_RIGHT -> Serving.PLAYER1_LEFT
                Serving.PLAYER2_LEFT -> Serving.PLAYER2_RIGHT
                Serving.PLAYER2_RIGHT -> Serving.PLAYER2_LEFT
            }
        }

        updateDisplay()
    }
}
