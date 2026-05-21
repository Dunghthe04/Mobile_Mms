package com.mkac.meikomms.ui.custom;


import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity(tableName = "language")
public class Language {
    @PrimaryKey
    public int Id;

    @ColumnInfo(defaultValue = "en")
    public String Language_Code;

    public Language(int Id, String Language_Code) {
        this.Id = Id;
        this.Language_Code = Language_Code;
    }


}

