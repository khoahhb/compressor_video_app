package com.example.compress_video_app.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.compress_video_app.models.HandleVideo;

@Database(entities = {HandleVideo.class}, version = 1)
public abstract class CompressedVideoDatabase extends RoomDatabase {
    private static CompressedVideoDatabase database;

    public static synchronized CompressedVideoDatabase getDatabase(Context context) {
        if (database == null) {
            database = Room.databaseBuilder(context.getApplicationContext(),
                            CompressedVideoDatabase.class, "video_db")
                    .allowMainThreadQueries()
                    .build();
        }
        return database;
    }

    public abstract CompressedVideoDao compressedVideoDao();
}
