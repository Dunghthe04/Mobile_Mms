package com.mkac.meikomms.data;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ConnectionToServerDao
{
    @Query("SELECT * FROM connectionToServer")
    LiveData<ConnectionToServer> getConnectionToServerStatus();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ConnectionToServer connectionStatus);


}


