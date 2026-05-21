package com.mkac.meikomms.data;


import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "connectionToServer")
public class ConnectionToServer
{
    @PrimaryKey
    @NonNull
    public int Id;

    public String Status;

    public String Last_Send;

    public ConnectionToServer(@NonNull int Id, String Status, String Last_Send ) {
        this.Id = Id;
        this.Status = Status;
        this.Last_Send = Last_Send;
    }
}

