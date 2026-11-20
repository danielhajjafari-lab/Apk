package com.example.dakhlokharj

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class Txn(@PrimaryKey(autoGenerate=true) val id:Int=0, val amount:Long, val dateMillis:Long, val bank:String, val type:String, val category:String="", val note:String="")

@Dao interface TxnDao {
 @Query("SELECT * FROM transactions ORDER BY dateMillis DESC") fun all(): Flow<List<Txn>>
 @Query("SELECT * FROM transactions WHERE dateMillis >= :from AND dateMillis < :to ORDER BY dateMillis") suspend fun range(from:Long,to:Long):List<Txn>
 @Insert suspend fun insert(txn:Txn)
 @Query("SELECT * FROM transactions WHERE dateMillis >= :from AND dateMillis < :to") suspend fun day(from:Long,to:Long):List<Txn>
}
@Database(entities=[Txn::class],version=1,exportSchema=false)
abstract class AppDb:RoomDatabase(){abstract fun dao():TxnDao; companion object { @Volatile private var instance:AppDb?=null; fun get(c:Context)=instance?: synchronized(this){instance?:Room.databaseBuilder(c.applicationContext,AppDb::class.java,"personal-ledger.db").build().also{instance=it}} } }
