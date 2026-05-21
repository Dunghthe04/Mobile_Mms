package com.mkac.meikomms.data;


import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.mkac.meikomms.ui.custom.Language;


@Database(entities = {Language.class, ConnectionToServer.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;
    public abstract LanguageDao languageDao();
    public abstract ConnectionToServerDao connectionToServerDao();



    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "app_database")
                    //   .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }

    // Wrap read and write operations in a transaction
    public void performTransaction(Runnable runnable) {
        getOpenHelper().getWritableDatabase().beginTransaction();
        try {
            runnable.run();
            getOpenHelper().getWritableDatabase().setTransactionSuccessful();
        } finally {
            getOpenHelper().getWritableDatabase().endTransaction();
        }
    }
}
