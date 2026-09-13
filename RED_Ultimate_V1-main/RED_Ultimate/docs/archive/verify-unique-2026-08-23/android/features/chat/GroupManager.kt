package com.red.features.chat

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "developed_groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val avatarUrl: String?,
    val ownerId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val userId: String,
    val role: String // OWNER, ADMIN, MEMBER
)

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: GroupMemberEntity)

    @Query("SELECT * FROM developed_groups")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>
}
