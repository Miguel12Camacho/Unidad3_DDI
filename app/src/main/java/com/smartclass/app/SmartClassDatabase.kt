package com.smartclass.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "users")
internal data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val passwordHash: String,
    val passwordSalt: String,
    val recoveryHash: String,
    val recoverySalt: String,
    val teacherId: String = "",
    val active: Boolean = true,
    val createdAt: String,
)

@Entity(tableName = "students")
internal data class StudentEntity(@PrimaryKey val id: String, val name: String, val enrollment: String, val groupId: String)

@Entity(tableName = "teachers")
internal data class TeacherEntity(@PrimaryKey val id: String, val name: String, val email: String = "")

@Entity(tableName = "groups")
internal data class GroupEntity(@PrimaryKey val id: String, val name: String, val teacherId: String)

@Entity(tableName = "classrooms")
internal data class ClassroomEntity(@PrimaryKey val id: String, val name: String, val location: String = "", val gatewayId: String = "")

@Entity(tableName = "subjects")
internal data class SubjectEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "schedules")
internal data class ScheduleEntity(@PrimaryKey val id: String, val subjectId: String, val groupId: String, val teacherId: String, val classroomId: String, val dayOfWeek: Int, val startTime: String, val endTime: String, val devicesJson: String)

@Entity(tableName = "class_sessions")
internal data class ClassSessionEntity(@PrimaryKey val id: String, val scheduleId: String, val subjectName: String, val groupName: String, val teacherName: String, val classroomName: String, val startedAt: String, val endedAt: String, val presentCount: Int, val studentCount: Int)

@Entity(tableName = "attendance")
internal data class AttendanceEntity(@PrimaryKey val id: String, val studentId: String, val groupId: String, val date: String, val status: String, val note: String, val updatedAt: String)

@Entity(tableName = "devices")
internal data class DeviceEntity(@PrimaryKey val id: String, val classroomId: String, val name: String, val kind: String, val connected: Boolean, val state: String, val updatedAt: String)

@Entity(tableName = "sensor_readings")
internal data class SensorReadingEntity(@PrimaryKey val id: String, val classroomId: String, val sensorId: String, val value: Double, val unit: String, val connected: Boolean, val recordedAt: String)

@Entity(tableName = "alerts")
internal data class AlertEntity(@PrimaryKey val id: String, val category: String, val title: String, val message: String, val priority: String, val createdAt: String, val scheduledAt: String, val read: Boolean, val resolved: Boolean)

@Entity(tableName = "announcements")
internal data class AnnouncementEntity(@PrimaryKey val id: String, val message: String, val category: String, val priority: String, val createdAt: String, val dueAt: String = "")

@Entity(tableName = "activity_log")
internal data class ActivityLogEntity(@PrimaryKey val id: String, val actorUserId: String, val action: String, val targetType: String, val targetId: String, val detail: String, val occurredAt: String)

@Dao
internal interface SmartClassDao {
    @Query("SELECT * FROM users ORDER BY email") suspend fun users(): List<UserEntity>
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1") suspend fun userById(id: String): UserEntity?
    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1") suspend fun userByEmail(email: String): UserEntity?
    @Query("SELECT COUNT(*) FROM users") suspend fun userCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUser(user: UserEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUsers(users: List<UserEntity>)
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteUser(id: String)

    @Query("SELECT * FROM students") suspend fun students(): List<StudentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStudents(items: List<StudentEntity>)
    @Query("DELETE FROM students") suspend fun clearStudents()
    @Query("SELECT * FROM teachers") suspend fun teachers(): List<TeacherEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTeachers(items: List<TeacherEntity>)
    @Query("DELETE FROM teachers") suspend fun clearTeachers()
    @Query("SELECT * FROM `groups`") suspend fun groups(): List<GroupEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGroups(items: List<GroupEntity>)
    @Query("DELETE FROM `groups`") suspend fun clearGroups()
    @Query("SELECT * FROM classrooms") suspend fun classrooms(): List<ClassroomEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putClassrooms(items: List<ClassroomEntity>)
    @Query("DELETE FROM classrooms") suspend fun clearClassrooms()
    @Query("SELECT * FROM subjects") suspend fun subjects(): List<SubjectEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSubjects(items: List<SubjectEntity>)
    @Query("DELETE FROM subjects") suspend fun clearSubjects()
    @Query("SELECT * FROM schedules") suspend fun schedules(): List<ScheduleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSchedules(items: List<ScheduleEntity>)
    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("SELECT * FROM class_sessions ORDER BY startedAt DESC") suspend fun sessions(): List<ClassSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSessions(items: List<ClassSessionEntity>)
    @Query("DELETE FROM class_sessions") suspend fun clearSessions()
    @Query("SELECT * FROM attendance") suspend fun attendance(): List<AttendanceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAttendance(items: List<AttendanceEntity>)
    @Query("DELETE FROM attendance") suspend fun clearAttendance()
    @Query("SELECT * FROM devices") suspend fun devices(): List<DeviceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDevices(items: List<DeviceEntity>)
    @Query("DELETE FROM devices") suspend fun clearDevices()
    @Query("SELECT * FROM sensor_readings ORDER BY recordedAt DESC") suspend fun sensorReadings(): List<SensorReadingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSensorReadings(items: List<SensorReadingEntity>)
    @Query("DELETE FROM sensor_readings") suspend fun clearSensorReadings()
    @Query("DELETE FROM sensor_readings WHERE id NOT IN (SELECT id FROM sensor_readings ORDER BY recordedAt DESC LIMIT 500)") suspend fun trimSensorReadings()
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC") suspend fun alerts(): List<AlertEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAlerts(items: List<AlertEntity>)
    @Query("DELETE FROM alerts") suspend fun clearAlerts()
    @Query("SELECT * FROM announcements ORDER BY createdAt DESC") suspend fun announcements(): List<AnnouncementEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAnnouncements(items: List<AnnouncementEntity>)
    @Query("DELETE FROM announcements") suspend fun clearAnnouncements()
    @Query("SELECT * FROM activity_log ORDER BY occurredAt DESC LIMIT 500") suspend fun activityLog(): List<ActivityLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putActivity(items: List<ActivityLogEntity>)
    @Query("DELETE FROM activity_log") suspend fun clearActivity()
}

@Database(
    entities = [UserEntity::class, StudentEntity::class, TeacherEntity::class, GroupEntity::class, ClassroomEntity::class, SubjectEntity::class, ScheduleEntity::class, ClassSessionEntity::class, AttendanceEntity::class, DeviceEntity::class, SensorReadingEntity::class, AlertEntity::class, AnnouncementEntity::class, ActivityLogEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class SmartClassDatabase : RoomDatabase() {
    abstract fun dao(): SmartClassDao

    companion object {
        @Volatile private var instance: SmartClassDatabase? = null
        fun get(context: Context): SmartClassDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SmartClassDatabase::class.java, "smartclass.db").build().also { instance = it }
        }
    }
}
