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

package net.mqduck.deuce.common

open class Match(
    val winMinimumMatch: Int,
    val winMarginMatch: Int,
    val winMinimumSet: Int,
    val winMarginSet: Int,
    val winMinimumGame: Int,
    val winMarginGame: Int,
    val winMinimumGameTiebreak: Int,
    val winMarginGameTiebreak: Int,
    val startingServer: Team,
    val overtimeRule: OvertimeRule,
    val matchType: MatchType,
    val startTime: Long,
    val scoreLog: ScoreStack,
    val nameTeam1: String,
    val nameTeam2: String
) {
    lateinit var sets: ArrayList<Set>
    private lateinit var mScore: Score
    lateinit var serving: Serving
        private set
    var changeover = false
        private set
    var serviceChanged = true
        private set

    val scoreTeam1 get() = mScore.scoreTeam1
    val scoreTeam2 get() = mScore.scoreTeam2

    init {
        loadScoreLog()
    }

    private fun loadScoreLog() {
        mScore = Score(winMinimumMatch, winMarginMatch)
        serving = if (startingServer == Team.TEAM1) Serving.PLAYER1_RIGHT else Serving.PLAYER2_RIGHT
        sets = arrayListOf(
            Set(
                winMinimumSet,
                winMarginSet,
                winMinimumGame,
                winMarginGame,
                winMinimumGameTiebreak,
                winMarginGameTiebreak,
                overtimeRule,
                this
            )
        )

        for (i in 0 until scoreLog.size) {
            score(scoreLog[i], false)
        }
    }

    val winner get() = mScore.winner

    val currentSet get() = sets.last()
    val currentGame get() = currentSet.currentGame

    private fun score(team: Team, updateLog: Boolean) {
        changeover = false
        serviceChanged = false
        val winnerGame = currentGame.score(team)
        if (winnerGame != Winner.NONE) {
            val winnerSet = currentSet.score(team)
            if (winnerSet != Winner.NONE) {
                val winnerMatch = mScore.score(team)
                if (winnerMatch != Winner.NONE) {
                    // Match is over
                } else {
                    // Set is over, Match is not
                    sets.add(
                        Set(
                            winMinimumSet,
                            winMarginSet,
                            winMinimumGame,
                            winMarginGame,
                            winMinimumGameTiebreak,
                            winMarginGameTiebreak,
                            overtimeRule,
                            this
                        )
                    )
                }
            } else {
                // Game is over, Set is not
                currentSet.addNewGame()
                if (currentGame.tiebreak) {
                    serving = when (serving) {
                        Serving.PLAYER1_RIGHT -> Serving.PLAYER1_LEFT
                        Serving.PLAYER2_RIGHT -> Serving.PLAYER2_LEFT
                        Serving.PLAYER3_RIGHT -> Serving.PLAYER3_LEFT
                        Serving.PLAYER4_RIGHT -> Serving.PLAYER4_LEFT
                        else -> Serving.PLAYER1_RIGHT
                    }
                }
            }

            if (currentSet.games.size % 2 == 0) {
                changeover = true
            }

            serviceChanged = true

            serving = when (serving) {
                Serving.PLAYER1_LEFT, Serving.PLAYER1_RIGHT ->
                    if (matchType == MatchType.DOUBLES && startingServer == Team.TEAM2)
                        Serving.PLAYER4_RIGHT
                    else
                        Serving.PLAYER2_RIGHT
                Serving.PLAYER2_LEFT, Serving.PLAYER2_RIGHT ->
                    if (matchType == MatchType.DOUBLES && startingServer == Team.TEAM1)
                        Serving.PLAYER3_RIGHT
                    else
                        Serving.PLAYER1_RIGHT
                Serving.PLAYER3_LEFT, Serving.PLAYER3_RIGHT ->
                    if (startingServer == Team.TEAM1)
                        Serving.PLAYER4_RIGHT
                    else
                        Serving.PLAYER2_RIGHT
                Serving.PLAYER4_LEFT, Serving.PLAYER4_RIGHT ->
                    if (startingServer == Team.TEAM1) Serving.PLAYER1_RIGHT else Serving.PLAYER3_RIGHT
            }
        } else if (currentGame.tiebreak && (currentGame.getScore(Team.TEAM1) + currentGame.getScore(
                Team.TEAM2
            )) % 2 == 1
        ) {
            serving = when (serving) {
                Serving.PLAYER1_LEFT, Serving.PLAYER1_RIGHT ->
                    if (matchType == MatchType.DOUBLES && startingServer == Team.TEAM2)
                        Serving.PLAYER4_LEFT
                    else
                        Serving.PLAYER2_LEFT
                Serving.PLAYER2_LEFT, Serving.PLAYER2_RIGHT ->
                    if (matchType == MatchType.DOUBLES && startingServer == Team.TEAM1)
                        Serving.PLAYER3_LEFT
                    else
                        Serving.PLAYER1_LEFT
                Serving.PLAYER3_LEFT, Serving.PLAYER3_RIGHT ->
                    if (startingServer == Team.TEAM1)
                        Serving.PLAYER4_LEFT
                    else
                        Serving.PLAYER2_LEFT
                Serving.PLAYER4_LEFT, Serving.PLAYER4_RIGHT ->
                    if (startingServer == Team.TEAM1)
                        Serving.PLAYER1_LEFT
                    else
                        Serving.PLAYER3_LEFT
            }

            serviceChanged = true
        } else {
            serving = when (serving) {
                Serving.PLAYER1_LEFT -> Serving.PLAYER1_RIGHT
                Serving.PLAYER1_RIGHT -> Serving.PLAYER1_LEFT
                Serving.PLAYER2_LEFT -> Serving.PLAYER2_RIGHT
                Serving.PLAYER2_RIGHT -> Serving.PLAYER2_LEFT
                Serving.PLAYER3_LEFT -> Serving.PLAYER3_RIGHT
                Serving.PLAYER3_RIGHT -> Serving.PLAYER3_LEFT
                Serving.PLAYER4_LEFT -> Serving.PLAYER4_RIGHT
                Serving.PLAYER4_RIGHT -> Serving.PLAYER4_LEFT
            }
        }

        if (updateLog) {
            scoreLog.push(team)
        }

        if (currentGame.tiebreak && (currentGame.getScore(Team.TEAM1) + currentGame.getScore(Team.TEAM2)) % 6 == 0) {
            changeover = true
        }
    }

    fun score(team: Team) = score(team, true)

    fun undo(): Boolean {
        if (scoreLog.size != 0) {
            scoreLog.pop()
            loadScoreLog()
            return true
        }
        return false
    }

    fun scoreLogArray(): LongArray = scoreLog.bitSetToLongArray()
    fun scoreLogSize() = scoreLog.size
}
