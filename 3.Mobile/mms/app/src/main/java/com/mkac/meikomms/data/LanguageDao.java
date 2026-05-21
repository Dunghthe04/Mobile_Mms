package com.mkac.meikomms.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mkac.meikomms.ui.custom.Language;

@Dao
public interface LanguageDao
{

    @Query("Update language set Language_Code = :languageCode where Id = 1")
    void updateLanguage(String languageCode);

    @Query("Select Language_Code from language")
    LiveData<String> getCurrentLanguage();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLanguage(Language language);

    @Query("DELETE FROM Language")
    void truncateTable();
}
