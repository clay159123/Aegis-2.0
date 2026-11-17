package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AegisDao {

    @Query("SELECT * FROM commander_state WHERE id = 1")
    fun observeCommanderState(): Flow<CommanderStateEntity?>

    @Query("SELECT * FROM commander_state WHERE id = 1")
    suspend fun getCommanderStateOnce(): CommanderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommanderState(state: CommanderStateEntity)

    @Query("SELECT * FROM facilities")
    fun observeFacilities(): Flow<List<FacilityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacilities(facilities: List<FacilityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacility(facility: FacilityEntity)

    @Query("SELECT * FROM operators")
    fun observeOperators(): Flow<List<OperatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperators(operators: List<OperatorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperator(operator: OperatorEntity)

    @Query("SELECT * FROM gear_items")
    fun observeGear(): Flow<List<GearEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGearList(items: List<GearEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingGear(items: List<GearEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGear(item: GearEntity)

    @Query("SELECT * FROM deployments ORDER BY startedAtMillis ASC")
    fun observeDeployments(): Flow<List<DeploymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeployment(deployment: DeploymentEntity)

    @Query("DELETE FROM deployments WHERE id = :id")
    suspend fun deleteDeploymentById(id: String)

    @Query("SELECT * FROM citations ORDER BY awardedAtMillis DESC")
    fun observeCitations(): Flow<List<CitationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCitation(citation: CitationEntity)

    @Query("SELECT * FROM pending_deeds ORDER BY createdAtMillis DESC")
    fun observePendingDeeds(): Flow<List<PendingDeedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeeds(deeds: List<PendingDeedEntity>)

    @Query("DELETE FROM pending_deeds WHERE id = :id")
    suspend fun deletePendingDeedById(id: String)

    @Query("DELETE FROM pending_deeds WHERE operatorId = :operatorId AND recommendedMedalId = :medalId")
    suspend fun deletePendingDeedsForAward(operatorId: String, medalId: String)

    @Query("SELECT * FROM squads ORDER BY createdAtMillis ASC")
    fun observeSquads(): Flow<List<SquadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquad(squad: SquadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquads(squads: List<SquadEntity>)

    @Query("DELETE FROM squads WHERE id = :id")
    suspend fun deleteSquadById(id: String)

    @Query("DELETE FROM operators WHERE id = :id")
    suspend fun deleteOperatorById(id: String)

    @Query("DELETE FROM citations WHERE operatorId = :operatorId")
    suspend fun deleteCitationsForOperator(operatorId: String)

    @Query("DELETE FROM pending_deeds WHERE operatorId = :operatorId")
    suspend fun deletePendingDeedsForOperator(operatorId: String)

    @Query("DELETE FROM commander_state")
    suspend fun clearCommanderState()

    @Query("DELETE FROM facilities")
    suspend fun clearFacilities()

    @Query("DELETE FROM operators")
    suspend fun clearOperators()

    @Query("DELETE FROM gear_items")
    suspend fun clearGearItems()

    @Query("DELETE FROM deployments")
    suspend fun clearDeployments()

    @Query("DELETE FROM citations")
    suspend fun clearCitations()

    @Query("DELETE FROM pending_deeds")
    suspend fun clearPendingDeeds()

    @Query("DELETE FROM squads")
    suspend fun clearSquads()

    @androidx.room.Transaction
    suspend fun clearAllData() {
        clearPendingDeeds()
        clearCitations()
        clearDeployments()
        clearSquads()
        clearGearItems()
        clearOperators()
        clearFacilities()
        clearCommanderState()
    }
}
